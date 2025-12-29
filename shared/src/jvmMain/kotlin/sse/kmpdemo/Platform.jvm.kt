package sse.kmpdemo

actual fun getPlatform(): Platform = object : Platform {
    override val name: String = "JVM"
}