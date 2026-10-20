package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ConfigIO
import org.kvxd.pakmc.utils.LoaderVersions
import org.kvxd.pakmc.utils.ModIO

class PinCommand : PakCommand(name = "pin", help = "Pin installed mod(s) or the loader at their current version") {

    private val modNames by argument(help = "Name or slug of installed mod(s), or 'loader'").multiple(required = true)
    private val version by option("--version", help = "Specific loader version to pin (with 'loader')")

    override suspend fun execute(config: PakConfig) {
        if (version != null && modNames.any { !it.equals("loader", ignoreCase = true) }) {
            terminal.println(red("! --version is only supported with 'pakmc pin loader'."))
            return
        }
        var currentConfig = config
        modNames.forEach { query ->
            if (query.equals("loader", ignoreCase = true)) {
                val selected = version?.let {
                    require(it.isNotBlank() && it != "*") { "A concrete loader version is required." }
                    currentConfig.copy(loaderVersion = it)
                } ?: if (currentConfig.loaderVersion.isNullOrBlank() || currentConfig.loaderVersion == "*") {
                    LoaderVersions.prepare(currentConfig) { _, _ -> false }
                } else currentConfig
                currentConfig = selected.copy(loaderPinned = true)
                ConfigIO.save(currentConfig)
                terminal.println(green("✔ Pinned ") + white("${currentConfig.loader} loader") + gray(" at ${currentConfig.loaderVersion}") + yellow(" [pinned]"))
                return@forEach
            }
            val meta = ModIO.findLocalMod(query)
            if (meta == null) {
                terminal.println(red("! Mod matching '$query' not found installed."))
            } else if (meta.pinned) {
                terminal.println(yellow("ℹ ${meta.name} is already pinned (${meta.fileName})."))
            } else {
                ModIO.save(meta.copy(pinned = true))
                terminal.println(green("✔ Pinned ") + white(meta.name) + gray(" at ${meta.fileName}"))
            }
        }
    }
}
