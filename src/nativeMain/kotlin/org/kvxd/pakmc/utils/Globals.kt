package org.kvxd.pakmc.utils

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import okio.FileSystem
import platform.posix.system

val jsonFormat = Json {
    prettyPrint = true
    ignoreUnknownKeys = true
    encodeDefaults = true
    coerceInputValues = true
}

val client = HttpClient {
    install(ContentNegotiation) {
        json(jsonFormat)
    }
}

val fs = FileSystem.SYSTEM

fun runCommand(command: String): Int {
    return system(command)
}