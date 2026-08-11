package io.sanato.appkit.download.engine

import io.sanato.appkit.download.DownloadError
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * End-to-end HTTP correctness tests against a real [MockWebServer] — the
 * whole reason `:downloadkit` depends on `:core-net`'s OkHttp instead of a
 * hand-rolled `HttpURLConnection` client is that range-resume correctness is
 * hard enough to get right that it needs exactly this kind of test.
 */
class OkHttpDownloadEngineTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var engine: DownloadEngine

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        engine = OkHttpDownloadEngine(OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun freshResume() = ResumeInfo(bytesDownloaded = 0L, etag = null, lastModified = null, acceptRanges = true)

    /** Runs the engine to completion (or throws) and returns every [ResponseInfo] it reported via `onHeaders`. */
    private fun download(
        destination: File,
        resume: ResumeInfo,
        progress: MutableList<Long> = mutableListOf(),
    ): List<ResponseInfo> {
        val collectedHeaders = mutableListOf<ResponseInfo>()
        runBlocking {
            engine.download(
                url = server.url("/file").toString(),
                headers = emptyMap(),
                destination = destination,
                resume = resume,
                onHeaders = { collectedHeaders += it },
                onProgress = { progress += it },
            )
        }
        return collectedHeaders
    }

    @Test
    fun `fresh 200 download writes the full body and reports totals`() {
        val body = "0123456789"
        server.enqueue(MockResponse(code = 200, headers = headersOf("ETag", "\"v1\""), body = body))
        val destination = tempFolder.newFile("out.bin")
        val progress = mutableListOf<Long>()

        val headersSeen = download(destination, freshResume(), progress)

        assertEquals(body, destination.readText())
        assertEquals(1, headersSeen.size)
        assertEquals(10L, headersSeen[0].totalBytes)
        assertEquals("\"v1\"", headersSeen[0].etag)
        assertTrue(headersSeen[0].acceptRanges)
        assertEquals(listOf(10L), progress)
    }

    @Test
    fun `Accept-Ranges none on a fresh download is reported as non-resumable`() {
        server.enqueue(MockResponse(code = 200, headers = headersOf("Accept-Ranges", "none"), body = "hello"))
        val destination = tempFolder.newFile("out.bin")

        val headersSeen = download(destination, freshResume())

        assertFalse(headersSeen[0].acceptRanges)
    }

    @Test
    fun `206 resume appends to the existing partial file at the correct offset`() {
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("01234")
        server.enqueue(
            MockResponse(
                code = 206,
                headers = headersOf("Content-Range", "bytes 5-9/10", "ETag", "\"v1\""),
                body = "56789",
            ),
        )

        val resume = ResumeInfo(bytesDownloaded = 5L, etag = "\"v1\"", lastModified = null, acceptRanges = true)
        val headersSeen = download(destination, resume)

        assertEquals("0123456789", destination.readText())
        assertEquals(10L, headersSeen[0].totalBytes)

        val sentRequest = server.takeRequest()
        assertEquals("bytes=5-", sentRequest.headers["Range"])
        assertEquals("\"v1\"", sentRequest.headers["If-Range"])
    }

    @Test
    fun `server ignoring Range and returning 200 truncates and restarts from scratch`() {
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("stale-partial-data")
        val fullBody = "brand-new-full-body"
        server.enqueue(MockResponse(code = 200, body = fullBody))

        val resume = ResumeInfo(bytesDownloaded = 19L, etag = "\"old\"", lastModified = null, acceptRanges = true)
        val headersSeen = download(destination, resume)

        assertEquals(fullBody, destination.readText())
        assertFalse(
            "a 200 in response to a Range request must mark the task non-resumable",
            headersSeen[0].acceptRanges,
        )
    }

    @Test
    fun `416 triggers exactly one from-scratch retry which then succeeds`() {
        server.enqueue(MockResponse(code = 416))
        server.enqueue(MockResponse(code = 200, body = "fresh-copy"))
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("garbage-offset-was-wrong")

        val resume = ResumeInfo(bytesDownloaded = 24L, etag = "\"v1\"", lastModified = null, acceptRanges = true)
        download(destination, resume)

        assertEquals("fresh-copy", destination.readText())
        assertEquals(2, server.requestCount)
        server.takeRequest() // the first (416'd) attempt
        val retryRequest = server.takeRequest() // the from-scratch retry
        assertNull("the retry must be a plain request, not another Range attempt", retryRequest.headers["Range"])
    }

    @Test
    fun `a second consecutive 416 is a non-retryable error`() {
        server.enqueue(MockResponse(code = 416))
        server.enqueue(MockResponse(code = 416))
        val destination = tempFolder.newFile("out.bin")

        val resume = ResumeInfo(bytesDownloaded = 5L, etag = null, lastModified = null, acceptRanges = true)
        val error =
            assertThrows(DownloadError.UnexpectedResponse::class.java) {
                download(destination, resume)
            }
        assertEquals(416, error.httpCode)
    }

    @Test
    fun `mismatched Content-Range start throws UnexpectedResponse`() {
        server.enqueue(MockResponse(code = 206, headers = headersOf("Content-Range", "bytes 3-9/10"), body = "789"))
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("01234")

        val resume = ResumeInfo(bytesDownloaded = 5L, etag = "\"v1\"", lastModified = null, acceptRanges = true)
        assertThrows(DownloadError.UnexpectedResponse::class.java) {
            download(destination, resume)
        }
    }

    @Test
    fun `malformed Content-Range throws UnexpectedResponse`() {
        server.enqueue(MockResponse(code = 206, headers = headersOf("Content-Range", "not-a-range"), body = "x"))
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("01234")

        val resume = ResumeInfo(bytesDownloaded = 5L, etag = null, lastModified = null, acceptRanges = true)
        assertThrows(DownloadError.UnexpectedResponse::class.java) {
            download(destination, resume)
        }
    }

    @Test
    fun `HTTP error status is surfaced as DownloadError Http`() {
        server.enqueue(MockResponse(code = 500))
        val destination = tempFolder.newFile("out.bin")

        val error =
            assertThrows(DownloadError.Http::class.java) {
                download(destination, freshResume())
            }
        assertEquals(500, error.code)
    }

    @Test
    fun `a task with acceptRanges false never sends a Range header even with existing bytes`() {
        val destination = tempFolder.newFile("out.bin")
        destination.writeText("01234")
        server.enqueue(MockResponse(code = 200, body = "0123456789"))

        val resume = ResumeInfo(bytesDownloaded = 5L, etag = "\"v1\"", lastModified = null, acceptRanges = false)
        download(destination, resume)

        assertNull(server.takeRequest().headers["Range"])
        assertEquals("0123456789", destination.readText())
    }
}
