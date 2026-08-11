package io.sanato.appkit.download.engine

import io.sanato.appkit.download.DownloadError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * The real transfer implementation. Correctness here is entirely about
 * getting HTTP range-resume right — see the three-way branch in
 * [executeOnce] and its KDoc for the exact rules. Every rule below exists
 * because getting it wrong doesn't fail loudly — it silently produces a
 * corrupted file that only a [DownloadError.ChecksumMismatch] (if the caller
 * even asked for one) would catch.
 */
internal class OkHttpDownloadEngine(
    private val client: OkHttpClient,
) : DownloadEngine {
    override suspend fun download(
        url: String,
        headers: Map<String, String>,
        destination: File,
        resume: ResumeInfo,
        onHeaders: (ResponseInfo) -> Unit,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            // A 416 means our resume assumption was wrong (server-side file
            // changed, or the offset is simply bogus) — self-heal by
            // truncating and re-issuing as a fresh, non-ranged request rather
            // than bubbling an error up and burning one of the queue's
            // limited retry attempts on a purely mechanical case. Bounded to
            // one retry: a *second* 416 on a fresh (offset-0) request is a
            // real server problem, not something restarting again will fix.
            var effectiveResume = resume
            repeat(2) { attempt ->
                val outcome = executeOnce(url, headers, destination, effectiveResume, onHeaders, onProgress)
                if (outcome != RestartSignal) return@withContext
                if (attempt > 0) {
                    throw DownloadError.UnexpectedResponse(416, "persisted after restarting from scratch")
                }
                effectiveResume =
                    ResumeInfo(bytesDownloaded = 0L, etag = null, lastModified = null, acceptRanges = true)
            }
        }
    }

    /** Sentinel return value: "the caller must retry with a from-scratch [ResumeInfo]." Not an exception — this is an expected, self-healing path, not a failure. */
    private object RestartSignal

    private suspend fun executeOnce(
        url: String,
        headers: Map<String, String>,
        destination: File,
        resume: ResumeInfo,
        onHeaders: (ResponseInfo) -> Unit,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ): RestartSignal? {
        val canResume = resume.bytesDownloaded > 0 && resume.acceptRanges
        val request =
            Request
                .Builder()
                .url(url)
                .apply {
                    headers.forEach { (name, value) -> addHeader(name, value) }
                    if (canResume) {
                        addHeader("Range", "bytes=${resume.bytesDownloaded}-")
                        // Without If-Range, a server whose underlying file changed between
                        // attempts will happily return a 206 for the *new* file's tail —
                        // silently splicing together bytes from two different files. One of
                        // ETag/Last-Modified is virtually always present; if neither was
                        // captured on the previous attempt there is nothing safe to send,
                        // so the Range request goes out unconditionally (the 416/200
                        // fallback paths below still keep this correct, just not resumable
                        // in that one edge case).
                        (resume.etag ?: resume.lastModified)?.let { validator -> addHeader("If-Range", validator) }
                    }
                }.build()

        val call = client.newCall(request)
        currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause is CancellationException) call.cancel()
        }

        val response =
            try {
                call.executeSuspending()
            } catch (e: IOException) {
                throw DownloadError.Network(e)
            }

        response.use {
            when (response.code) {
                206 -> handlePartialContent(response, resume, destination, onHeaders, onProgress)
                200 -> handleFullContent(response, resume, destination, onHeaders, onProgress)
                416 -> return RestartSignal
                else -> throw DownloadError.Http(response.code)
            }
        }
        return null
    }

    private suspend fun handlePartialContent(
        response: Response,
        resume: ResumeInfo,
        destination: File,
        onHeaders: (ResponseInfo) -> Unit,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        val range = response.header("Content-Range")
        val (start, total) =
            parseContentRange(range)
                ?: throw DownloadError.UnexpectedResponse(206, "missing/malformed Content-Range: $range")
        if (start != resume.bytesDownloaded) {
            throw DownloadError.UnexpectedResponse(
                206,
                "Content-Range starts at $start, expected ${resume.bytesDownloaded}",
            )
        }

        val info =
            ResponseInfo(
                totalBytes = total,
                etag = response.header("ETag") ?: resume.etag,
                lastModified = response.header("Last-Modified") ?: resume.lastModified,
                acceptRanges = true,
            )
        onHeaders(info)
        streamBody(response, destination, append = true, expectedTotal = total, onProgress)
    }

    private suspend fun handleFullContent(
        response: Response,
        resume: ResumeInfo,
        destination: File,
        onHeaders: (ResponseInfo) -> Unit,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        // We asked for a Range and got a plain 200 back — the server is
        // ignoring Range for this resource. Treat that as authoritative:
        // don't keep re-requesting ranges on every future attempt, just
        // accept a from-scratch redownload each time.
        val serverIgnoredRange = resume.bytesDownloaded > 0
        val acceptRangesHeader = response.header("Accept-Ranges")
        val acceptRanges =
            when {
                serverIgnoredRange -> false
                acceptRangesHeader == "none" -> false
                else -> true
            }
        val total = response.header("Content-Length")?.toLongOrNull() ?: -1L

        val info =
            ResponseInfo(
                totalBytes = total,
                etag = response.header("ETag"),
                lastModified = response.header("Last-Modified"),
                acceptRanges = acceptRanges,
            )
        onHeaders(info)
        streamBody(response, destination, append = false, expectedTotal = total, onProgress)
    }

    private suspend fun streamBody(
        response: Response,
        destination: File,
        append: Boolean,
        expectedTotal: Long,
        onProgress: (bytesDownloaded: Long) -> Unit,
    ) {
        // OkHttp's Response.body is non-null by construction — even a zero-length
        // reply has an (empty) body, so there's nothing to null-check here.
        val body = response.body
        val startOffset = if (append) destination.length() else 0L

        try {
            RandomAccessFile(destination, "rw").use { file ->
                if (!append) file.setLength(0)
                file.seek(startOffset)

                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var written = startOffset
                    while (true) {
                        // Cheap check between chunks — the real cancellation trigger is
                        // `call.cancel()` (wired in `executeOnce`), which closes the
                        // socket and makes the blocking `input.read` below throw; this
                        // is a defensive backstop for the case a chunk boundary lands
                        // right as cancellation fires.
                        currentCoroutineContext().ensureActive()
                        // Classified as Network (not the generic Io catch below) —
                        // a dropped connection mid-stream is a retry-and-resume case,
                        // not a "this device's disk is broken" case.
                        val read =
                            try {
                                input.read(buffer)
                            } catch (e: IOException) {
                                throw DownloadError.Network(e)
                            }
                        if (read < 0) break
                        file.write(buffer, 0, read)
                        written += read
                        onProgress(written)
                    }
                    if (expectedTotal > 0 && written != expectedTotal) {
                        throw DownloadError.UnexpectedResponse(
                            response.code,
                            "expected $expectedTotal bytes total, got $written",
                        )
                    }
                }
            }
        } catch (e: DownloadError) {
            throw e
        } catch (e: IOException) {
            // Anything else that reaches here is a local file-system failure:
            // opening/seeking/truncating/writing `destination` (disk full,
            // permission revoked mid-write, storage unmounted, ...).
            throw DownloadError.Io(e)
        }
    }

    /**
     * `Call.execute()` blocks the calling thread — safe here because the
     * whole function already runs on [Dispatchers.IO] via the outer
     * `withContext`, but it must still observe cooperative cancellation
     * between chunks (see the `ensureActive()` inside [streamBody]'s read
     * loop — added at the call site, not here, since this method only
     * covers the connect+headers phase).
     */
    private suspend fun Call.executeSuspending(): Response {
        currentCoroutineContext().ensureActive()
        return execute()
    }

    private fun parseContentRange(header: String?): Pair<Long, Long>? {
        // "bytes 100-999/1000" — the denominator (not Content-Length, which
        // for a 206 is only this segment's length) is the true total size.
        val match = CONTENT_RANGE_REGEX.matchEntire(header ?: return null) ?: return null
        val (startStr, _, totalStr) = match.destructured
        return startStr.toLong() to totalStr.toLong()
    }

    private companion object {
        const val BUFFER_SIZE = 16 * 1024
        val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+)""")
    }
}
