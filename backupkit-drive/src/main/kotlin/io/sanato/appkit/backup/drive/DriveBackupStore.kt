package io.sanato.appkit.backup.drive

import io.sanato.appkit.backup.remote.RemoteBackupStore
import io.sanato.appkit.backup.remote.RemoteFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * [RemoteBackupStore] 的 Google Drive REST v3 实现。裸 `HttpURLConnection` + `org.json`，
 * 不引入 OkHttp/Retrofit——这个模块已经因为 GMS 认证带了一份不小的依赖，没必要再加一个
 * HTTP 客户端库。
 *
 * "文件夹"映射成 [rootFolderName] 目录（可选 [subPath] 子目录，用于多空间隔离，如
 * 宿主的隐私空间备份）下的一级子目录。[RemoteFile.id] 就是 Drive 的 file id。
 *
 * @param rootFolderName 每个消费方 app 必须传自己的根目录名（不设默认值）——多个 app
 * 共用同一个 Google 账号时，根目录名撞车会导致互相覆盖对方的备份，这是刻意不给默认值
 * 强制调用方显式决定的原因。
 *
 * ## 超时必须显式设置（别把它当可有可无的调优）
 *
 * `HttpURLConnection` 的 `connectTimeout`/`readTimeout` 默认值是 **0 = 无限等待**。
 * 本类原先三处建连接（[openConnection]、[startResumableSession]、[resumableUpload] 的
 * PUT）都没设，结果一条半死的 googleapis.com 连接会让调用方永久阻塞在 socket 上：
 * 不抛异常、不返回、[io.sanato.appkit.backup.remote.BackupOrchestrator] 的进度回调也
 * 一次不出（第一个网络动作就在 `ensureMediaUploaded` 的 `store.list` 里卡住）。
 *
 * 宿主 sanato-diary 上的真实故障：用户点「立即备份」后前台服务挂住 8 分钟，进程 CPU
 * 占用 0，通知栏永远停在初始的不确定进度条，服务的 `runCatching` 永不返回 → 不通知
 * 失败、不收尾、wakeLock 一直持有、备份互斥锁一直被占（把之后所有自动补漏都挤掉）。
 *
 * 注意这里**只能**盖住"连不上"和"连上了但不回数据"两类停摆：`HttpURLConnection` 没有
 * 写超时可设，上传时对端 TCP 窗口关死导致的写阻塞仍然管不了。消费方要彻底不被拖死，
 * 还得在自己那侧给整个任务加一道总闸（sanato-diary 的 `withBackupDeadline` 就是这个角色）。
 */
