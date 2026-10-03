package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO

class PinCommand : PakCommand(name = "pin", help = "Pin installed mod(s) at their current version") {

    private val modNames by argument(help = "Name or slug of installed mod(s)").multiple(required = true)

    override suspend fun execute(config: PakConfig) {
        modNames.forEach { query ->
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
