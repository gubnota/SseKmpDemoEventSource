package sse.kmpdemo

import kotlinx.coroutines.*
import kotlin.test.*

class SseClientIntegrationTest {

    private val baseUrl = System.getProperty("SSE_BASE_URL")
        ?: "http://192.168.50.223:8787"

    private val sseUrl = "$baseUrl/sse"
    private val textUrl = "$baseUrl/text"

    private val enabled: Boolean =
        (System.getProperty("integration") ?: "false").toBoolean()

    @Test
    fun integration_sse_matches_http_text_exactly() = runBlocking {
        // Preflight: server must be reachable, otherwise FAIL
        val expectedText = try {
            httpGet(textUrl)
        } catch (t: Throwable) {
            fail("Backend is not reachable at $textUrl. Start server or set -DSSE_BASE_URL=... . Cause: $t")
        }

        assertEquals(expectedText.isNotBlank(), true)

        val expectedWords = expectedText
            .replace("\r\n", "\n")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val receivedWords = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val done = CompletableDeferred<Unit>()

        val client = SseClient()
        client.start(
            sseUrl,
            object : SseListener {
                override fun onLine(line: String) {
                    // line like: "[word] Hello" or "[done] [DONE]"
                    val payload = line.substringAfter("] ", line).trim()

                    if (payload == "[DONE]") {
                        if (!done.isCompleted) done.complete(Unit)
                        return
                    }

                    // Only collect "word" events. If your server sends other event types, ignore them.
                    if (line.startsWith("[word]")) {
                        receivedWords += payload
                        if (receivedWords.size >= expectedWords.size && !done.isCompleted) {
                            // In case server doesn't send DONE for some reason
                            done.complete(Unit)
                        }
                    }
                }

                override fun onError(error: String) {
                    errors += error
                    if (!done.isCompleted) done.complete(Unit)
                }
            }
        )

        // Wait long enough: ~50ms/word, plus buffer. Use a generous timeout.
        withTimeout(20_000) { done.await() }
        client.stop()

        val unexpectedErrors = errors.filterNot { e ->
            val s = e.lowercase()
            s.contains("cancellation") ||
                    s.contains("cancelled") ||
                    s.contains("closed") ||
                    s.contains("eof") ||
                    s.contains("abort")
        }
        assertTrue(unexpectedErrors.isEmpty(), "Unexpected errors: $unexpectedErrors (all: $errors)")
        println(receivedWords)
        // Exact match of words
        assertEquals(
            expectedWords,
            receivedWords,
            "SSE streamed words must match /text exactly.\nExpectedWords=${expectedWords.size}\nReceivedWords=${receivedWords.size}"
        )
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 1500
            readTimeout = 1500
        }
        val code = conn.responseCode
        val body = runCatching {
            conn.inputStream.bufferedReader().use { it.readText() }
        }.getOrElse {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code from $url. Body: $body")
        }
        body
    }
}
