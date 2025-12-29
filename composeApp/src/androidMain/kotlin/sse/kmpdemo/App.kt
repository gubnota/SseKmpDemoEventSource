package sse.kmpdemo

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val BASE_URL = "http://192.168.50.223:8787"
private const val SSE_URL = "$BASE_URL/sse"
private const val TEXT_URL = "$BASE_URL/text"

@Composable
fun App() {
    val sse = remember { SseClient() }

    var running by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<String>() }
    var lastError by remember { mutableStateOf<String?>(null) }
    var httpText by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose { sse.stop() }
    }

    MaterialTheme {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Backend: $BASE_URL", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = !running,
                    onClick = {
                        lastError = null
                        httpText = null
                        lines.clear()
                        running = true

                        sse.start(SSE_URL, object : SseListener {
                            override fun onLine(line: String) {
                                if (lines.size > 300) lines.removeAt(0)
                                lines.add(line)
                            }

                            override fun onError(error: String) {
                                lastError = error
                                running = false
                            }
                        })
                    }
                ) { Text("Start SSE") }

                OutlinedButton(
                    enabled = running,
                    onClick = {
                        sse.stop()
                        running = false
                    }
                ) { Text("Stop") }

                OutlinedButton(
                    onClick = {
                        lastError = null
                        httpText = null
                        lines.clear()
                    }
                ) { Text("Clear") }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    scope.launch {
                        lastError = null
                        try {
                            httpText = httpGet(TEXT_URL)
                        } catch (t: Throwable) {
                            lastError = t.toString()
                        }
                    }
                }
            ) { Text("Fetch HTTP /text") }

            lastError?.let {
                Spacer(Modifier.height(8.dp))
                Text("Error: $it", style = MaterialTheme.typography.bodySmall)
            }

            httpText?.let {
                Spacer(Modifier.height(8.dp))
                Text("HTTP /text:", style = MaterialTheme.typography.titleSmall)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(12.dp))
            Text("SSE /sse:", style = MaterialTheme.typography.titleSmall)

            LazyColumn(Modifier.fillMaxSize()) {
                items(lines) { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 5000
        readTimeout = 5000
    }
    conn.inputStream.bufferedReader().use { it.readText() }
}
