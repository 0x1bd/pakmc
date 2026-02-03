package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import org.kvxd.pakmc.utils.VersionSelector

class SelectCommand : PakCommand(name = "select", help = "Select a specific version for installed mod(s)") {

    private val modNames by argument(help = "Name or slug of installed mod(s)").multiple(required = true)
    private val version by option("-v", "--version", help = "Target version ID, number, or filename part")
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    override suspend fun execute(config: PakConfig) {
        val cfKey = apiKey ?: config.curseForgeApiKey

        modNames.forEach { query ->
            val meta = ModIO.findLocalMod(query)
            
            if (meta == null) {
                t.println(red("! Mod matching '$query' not found installed."))
                
                if (query.matches(Regex(".*\\d.*"))) {
                    t.println(gray("  (Did you mean to use -v \"$query\"?)"))
                }
                return@forEach
            }

            try {
                processSelection(meta, config, cfKey)
            } catch (e: Exception) {
                t.println(red("! Error processing '$query': ${e.message}"))
            }
        }
    }

    private suspend fun processSelection(meta: LocalModMeta, config: PakConfig, cfKey: String?) {
        t.println(blue("Processing ${meta.name} (Current: ${meta.fileName})"))

        if (meta.provider == "mr") {
            handleModrinth(meta, config)
        } else if (meta.provider.startsWith("cf")) {
            handleCurseForge(meta, config, cfKey)
        } else {
            t.println(red("! Unknown provider: ${meta.provider}"))
        }
    }

    private suspend fun handleModrinth(meta: LocalModMeta, config: PakConfig) {
        val versions = ModrinthApi.getVersions(meta.slug, config.loader, config.mcVersion)
        
        if (versions.isEmpty()) {
            t.println(red("! No versions found for ${meta.name} on ${config.loader} ${config.mcVersion}"))
            return
        }

        val selected = if (version != null) {
            versions.find { it.id == version || it.version_number == version } ?: run {
                t.println(red("! Version '$version' not found for ${meta.name}"))
                return
            }
        } else {
            val candidates = VersionSelector.fromModrinth(versions, meta.fileName)
            VersionSelector.prompt(candidates, meta.name) ?: return
        }

        val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

        if (meta.fileName != file.filename) {
            val newMeta = meta.copy(
                fileName = file.filename,
                hashes = file.hashes,
                downloadUrl = file.url,
                fileSize = file.size
            )
            ModIO.save(newMeta)
            t.println(green("✔ Set ${meta.name} to ${selected.version_number}"))

        } else {
            t.println(yellow("ℹ Version already selected."))
        }
    }

    private suspend fun handleCurseForge(meta: LocalModMeta, config: PakConfig, key: String?) {
        val projectId = meta.projectId.toIntOrNull() ?: return
        val files = CurseForgeApi.getFiles(projectId, config.loader, config.mcVersion, key)

        if (files.isEmpty()) {
            t.println(red("! No files found for ${meta.name} on ${config.loader} ${config.mcVersion}"))
            return
        }

        val selected = if (version != null) {
            files.find { it.displayName.contains(version!!) || it.fileName.contains(version!!) } ?: run {
                t.println(red("! File matching '$version' not found for ${meta.name}"))
                return
            }
        } else {
            val candidates = VersionSelector.fromCurseForge(files, meta.fileName)
            VersionSelector.prompt(candidates, meta.name) ?: return
        }

        if (meta.fileName != selected.fileName) {
            val sha1 = selected.hashes.find { it.algo == 1 }?.value ?: ""
            val isManual = selected.downloadUrl == null
            val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${meta.slug}/download/${selected.id}"

            val newMeta = meta.copy(
                fileName = selected.fileName,
                hashes = mapOf("sha1" to sha1),
                downloadUrl = selected.downloadUrl ?: "",
                fileSize = selected.fileLength,
                provider = if (isManual) "cf_manual" else "cf",
                manualLink = if (isManual) manualLink else null
            )
            ModIO.save(newMeta)
            t.println(green("✔ Set ${meta.name} to ${selected.displayName}"))
        } else {
            t.println(yellow("ℹ Version already selected."))
        }
    }
}