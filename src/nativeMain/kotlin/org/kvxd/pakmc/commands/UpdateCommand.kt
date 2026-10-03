package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.ModSide
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO

class UpdateCommand : PakCommand(name = "update", help = "Update all unpinned mods to the latest version") {

    private val allowUnstable by option("--allow-unstable", help = "Allow Beta/Alpha versions").flag(default = false)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    override suspend fun execute(config: PakConfig) {
        val mods = ModIO.getAllMods()
        val cfKey = apiKey ?: config.curseForgeApiKey

        if (mods.isEmpty()) {
            terminal.println(yellow("No mods found to update."))
            return
        }

        var updateCount = 0
        val pinnedCount = mods.count { it.pinned }

        mods.forEach { meta ->
            if (meta.pinned) {
                terminal.println(yellow("• Pinned ") + white(meta.name) + gray(": ${meta.fileName} (skipping update)"))
                return@forEach
            }
            try {
                if (updateMod(meta, config, cfKey)) updateCount++
            } catch (e: Exception) {
                terminal.println(red("! Failed to process ${meta.name}: ${e.message}"))
            }
        }

        if (updateCount == 0 && pinnedCount > 0) {
            terminal.println(green("No updates applied. $pinnedCount mod(s) pinned."))
        } else if (updateCount == 0) {
            terminal.println(green("All mods are up to date!"))
        } else {
            val pinnedSummary = if (pinnedCount > 0) " $pinnedCount mod(s) pinned." else ""
            terminal.println(green("Updated $updateCount mod(s).$pinnedSummary"))
        }
    }

    private suspend fun updateMod(meta: LocalModMeta, config: PakConfig, cfKey: String?): Boolean {
        if (meta.provider == "mr") {
            val versions = ModrinthApi.getVersions(meta.slug, config.loader, config.mcVersion)
            val validVersions = if (allowUnstable) versions else versions.filter { it.version_type == "release" }
            val latest = validVersions.firstOrNull() ?: return false

            val latestFile = latest.files.find { it.filename.endsWith(".jar") } ?: latest.files.first()
            val detectedSide = if (meta.sideOverride) {
                meta.side
            } else {
                ModSide.fromModrinthEnvironment(latest.environment)
                    ?: ModrinthApi.getProject(meta.slug)?.let { ModSide.fromModrinth(null, it) }
                    ?: meta.side
            }
            val fileChanged = latestFile.filename != meta.fileName
            val sideChanged = detectedSide != meta.side

            if (fileChanged) {
                terminal.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latestFile.filename}"))
            }
            if (sideChanged) {
                terminal.println(green("↔ Correcting side ") + white(meta.name) + gray(": ${meta.side} -> $detectedSide"))
            }

            if (fileChanged || sideChanged) {
                ModIO.save(meta.copy(
                    side = detectedSide, fileName = latestFile.filename,
                    hashes = latestFile.hashes, downloadUrl = latestFile.url,
                    fileSize = latestFile.size
                ))

                return true
            }

        } else if (meta.provider.startsWith("cf")) {
            val projectId = meta.projectId.toIntOrNull() ?: return false
            val files = CurseForgeApi.getFiles(projectId, config.loader, config.mcVersion, cfKey)
            val validFiles = if (allowUnstable) files else files.filter { it.releaseType == 1 }
            val latest = validFiles.firstOrNull() ?: return false

            if (latest.fileName != meta.fileName) {
                terminal.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latest.fileName}"))

                val sha1 = latest.hashes.find { it.algo == 1 }?.value ?: ""
                val isManual = latest.downloadUrl == null
                val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${meta.slug}/download/${latest.id}"

                ModIO.save(meta.copy(
                    fileName = latest.fileName, hashes = mapOf("sha1" to sha1),
                    downloadUrl = latest.downloadUrl ?: "", fileSize = latest.fileLength,
                    provider = if (isManual) "cf_manual" else "cf",
                    manualLink = if (isManual) manualLink else null
                ))

                return true
            }
        }
        return false
    }
}