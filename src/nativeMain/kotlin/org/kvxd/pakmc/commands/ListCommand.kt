package org.kvxd.pakmc.commands

import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import com.github.ajalt.mordant.rendering.TextColors.*

class ListCommand: PakCommand(name = "list", help = "List all installed mods") {

    override suspend fun execute(config: PakConfig) {
        val loaderPinnedStatus = if (config.loaderPinned) yellow(" [pinned]") else ""
        terminal.println("${config.loader} loader " + gray("(${config.loaderVersion ?: "unresolved"})") + loaderPinnedStatus)
        ModIO.getAllMods().forEach { modMeta ->
            val pinnedStatus = if (modMeta.pinned) yellow(" [pinned]") else ""
            terminal.println("${modMeta.name} " + gray("(${modMeta.side})") + pinnedStatus)
        }
    }

}
