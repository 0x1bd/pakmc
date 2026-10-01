package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import com.github.ajalt.mordant.rendering.TextColors.*
import platform.linux.MODE_MASK

class RemoveCommand : PakCommand(name = "remove", help = "Remove one or more mods") {

    private val queries by argument(help = "Mod slug(s), ID(s), URL(s), or name(s)").multiple(required = true)

    override suspend fun execute(config: PakConfig) {
        val modsToRemove = queries.map { query ->
            query to ModIO.findLocalMod(query)
        }

        for ((query, modMeta) in modsToRemove) {
            if (modMeta == null) {
                terminal.println(yellow("? $query did not match any installed mod"))
                continue
            }

            if(ModIO.tryRemove(modMeta.slug))
                terminal.println(red("-  Removed: ") + white(modMeta.name))
        }
    }

}