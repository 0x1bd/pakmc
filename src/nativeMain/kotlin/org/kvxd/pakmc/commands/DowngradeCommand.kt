package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
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
import org.kvxd.pakmc.models.MrVersion
import org.kvxd.pakmc.models.CfFile

class DowngradeCommand : CliktCommand(name = "downgrade") {

    private val modNames by argument(help = "Name or slug of installed mod(s)").multiple(required = true)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    private val t = Terminal()

    override fun help(context: Context): String = "Select a specific version for an installed mod (Downgrade/Upgrade)"

    override fun run() = runBlocking {
        if (!fs.exists("pakmc.json".toPath())) {
            t.println(red("Error: Not a pakmc directory"))
            return@runBlocking
        }

        val config = jsonFormat.decodeFromString<PakConfig>(fs.read("pakmc.json".toPath()) { readUtf8() })
        val modsPath = "contents/mods".toPath()

        modNames.forEach { query ->
            val file = findLocalMod(modsPath, query)
            if (file == null) {
                t.println(red("! Mod matching '$query' not found in contents/mods"))
                return@forEach
            }

            try {
                val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(file) { readUtf8() })
                changeVersion(meta, config, apiKey ?: config.curseForgeApiKey)
            } catch (e: Exception) {
                t.println(red("! Error processing '$query': ${e.message}"))
            }
        }
    }

    private fun findLocalMod(path: okio.Path, query: String): okio.Path? {
        if (!fs.exists(path)) return null

        val slugPath = path.div("$query.json")
        if (fs.exists(slugPath)) return slugPath

        return fs.list(path).find {
            if (!it.name.endsWith(".json")) return@find false
            try {
                val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(it) { readUtf8() })
                meta.slug.equals(query, ignoreCase = true) || meta.name.contains(query, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }
    }

    private suspend fun changeVersion(meta: LocalModMeta, config: PakConfig, cfKey: String?) {
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
            t.println(red("! No versions found for ${meta.name}"))
            return
        }

        t.println(cyan("Available versions:"))
        versions.take(20).forEachIndexed { index, v ->
            val isCurrent = v.files.any { it.filename == meta.fileName }
            val marker = if (isCurrent) green("*") else " "
            val typeColor = if (v.version_type == "release") green else yellow
            t.println(
                "$marker ${index + 1}. ${typeColor(v.version_type.uppercase())} ${white(v.version_number)} ${
                    gray(
                        "(${v.date_published.take(10)})"
                    )
                }"
            )
        }

        t.print("Select version (1-${minOf(versions.size, 20)}): ")
        val idx = readlnOrNull()?.toIntOrNull()
        if (idx != null && idx in 1..minOf(versions.size, 20)) {
            val selected = versions[idx - 1]
            val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

            val newMeta = meta.copy(
                fileName = file.filename,
                hashes = file.hashes,
                downloadUrl = file.url,
                fileSize = file.size
            )
            saveMeta(newMeta)
            t.println(green("✔ Updated ${meta.name} to ${selected.version_number}"))
        } else {
            t.println(yellow("Cancelled."))
        }
    }

    private suspend fun handleCurseForge(meta: LocalModMeta, config: PakConfig, key: String?) {
        val projectId = meta.projectId.toIntOrNull() ?: return
        val files = CurseForgeApi.getFiles(projectId, config.loader, config.mcVersion, key)

        if (files.isEmpty()) {
            t.println(red("! No files found for ${meta.name}"))
            return
        }

        t.println(cyan("Available files:"))
        files.take(20).forEachIndexed { index, f ->
            val isCurrent = f.fileName == meta.fileName
            val marker = if (isCurrent) green("*") else " "
            val type = when (f.releaseType) {
                1 -> "R"; 2 -> "B"; else -> "A"
            }
            val color = if (f.releaseType == 1) green else yellow
            t.println("$marker ${index + 1}. ${color(type)} ${white(f.displayName)} ${gray("(${f.fileDate.take(10)})")}")
        }

        t.print("Select file (1-${minOf(files.size, 20)}): ")
        val idx = readlnOrNull()?.toIntOrNull()
        if (idx != null && idx in 1..minOf(files.size, 20)) {
            val selected = files[idx - 1]
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
            saveMeta(newMeta)
            t.println(green("✔ Updated ${meta.name} to ${selected.displayName}"))
        } else {
            t.println(yellow("Cancelled."))
        }
    }

    private fun saveMeta(meta: LocalModMeta) {
        val path = "contents/mods/${meta.slug}.json".toPath()
        fs.write(path) { writeUtf8(jsonFormat.encodeToString(meta)) }
    }
}