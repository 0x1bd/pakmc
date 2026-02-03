package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
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

class AddCommand : CliktCommand(name = "add") {

    private val queries by argument(help = "Mod slug(s), ID(s), URL(s), or name(s)").multiple(required = true)

    private val version by argument(help = "Specific version (applies to all if multiple mods)").optional()

    private val provider by option("--provider", help = "Default mod provider (overridden if URL provided)").choice("mr", "modrinth", "cf", "curseforge")
        .default("mr")
    private val side by option("--side", help = "Side restriction").choice("c", "client", "s", "server").default("both")
    private val apiKey by option("--api-key", help = "CurseForge API Key (optional, defaults to built-in)")

    private val t = Terminal()
    private val addedSlugs = mutableSetOf<String>()
    private val addedIds = mutableSetOf<String>()

    override fun help(context: Context): String = "Add mod(s) to the pack"

    override fun run() = runBlocking {
        if (!fs.exists("pakmc.json".toPath())) {
            t.println(red("Error: Not a pakmc directory (missing pakmc.json)"))
            return@runBlocking
        }

        val configStr = fs.read("pakmc.json".toPath()) { readUtf8() }
        val config = jsonFormat.decodeFromString<PakConfig>(configStr)

        val modsPath = "contents/mods".toPath()
        if (fs.exists(modsPath)) {
            fs.list(modsPath).forEach { path ->
                if (path.name.endsWith(".json")) {
                    try {
                        val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })
                        addedSlugs.add(meta.slug)
                        addedIds.add(meta.projectId)
                    } catch (_: Exception) {}
                }
            }
        }

        val defaultNormProvider = if (provider in listOf("cf", "curseforge")) "cf" else "mr"
        val cfKey = apiKey ?: config.curseForgeApiKey

        queries.forEach { rawQuery ->
            val (query, detectedProvider) = resolveIdentity(rawQuery, defaultNormProvider)

            if (detectedProvider == "mr") {
                addModrinthRecursive(query, version, config, side, depth = 0)
            } else {
                addCurseForgeRecursive(query, version, config, side, cfKey, depth = 0)
            }
        }
    }

    private fun resolveIdentity(input: String, defaultProvider: String): Pair<String, String> {
        val mrRegex = Regex("modrinth\\.com/(?:mod|plugin|datapack|resourcepack|shader|project)/([^/?#]+)")
        mrRegex.find(input)?.let {
            return it.groupValues[1] to "mr"
        }

        val cfRegex = Regex("curseforge\\.com/minecraft/mc-mods/([^/?#]+)")
        cfRegex.find(input)?.let {
            return it.groupValues[1] to "cf"
        }

        return input to defaultProvider
    }

    private suspend fun addModrinthRecursive(query: String, versionId: String?, config: PakConfig, side: String, depth: Int) {
        if (depth > 0 && shouldSkip(query)) return

        val project = ModrinthApi.getProject(query) ?: run {
            t.println(red("! Project '$query' not found on Modrinth."))
            return
        }

        if (shouldSkip(project.id, project.slug)) {
            if (depth == 0)
                t.println(yellow("ℹ\uFE0F  Skipped '${project.title}' (already present)"))
            return
        }

        val versions = ModrinthApi.getVersions(project.slug, config.loader, config.mcVersion)
        if (versions.isEmpty()) {
            t.println(red("! No valid versions found for '") + white(project.title) + red("' on ${config.loader} ${config.mcVersion}"))
            return
        }

        val selected = versionId?.let { v -> versions.find { it.id == v || it.version_number == v } }
            ?: versions.firstOrNull()

        if (selected == null) {
            t.println(red("! Version '$versionId' not found for '") + white(project.title) + red("'"))
            return
        }

        val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

        printModStatus(project.title, isManual = false, manualLink = null, depth = depth)

        saveMeta(LocalModMeta(
            name = project.title, slug = project.slug, provider = "mr", side = side,
            fileName = file.filename, hashes = file.hashes, downloadUrl = file.url,
            fileSize = file.size, projectId = project.id
        ))

        track(project.id, project.slug)

        selected.dependencies.filter { it.dependency_type == "required" }.forEach { dep ->
            dep.project_id?.let { pid ->
                addModrinthRecursive(pid, dep.version_id, config, side, depth + 1)
            }
        }
    }

    private suspend fun addCurseForgeRecursive(query: String, version: String?, config: PakConfig, side: String, key: String?, depth: Int) {
        if (depth > 0 && shouldSkip(query)) return

        val mod = query.toIntOrNull()?.let { CurseForgeApi.getMod(it, key) } ?: CurseForgeApi.searchMod(query, key)

        if (mod == null) {
            t.println(red("! Mod '$query' not found on CurseForge."))
            return
        }

        if (shouldSkip(mod.id.toString(), mod.slug)) {
            if (depth == 0) {
                t.println(yellow("ℹ\uFE0F  Skipped '${mod.name}' (already present)"))
            }
            return
        }

        val files = CurseForgeApi.getFiles(mod.id, config.loader, config.mcVersion, key)
        val selected = version?.let { v -> files.find { it.displayName.contains(v) || it.fileName.contains(v) } }
            ?: files.firstOrNull()

        if (selected == null) {
            if (depth == 0) t.println(red("! No compatible files found for '${mod.name}' on MC ${config.mcVersion}"))
            return
        }

        val sha1 = selected.hashes.find { it.algo == 1 }?.value ?: ""
        val isManual = selected.downloadUrl == null
        val dUrl = selected.downloadUrl ?: ""
        val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${mod.slug}/download/${selected.id}"

        printModStatus(mod.name, isManual, manualLink, depth)

        saveMeta(LocalModMeta(
            name = mod.name,
            slug = mod.slug,
            provider = if (isManual) "cf_manual" else "cf",
            side = side,
            fileName = selected.fileName,
            hashes = mapOf("sha1" to sha1),
            downloadUrl = dUrl,
            fileSize = selected.fileLength,
            projectId = mod.id.toString(),
            manualLink = if (isManual) manualLink else null
        ))

        track(mod.id.toString(), mod.slug)

        selected.dependencies.filter { it.relationType == 3 }.forEach { dep ->
            val depId = dep.modId.toString()
            addCurseForgeRecursive(depId, null, config, side, key, depth + 1)
        }
    }

    private fun shouldSkip(id: String, slug: String? = null): Boolean {
        return addedIds.contains(id) || (slug != null && addedSlugs.contains(slug))
    }

    private fun track(id: String, slug: String) {
        addedIds.add(id)
        addedSlugs.add(slug)
    }

    private fun printModStatus(name: String, isManual: Boolean, manualLink: String?, depth: Int) {
        val indent = "   ".repeat(depth)
        val symbol = if (depth == 0) "+ " else "└─ "

        if (isManual) {
            t.println(yellow("$indent$symbol Manual Download: ") + white(name))
            t.println(gray("$indent   Link: ") + blue(manualLink ?: "Unknown"))
        } else {
            t.println(green("$indent$symbol Adding: ") + white(name))
        }
    }

    private fun saveMeta(meta: LocalModMeta) {
        val path = "contents/mods/${meta.slug}.json".toPath()
        fs.createDirectories("contents/mods".toPath())
        fs.write(path) { writeUtf8(jsonFormat.encodeToString(meta)) }
    }
}