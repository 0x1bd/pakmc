package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO

class UpdateCommand : PakCommand(name = "update", help = "Update all mods to the latest version") {

    private val allowUnstable by option("--allow-unstable", help = "Allow Beta/Alpha versions").flag(default = false)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    override suspend fun execute(config: PakConfig) {
        val mods = ModIO.getAllMods()
        val cfKey = apiKey ?: config.curseForgeApiKey

        if (mods.isEmpty()) {
            t.println(yellow("No mods found to update."))
            return
        }

        var updateCount = 0

        mods.forEach { meta ->
            try {
                if (updateMod(meta, config, cfKey)) updateCount++
            } catch (e: Exception) {
                t.println(red("! Failed to process ${meta.name}: ${e.message}"))
            }
        }

        if (updateCount == 0) {
            t.println(green("All mods are up to date!"))
        } else {
            t.println(green("Updated $updateCount mod(s)."))
        }
    }

    private suspend fun updateMod(meta: LocalModMeta, config: PakConfig, cfKey: String?): Boolean {
        if (meta.provider == "mr") {
            val versions = ModrinthApi.getVersions(meta.slug, config.loader, config.mcVersion)
            val validVersions = if (allowUnstable) versions else versions.filter { it.version_type == "release" }
            val latest = validVersions.firstOrNull() ?: return false

            val latestFile = latest.files.find { it.filename.endsWith(".jar") } ?: latest.files.first()

            if (latestFile.filename != meta.fileName) {
                t.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latestFile.filename}"))
                ModIO.save(meta.copy(
                    fileName = latestFile.filename, hashes = latestFile.hashes,
                    downloadUrl = latestFile.url, fileSize = latestFile.size
                ))

                return true
            }

        } else if (meta.provider.startsWith("cf")) {
            val projectId = meta.projectId.toIntOrNull() ?: return false
            val files = CurseForgeApi.getFiles(projectId, config.loader, config.mcVersion, cfKey)
            val validFiles = if (allowUnstable) files else files.filter { it.releaseType == 1 }
            val latest = validFiles.firstOrNull() ?: return false

            if (latest.fileName != meta.fileName) {
                t.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latest.fileName}"))

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