public class DriveBackupStore(
    private val tokenProvider: DriveTokenProvider,
    private val rootFolderName: String,
    private val subPath: String? = null,
    // 测试接缝：真实消费方从不需要传这两个参数（默认值就是真实 Drive 端点）；
    // 单测用它们把请求指向本地假 HTTP 服务器，不必为此单独反射或 mock HttpURLConnection。
    private val filesUrl: String = DEFAULT_FILES_URL,
    private val uploadUrl: String = DEFAULT_UPLOAD_URL,
    // 同样是测试接缝：真实消费方用默认值即可；单测把它们调到几十毫秒，才能在秒级内
    // 验证"服务端不回包时会按时失败"，而不是让用例真的等满 15/30 秒。
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : RemoteBackupStore {
    private val folderIdCache = mutableMapOf<String, String>()

    override suspend fun list(folder: String): List<RemoteFile> {
        val folderId = ensureFolder(folder) ?: return emptyList()
        return listFilesInFolder(folderId)
    }

    override suspend fun uploadIfAbsent(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        val existing = list(folder).firstOrNull { it.name == name && it.size > 0 }
        if (existing != null) return existing
        return upload(folder, name, file)
    }

    override suspend fun upload(
        folder: String,
        name: String,
        file: File,
    ): RemoteFile {
        val folderId = ensureFolder(folder) ?: error("DriveBackupStore: cannot create folder '$folder'")
        val existingId = findChild(folderId, name)
        val fileId = resumableUpload(existingId, folderId, name, file)
        return RemoteFile(fileId, name, file.length())
    }

    override suspend fun download(
        fileId: String,
        dest: File,
    ) {
        val token = tokenProvider.currentAccessToken()
        val connection = openConnection(token, "GET", "$filesUrl/$fileId?alt=media")
        try {
            checkOk(connection, "download($fileId)")
            connection.inputStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun delete(fileId: String) {
        val token = tokenProvider.currentAccessToken()
        val connection = openConnection(token, "DELETE", "$filesUrl/$fileId")
        try {
            checkOk(connection, "delete($fileId)", allowNotFound = true)
        } finally {
            connection.disconnect()
        }
    }

    // ── 目录解析（根目录 → 可选 subPath → 逻辑文件夹名）──────────────────

    private suspend fun ensureFolder(name: String): String? {
        val cacheKey = "$subPath/$name"
        folderIdCache[cacheKey]?.let { return it }

        var parentId = ensureNamedChild("root", rootFolderName)
        if (subPath != null) {
            parentId = ensureNamedChild(parentId, subPath)
        }
        val folderId = ensureNamedChild(parentId, name)
        folderIdCache[cacheKey] = folderId
        return folderId
    }

    private suspend fun ensureNamedChild(
        parentId: String,
        name: String,
    ): String = findChild(parentId, name, FOLDER_MIME) ?: createFolder(parentId, name)

    private suspend fun findChild(
        parentId: String,
        name: String,
        mimeType: String? = null,
    ): String? {
        val mimeClause = if (mimeType != null) " and mimeType='$mimeType'" else ""
        val query = "'$parentId' in parents and name='${escapeQueryValue(name)}' and trashed=false$mimeClause"
        val url = "$filesUrl?q=${encode(query)}&fields=${encode("files(id,name)")}&pageSize=1"
        val json = requestJson("GET", url)
        val files = json.optJSONArray("files") ?: JSONArray()
        return if (files.length() > 0) files.getJSONObject(0).getString("id") else null
    }

    private suspend fun createFolder(
        parentId: String,
        name: String,
    ): String {
        val body =
            JSONObject().apply {
                put("name", name)
                put("mimeType", FOLDER_MIME)
                put("parents", JSONArray(listOf(parentId)))
            }
        val json = requestJson("POST", filesUrl, body.toString(), "application/json; charset=UTF-8")
        return json.getString("id")
    }

    private suspend fun listFilesInFolder(folderId: String): List<RemoteFile> {
        val results = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val query = "'$folderId' in parents and trashed=false"
            val tokenParam = pageToken?.let { "&pageToken=${encode(it)}" } ?: ""
            val url = "$filesUrl?q=${encode(
                query,
            )}&fields=${encode("nextPageToken,files(id,name,size)")}&pageSize=1000$tokenParam"
            val json = requestJson("GET", url)
            val files = json.optJSONArray("files") ?: JSONArray()
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                results +=
                    RemoteFile(f.getString("id"), f.getString("name"), f.optString("size", "0").toLongOrNull() ?: 0L)
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotEmpty() }
        } while (pageToken != null)
        return results
    }

    // ── Resumable upload：POST/PATCH 起 session，再单次 PUT 内容 ─────────

    private suspend fun resumableUpload(
        existingFileId: String?,
        parentId: String,
        name: String,
        file: File,
    ): String {
        val token = tokenProvider.currentAccessToken()
        val sessionUri =
            if (existingFileId != null) {
                startResumableSession(token, "PATCH", "$uploadUrl/$existingFileId?uploadType=resumable", null)
            } else {
                val metadata =
                    JSONObject().apply {
                        put("name", name)
                        put("parents", JSONArray(listOf(parentId)))
                    }
                startResumableSession(token, "POST", "$uploadUrl?uploadType=resumable", metadata.toString())
            }

        val connection = newConnection(sessionUri)
        connection.requestMethod = "PUT"
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(file.length())
        connection.setRequestProperty("Content-Type", "application/octet-stream")
        try {
            connection.outputStream.use { out -> file.inputStream().use { it.copyTo(out) } }
            checkOk(connection, "resumableUpload($name)")
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            return json.getString("id")
        } finally {
            connection.disconnect()
        }
    }

    private fun startResumableSession(
        token: String,
        method: String,
        url: String,
        jsonBody: String?,
    ): String {
        val connection = newConnection(url)
        // java.net.HttpURLConnection 硬编码的合法方法白名单里没有 PATCH（一个从未被修复的
        // JDK 老限制，最新 JDK 依然如此）。Google API 对这个已知的 Java 限制有官方支持的
        // 绕过方式：实际方法发 POST，用 X-HTTP-Method-Override 头告诉服务端"当 PATCH 处理"。
        if (method == "PATCH") {
            connection.requestMethod = "POST"
            connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
        } else {
            connection.requestMethod = method
        }
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("X-Upload-Content-Type", "application/octet-stream")
        try {
            if (jsonBody != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            }
            checkOk(connection, "startResumableSession")
            return connection.getHeaderField("Location") ?: error("resumable session response has no Location header")
        } finally {
            connection.disconnect()
        }
    }

    // ── 底层 HTTP 帮助函数 ────────────────────────────────────────────

    private suspend fun requestJson(
        method: String,
        url: String,
        body: String? = null,
        contentType: String = "application/json; charset=UTF-8",
    ): JSONObject {
        val token = tokenProvider.currentAccessToken()
        val connection = openConnection(token, method, url)
        try {
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            checkOk(connection, "$method $url")
            val text = connection.inputStream.bufferedReader().readText()
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        token: String,
        method: String,
        url: String,
    ): HttpURLConnection {
        val connection = newConnection(url)
        connection.requestMethod = method
        connection.setRequestProperty("Authorization", "Bearer $token")
        return connection
    }

    /**
     * 本类建 [HttpURLConnection] 的唯一入口——三处建连接都必须走这里，别再直接
     * `URL(...).openConnection()`：那样就漏了超时，等于回到"一条半死的连接把调用方
     * 永久挂住"的老 bug（见类 KDoc）。
     */
    private fun newConnection(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
        }

    private fun checkOk(
        connection: HttpURLConnection,
        context: String,
        allowNotFound: Boolean = false,
    ) {
        val code = connection.responseCode
        if (code in 200..299) return
        if (allowNotFound && code == 404) return
        val errorBody = runCatching { connection.errorStream?.bufferedReader()?.readText() }.getOrNull()
        throw IOException("Drive request failed [$context]: HTTP $code $errorBody")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun escapeQueryValue(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")

    private companion object {
        const val DEFAULT_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        const val DEFAULT_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"

        // 建连接 15s：连不上就是连不上，等更久没有意义。
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000

        // 读 30s：这是"两次收到字节之间"的上限，不是整个传输的上限——大文件上传/下载
        // 只要还在出数据就不会被它打断，30 秒一个字节都没动才算对端停摆。
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
    }
}
