package ai.core

import io.ktor.client.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.sse.*
import kotlinx.coroutines.*
import kotlin.native.concurrent.ThreadLocal

interface SseListener {
    fun onLine(line: String)
    fun onError(error: String)
}

class SseClient {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun start(url: String, listener: SseListener) {
        stop()
        job = scope.launch {
            try {
                val client = httpClient()
                client.sse(urlString = url) {
                    incoming.collect { event ->
                        val name = event.event ?: "message"
                        val data = event.data ?: ""
                        listener.onLine("[$name] $data")
                    }
                }
            } catch (t: Throwable) {
                listener.onError(t.toString())
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

@ThreadLocal
private var cachedClient: HttpClient? = null

private fun httpClient(): HttpClient {
    cachedClient?.let { return it }
    val c = HttpClient {
        install(SSE)
        install(Logging) { level = LogLevel.NONE }
        expectSuccess = false
    }
    cachedClient = c
    return c
}
