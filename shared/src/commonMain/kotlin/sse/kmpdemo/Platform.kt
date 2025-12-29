package sse.kmpdemo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform