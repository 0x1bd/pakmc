package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ConfigIO
import org.kvxd.pakmc.utils.ModIO

class UnpinCommand : PakCommand(name = "unpin", help = "Allow installed mod(s) or the loader to update again") {

    private val modNames by argument(help = "Name or slug of installed mod(s), or 'loader'").multiple(required = true)

    override suspend fun execute(config: PakConfig) {
        modNames.forEach { query ->
            if (query.equals("loader", ignoreCase = true)) {
                ConfigIO.save(config.copy(loaderPinned = false))
                terminal.println(green("✔ Unpinned ") + white("${config.loader} loader"))
                return@forEach
            }
            val meta = ModIO.findLocalMod(query)
            if (meta == null) {
                terminal.println(red("! Mod matching '$query' not found installed."))
            } else if (!meta.pinned) {
                terminal.println(yellow("ℹ ${meta.name} is not pinned."))
            } else {
                ModIO.save(meta.copy(pinned = false))
                terminal.println(green("✔ Unpinned ") + white(meta.name))
            }
        }
    }
}
