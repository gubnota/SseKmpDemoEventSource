import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Ktor core + SSE + logging
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.sse)
            implementation(libs.ktor.client.logging)
        }

        androidMain.dependencies {
            // Android engine
            implementation(libs.ktor.client.okhttp)
            implementation(libs.slf4j.simple)
        }

        iosMain.dependencies {
            // iOS engine
            implementation(libs.ktor.client.darwin)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(libs.slf4j.simple)
            implementation(libs.ktor.client.okhttp)

        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-server-core:${libs.versions.ktor.get()}")
            implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
        }
    }
    jvm("jvm") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
}


android {
    namespace = "sse.kmpdemo.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}
