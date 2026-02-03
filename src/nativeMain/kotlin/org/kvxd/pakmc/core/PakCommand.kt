package org.kvxd.pakmc.core

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.fs
import org.kvxd.pakmc.utils.jsonFormat

abstract class PakCommand(
    name: String,
    private val help: String = ""
) : CliktCommand(name = name) {

    protected val t = Terminal()

    abstract suspend fun execute(config: PakConfig)

    override fun help(context: Context): String = help

    final override fun run() = runBlocking {
        val configPath = "pakmc.json".toPath()

        if (!fs.exists(configPath)) {
            t.println(red("Error: Not a pakmc directory (missing pakmc.json)"))
            return@runBlocking
        }

        try {
            val configStr = fs.read(configPath) { readUtf8() }
            val config = jsonFormat.decodeFromString<PakConfig>(configStr)
            execute(config)
        } catch (e: Exception) {
            t.println(red("Error loading config: ${e.message}"))
        }
    }
}