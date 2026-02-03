plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

group = "org.kvxd"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    sourceSets.all {
        languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
    }

    linuxX64("native") {
        binaries {
            executable {
                entryPoint = "main"
            }
        }

        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    // Fix duplicate symbol issues in linking because of clikt
                    freeCompilerArgs.add("-linker-options")
                    freeCompilerArgs.add("-z muldefs")
                }
            }
        }
    }

    sourceSets {
        nativeMain.dependencies {
            implementation(libs.clikt)
            implementation(libs.okio)
            implementation(libs.serializationJson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.curl)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }

        val nativeTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}
