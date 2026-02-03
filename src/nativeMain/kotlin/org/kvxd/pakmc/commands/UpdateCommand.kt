package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.runBlocking
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import okio.Path.Companion.toPath
import org.kvxd.pakmc.utils.fs
import org.kvxd.pakmc.utils.jsonFormat

class UpdateCommand : CliktCommand(name = "update") {

    private val allowUnstable by option("--allow-unstable", help = "Allow Beta/Alpha versions").flag(default = false)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    private val t = Terminal()

    override fun help(context: Context): String = "Update all mods to the latest version"

    override fun run() = runBlocking {
        if (!fs.exists("pakmc.json".toPath())) {
            t.println(red("Error: Not a pakmc directory"))
            return@runBlocking
        }

        val config = jsonFormat.decodeFromString<PakConfig>(fs.read("pakmc.json".toPath()) { readUtf8() })
        val modsPath = "contents/mods".toPath()
        val cfKey = apiKey ?: config.curseForgeApiKey

        if (!fs.exists(modsPath)) {
            t.println(yellow("No mods found to update."))
            return@runBlocking
        }

        var updateCount = 0

        fs.list(modsPath).forEach { path ->
            if (path.name.endsWith(".json")) {
                try {
                    val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })
                    val updated = updateMod(meta, config, cfKey)
                    if (updated) updateCount++
                } catch (e: Exception) {
                    t.println(red("! Failed to process ${path.name}: ${e.message}"))
                }
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
            return updateModrinth(meta, config)
        } else if (meta.provider == "cf" || meta.provider == "cf_manual") {
            return updateCurseForge(meta, config, cfKey)
        }
        return false
    }

    private suspend fun updateModrinth(meta: LocalModMeta, config: PakConfig): Boolean {
        val versions = ModrinthApi.getVersions(meta.slug, config.loader, config.mcVersion)
        
        val validVersions = if (allowUnstable) versions else versions.filter { it.version_type == "release" }
        val latest = validVersions.firstOrNull() ?: return false

        val currentFile = meta.fileName

        val latestFile = latest.files.find { it.filename.endsWith(".jar") } ?: latest.files.first()

        if (latestFile.filename != currentFile) {
            t.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latestFile.filename}"))
            
            val newMeta = meta.copy(
                fileName = latestFile.filename,
                hashes = latestFile.hashes,
                downloadUrl = latestFile.url,
                fileSize = latestFile.size
            )

            saveMeta(newMeta)

            return true
        }
        return false
    }

    private suspend fun updateCurseForge(meta: LocalModMeta, config: PakConfig, key: String?): Boolean {
        val projectId = meta.projectId.toIntOrNull() ?: return false
        val files = CurseForgeApi.getFiles(projectId, config.loader, config.mcVersion, key)

        val validFiles = if (allowUnstable) files else files.filter { it.releaseType == 1 }
        val latest = validFiles.firstOrNull() ?: return false

        if (latest.fileName != meta.fileName) {
            t.println(green("↑ Updating ") + white(meta.name) + gray(": ${meta.fileName} -> ${latest.fileName}"))

            val sha1 = latest.hashes.find { it.algo == 1 }?.value ?: ""
            val isManual = latest.downloadUrl == null
            val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${meta.slug}/download/${latest.id}"

            val newMeta = meta.copy(
                fileName = latest.fileName,
                hashes = mapOf("sha1" to sha1),
                downloadUrl = latest.downloadUrl ?: "",
                fileSize = latest.fileLength,
                provider = if (isManual) "cf_manual" else "cf",
                manualLink = if (isManual) manualLink else null
            )

            saveMeta(newMeta)

            return true
        }
        return false
    }

    private fun saveMeta(meta: LocalModMeta) {
        val path = "contents/mods/${meta.slug}.json".toPath()
        fs.write(path) { writeUtf8(jsonFormat.encodeToString(meta)) }
    }
}