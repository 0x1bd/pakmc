package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.fs
import org.kvxd.pakmc.utils.jsonFormat

class InitCommand : CliktCommand(name = "init") {

    private val name by argument(help = "Name of the modpack")
    private val mc by option("--mc", help = "Minecraft Version").required()
    private val loader by option("--loader", help = "Mod Loader (neoforge, fabric, forge)").required()
    private val author by option("--author", help = "Modpack author").default("Unknown")
    private val cfKey by option("--cf-key", help = "CurseForge API Key (optional, saves to config)")
    private val t = Terminal()

    override fun help(context: Context): String = "Initialize a new modpack"

    override fun run() {
        val configFile = "pakmc.json".toPath()

        if (fs.exists(configFile)) {
            t.println(red("Error: pakmc.json already exists."))
            return
        }

        val config = PakConfig(
            name = name,
            author = author,
            mcVersion = mc,
            loader = loader,
            curseForgeApiKey = cfKey
        )

        val dirs = listOf(
            "contents/mods",
            "contents/jarmods",
            "contents/configs"
        )

        dirs.forEach { fs.createDirectories(it.toPath()) }

        fs.write(configFile) {
            writeUtf8(jsonFormat.encodeToString(config))
        }

        t.println(green("Initialized modpack '$name' for Minecraft $mc ($loader)"))
        if (cfKey != null) t.println(gray("CurseForge API Key saved."))
    }
}