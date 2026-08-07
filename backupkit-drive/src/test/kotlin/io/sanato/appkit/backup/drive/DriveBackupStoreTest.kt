package io.sanato.appkit.backup.drive

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.sanato.appkit.backup.remote.RemoteBackupStore
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicLong

/**
 * 用 JDK 内置 [HttpServer] 起一个内存版的假 Drive API，端到端验证 [DriveBackupStore]
 * 真的按 Drive REST v3 的协议在说话——folder 解析/创建、resumable upload 的两段式
 * (起 session 拿 Location，再 PUT 内容)、files.list 查询、download、delete。
 * 不 mock `HttpURLConnection`：那需要大量反射，真实 HTTP 服务器更接近生产行为。
 */
class DriveBackupStoreTest {
    private lateinit var server: HttpServer
    private lateinit var baseUrl: String
    private lateinit var uploadBaseUrl: String
    private lateinit var store: DriveBackupStore
    private lateinit var tmpDir: File

    // 内存里的假 Drive：id -> FakeFile
    private class FakeFile(
        var name: String,
        val parents: MutableList<String>,
        val mimeType: String,
        var bytes: ByteArray = ByteArray(0),
    )

    private val filesById = mutableMapOf<String, FakeFile>()
    private val pendingSessions = mutableMapOf<String, Pair<String?, FakeFile>>() // sessionId -> (existingId, metadata)
    private val nextId = AtomicLong(1)

