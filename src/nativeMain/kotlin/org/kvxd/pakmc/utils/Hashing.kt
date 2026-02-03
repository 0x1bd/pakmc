package org.kvxd.pakmc.utils

import okio.FileSystem
import okio.HashingSink
import okio.Path
import okio.blackholeSink
import okio.buffer
import okio.use

fun calculateHashes(path: Path, fs: FileSystem): Map<String, String> {
    val sha1 = fs.source(path).use { source ->
        val sink = HashingSink.sha1(blackholeSink())
        sink.buffer().use { it.writeAll(source) }
        sink.hash.hex()
    }

    val sha512 = fs.source(path).use { source ->
        val sink = HashingSink.sha512(blackholeSink())
        sink.buffer().use { it.writeAll(source) }
        sink.hash.hex()
    }
    return mapOf("sha1" to sha1, "sha512" to sha512)
}