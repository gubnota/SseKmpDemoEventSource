package sse.kmpdemo

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import kotlin.test.*

class SseClientTest {

    private lateinit var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>
    private val port = 19090
    private val baseUrl = "http://127.0.0.1:$port"

    @BeforeTest
    fun setup() {
        server = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            routing {
                get("/sse") {
                    call.response.cacheControl(CacheControl.NoCache(null))
                    call.respondTextWriter(contentType = ContentType.Text.EventStream) {
                        val text = "hello world this is a full stream test"
                        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }

                        for (w in words) {
                            write("event: word\n")
                            write("data: $w\n\n")
                            flush()
                            delay(20)
                        }

                        write("event: done\n")
                        write("data: [DONE]\n\n")
                        flush()
                        // writer closes after block ends
                    }
                }            }
        }.start(wait = false)
    }

    @AfterTest
    fun tearDown() {
        server.stop(0, 0)
    }

    @Test
    fun sseClient_receives_all_streamed_words_in_order() = runBlocking {
        val expectedText = "hello world this is a full stream test"
        val expectedWords = expectedText.split(Regex("\\s+")).filter { it.isNotBlank() }

        val receivedWords = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()

        val client = SseClient()
        client.start(
            "$baseUrl/sse",
            object : SseListener {
                override fun onLine(line: String) {
                    // line format: "[word] <data>"
                    // be tolerant: strip prefix if present
                    val w = line.substringAfter("] ", line).trim()
                    if (w == "[DONE]") {
                        if (!done.isCompleted) done.complete(Unit)
                        return
                    }
                    receivedWords += w
                    if (receivedWords.size >= expectedWords.size && !done.isCompleted) {
                        done.complete(Unit)
                    }
                }

                override fun onError(error: String) {
                    errors += error
                    if (!done.isCompleted) done.complete(Unit)
                }
            }
        )

        // Wait for completion or timeout
        withTimeout(5_000) { done.await() }

        client.stop()

        // Ignore cancellation/close noise
        val unexpectedErrors = errors.filterNot { e ->
            val s = e.lowercase()
            s.contains("cancellation") ||
                    s.contains("cancelled") ||
                    s.contains("closed") ||
                    s.contains("eof") ||
                    s.contains("abort")
        }

        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: $unexpectedErrors (all: $errors)")
        assertEquals(expectedWords, receivedWords, "Streamed words should match exactly")
    }

    @Test
    fun sseClient_reports_connection_error() = runBlocking {
        val errors = mutableListOf<String>()

        val client = SseClient()
        client.start(
            "http://localhost:1/does-not-exist",
            object : SseListener {
                override fun onLine(line: String) = Unit
                override fun onError(error: String) {
                    errors += error
                }
            }
        )

        delay(200)
        client.stop()

        assertTrue(errors.isNotEmpty(), "Error should be reported")
    }

    @Test
    fun stop_cancels_active_stream() = runBlocking {
        val received = mutableListOf<String>()

        val client = SseClient()
        client.start(
            "$baseUrl/sse",
            object : SseListener {
                override fun onLine(line: String) {
                    received += line
                }

                override fun onError(error: String) = Unit
            }
        )

        delay(60)
        client.stop()

        val countAfterStop = received.size
        delay(200)

        assertEquals(
            countAfterStop,
            received.size,
            "No events should be received after stop()"
        )
    }
}