    @Before
    fun setUp() {
        // com.sun.net.httpserver.HttpServer 与 HttpURLConnection 的 keep-alive 连接复用在
        // 快速连续请求时有已知的时序问题(SocketException: Unexpected end of file)——
        // 只在这个用内置 HttpServer 做假 Drive 服务器的测试里禁用，不影响生产代码路径。
        System.setProperty("http.keepAlive", "false")
        tmpDir =
            File.createTempFile("drivetest", "").apply {
                delete()
                mkdirs()
            }
        filesById.clear()
        pendingSessions.clear()

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", ::handle)
        // 默认(null)执行器在同进程内客户端+服务端来回请求时有已知的时序问题；
        // 显式给一个线程池执行器规避。
        server.executor =
            java.util.concurrent.Executors
                .newCachedThreadPool()
        server.start()
        val port = server.address.port
        baseUrl = "http://127.0.0.1:$port/drive/v3/files"
        uploadBaseUrl = "http://127.0.0.1:$port/upload/drive/v3/files"

        store =
            DriveBackupStore(
                tokenProvider = DriveTokenProvider { "fake-token" },
                rootFolderName = "TestRoot",
                filesUrl = baseUrl,
                uploadUrl = uploadBaseUrl,
            )
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun uploadThenListThenDownloadThenDelete_fullCycle() =
        runTest {
            val content = "hello drive — 你好".toByteArray()
            val source = File(tmpDir, "source.bin").apply { writeBytes(content) }

            val uploaded = store.upload(RemoteBackupStore.FOLDER_ENTRIES, "entry_1.sdb", source)
            assertEquals("entry_1.sdb", uploaded.name)
            assertEquals(content.size.toLong(), uploaded.size)

            val listed = store.list(RemoteBackupStore.FOLDER_ENTRIES)
            assertEquals(1, listed.size)
            assertEquals("entry_1.sdb", listed[0].name)

            val dest = File(tmpDir, "downloaded.bin")
            store.download(listed[0].id, dest)
            assertTrue(content.contentEquals(dest.readBytes()))

            store.delete(listed[0].id)
            assertTrue(store.list(RemoteBackupStore.FOLDER_ENTRIES).isEmpty())
        }

    @Test
    fun uploadIfAbsent_doesNotReuploadExistingNonEmptyFile() =
        runTest {
            val source = File(tmpDir, "source.bin").apply { writeBytes("v1".toByteArray()) }
            val first = store.uploadIfAbsent(RemoteBackupStore.FOLDER_MEDIA, "photo.jpg.sdb", source)

            source.writeBytes("v2-should-not-be-uploaded".toByteArray())
            val second = store.uploadIfAbsent(RemoteBackupStore.FOLDER_MEDIA, "photo.jpg.sdb", source)

            assertEquals(first.id, second.id)
            val dest = File(tmpDir, "out.bin")
            store.download(second.id, dest)
            assertEquals("v1", dest.readText())
        }

    @Test
    fun upload_overwritesExistingFileWithSameName() =
        runTest {
            val v1 = File(tmpDir, "v1.bin").apply { writeBytes("first".toByteArray()) }
            val first = store.upload(RemoteBackupStore.FOLDER_SNAPSHOTS, "snapshot_1.sdb", v1)

            val v2 = File(tmpDir, "v2.bin").apply { writeBytes("second-longer".toByteArray()) }
            val second = store.upload(RemoteBackupStore.FOLDER_SNAPSHOTS, "snapshot_1.sdb", v2)

            assertEquals(first.id, second.id) // 同名覆盖，不是新建一份
            val listed = store.list(RemoteBackupStore.FOLDER_SNAPSHOTS)
            assertEquals(1, listed.size)

            val dest = File(tmpDir, "out.bin")
            store.download(second.id, dest)
            assertEquals("second-longer", dest.readText())
        }

    @Test
    fun subPath_isolatesFoldersFromMainSpace() =
        runTest {
            val privateStore =
                DriveBackupStore(
                    tokenProvider = DriveTokenProvider { "fake-token" },
                    rootFolderName = "TestRoot",
                    subPath = "private",
                    filesUrl = baseUrl,
                    uploadUrl = uploadBaseUrl,
                )
            val source = File(tmpDir, "p.bin").apply { writeBytes("private content".toByteArray()) }
            privateStore.upload(RemoteBackupStore.FOLDER_ENTRIES, "entry_1.sdb", source)

            // 主空间与隐私空间用不同 subPath，各自看不到对方的文件。
            assertTrue(store.list(RemoteBackupStore.FOLDER_ENTRIES).isEmpty())
            assertEquals(1, privateStore.list(RemoteBackupStore.FOLDER_ENTRIES).size)
        }

    // ── 假 Drive API ──────────────────────────────────────────────────

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path
            val query = exchange.requestURI.rawQuery ?: ""
            // 生产代码用 X-HTTP-Method-Override 绕过 HttpURLConnection 不支持 PATCH 的限制
            // （见 DriveBackupStore.startResumableSession 的注释），假服务器要认这个头。
            val effectiveMethod = exchange.requestHeaders.getFirst("X-HTTP-Method-Override") ?: exchange.requestMethod
            when {
                path == "/drive/v3/files" && effectiveMethod == "GET" -> {
                    handleList(exchange, query)
                }

                path == "/drive/v3/files" && effectiveMethod == "POST" -> {
                    handleCreate(exchange)
                }

                path.startsWith("/drive/v3/files/") && effectiveMethod == "GET" -> {
                    handleDownload(exchange, path)
                }

                path.startsWith("/drive/v3/files/") && effectiveMethod == "DELETE" -> {
                    handleDelete(exchange, path)
                }

                path == "/upload/drive/v3/files" && effectiveMethod == "POST" -> {
                    handleStartSession(exchange, null)
                }

                path.startsWith("/upload/drive/v3/files/") && effectiveMethod == "PATCH" -> {
                    handleStartSession(exchange, path.substringAfterLast('/').substringBefore('?'))
                }

                path.startsWith("/upload-session/") && effectiveMethod == "PUT" -> {
                    handlePutContent(exchange, path)
                }

                else -> {
                    respond(exchange, 404, "{}")
                }
            }
        } catch (e: Exception) {
            val trace =
                java.io
                    .StringWriter()
                    .also { e.printStackTrace(java.io.PrintWriter(it)) }
                    .toString()
            respond(exchange, 500, JSONObject().apply { put("error", trace) }.toString())
        } finally {
            exchange.close()
        }
    }

    private fun handleList(
        exchange: HttpExchange,
        rawQuery: String,
    ) {
        val q = decodeQueryParam(rawQuery, "q") ?: ""
        val nameMatch =
            Regex("name='((?:[^'\\\\]|\\\\.)*)'")
                .find(q)
                ?.groupValues
                ?.get(1)
                ?.replace("\\'", "'")
        val parentMatch = Regex("'([^']*)' in parents").find(q)?.groupValues?.get(1)
        val mimeMatch = Regex("mimeType='([^']*)'").find(q)?.groupValues?.get(1)

        val matches =
            filesById.entries.filter { (_, f) ->
                (parentMatch == null || parentMatch in f.parents) &&
                    (nameMatch == null || f.name == nameMatch) &&
                    (mimeMatch == null || f.mimeType == mimeMatch)
            }
        val filesJson =
            JSONArray().apply {
                matches.forEach { (id, f) ->
                    put(
                        JSONObject().apply {
                            put("id", id)
                            put("name", f.name)
                            put("size", f.bytes.size.toString())
                        },
                    )
                }
            }
        respond(exchange, 200, JSONObject().apply { put("files", filesJson) }.toString())
    }

    private fun handleCreate(exchange: HttpExchange) {
        val body = JSONObject(exchange.requestBody.bufferedReader().readText())
        val id = "folder-${nextId.getAndIncrement()}"
        val parents =
            body
                .optJSONArray("parents")
                ?.let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }.orEmpty()
        filesById[id] =
            FakeFile(
                name = body.getString("name"),
                parents = parents.toMutableList(),
                mimeType = body.optString("mimeType", ""),
            )
        respond(exchange, 200, JSONObject().apply { put("id", id) }.toString())
    }

    private fun handleStartSession(
        exchange: HttpExchange,
        existingId: String?,
    ) {
        val body = exchange.requestBody.bufferedReader().readText()
        val sessionId = "session-${nextId.getAndIncrement()}"
        val metadata =
            if (body.isNotBlank()) {
                val json = JSONObject(body)
                val parents =
                    json
                        .optJSONArray("parents")
                        ?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        }.orEmpty()
                FakeFile(
                    name = json.getString("name"),
                    parents = parents.toMutableList(),
                    mimeType = "application/octet-stream",
                )
            } else {
                val existing = filesById.getValue(existingId!!)
                FakeFile(name = existing.name, parents = existing.parents, mimeType = existing.mimeType)
            }
        pendingSessions[sessionId] = existingId to metadata
        exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/upload-session/$sessionId")
        respond(exchange, 200, "")
    }

    private fun handlePutContent(
        exchange: HttpExchange,
        path: String,
    ) {
        val sessionId = path.removePrefix("/upload-session/")
        val (existingId, metadata) = pendingSessions.remove(sessionId) ?: return respond(exchange, 404, "{}")
        val bytes = exchange.requestBody.readBytes()
        val id =
            if (existingId != null) {
                filesById.getValue(existingId).apply { bytes.let { this.bytes = it } }
                existingId
            } else {
                val newId = "file-${nextId.getAndIncrement()}"
                metadata.bytes = bytes
                filesById[newId] = metadata
                newId
            }
        respond(exchange, 200, JSONObject().apply { put("id", id) }.toString())
    }

    private fun handleDownload(
        exchange: HttpExchange,
        path: String,
    ) {
        val id = path.removePrefix("/drive/v3/files/")
        val file = filesById[id] ?: return respond(exchange, 404, "{}")
        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, file.bytes.size.toLong())
        exchange.responseBody.use { it.write(file.bytes) }
    }

    private fun handleDelete(
        exchange: HttpExchange,
        path: String,
    ) {
        val id = path.removePrefix("/drive/v3/files/")
        filesById.remove(id)
        respond(exchange, 204, "")
    }

    private fun respond(
        exchange: HttpExchange,
        code: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=UTF-8")
        exchange.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
    }

    private fun decodeQueryParam(
        rawQuery: String,
        key: String,
    ): String? =
        rawQuery.split("&").firstNotNullOfOrNull { pair ->
            val (k, v) = pair.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            if (k == key) java.net.URLDecoder.decode(v, "UTF-8") else null
        }
}
