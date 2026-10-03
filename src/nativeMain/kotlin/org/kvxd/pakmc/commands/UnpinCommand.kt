package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO

class UnpinCommand : PakCommand(name = "unpin", help = "Allow installed mod(s) to update again") {

    private val modNames by argument(help = "Name or slug of installed mod(s)").multiple(required = true)

    override suspend fun execute(config: PakConfig) {
        modNames.forEach { query ->
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
