package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
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

    private val version by option("-v", "--version", help = "Specific version (applies to all if multiple mods)")

    private val provider by option("--provider", help = "Default mod provider (overridden if URL provided)").choice("mr", "modrinth", "cf", "curseforge")
        .default("mr")
    private val side by option("--side", help = "Side restriction").choice("c", "client", "s", "server").default("both")
    private val apiKey by option("--api-key", help = "CurseForge API Key (optional, defaults to built-in)")

    private val t = Terminal()

    private val addedSlugs = mutableSetOf<String>()
    private val addedIds = mutableSetOf<String>()

    private val visitedProjects = mutableSetOf<String>()

    override fun help(context: Context): String = "Add mod(s) to the pack"

    override fun run() = runBlocking {
        if (!fs.exists("pakmc.json".toPath())) {
            t.println(red("Error: Not a pakmc directory (missing pakmc.json)"))
            return@runBlocking
        }

        visitedProjects.clear()

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
                val foundOnMr = addModrinthRecursive(query, version, config, side, depth = 0)

                val isDirectUrl = rawQuery.contains("modrinth.com")
                if (!foundOnMr && !isDirectUrl) {
                    fallbackToCurseForge(query, version, config, side, cfKey)
                }
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

    private suspend fun fallbackToCurseForge(query: String, version: String?, config: PakConfig, side: String, key: String?) {
        val mod = CurseForgeApi.searchMod(query, key)

        if (mod != null) {
            t.println(yellow("? Project '$query' not found on Modrinth."))
            t.print(white("  Found ") + cyan(mod.name) + white(" on CurseForge. Add this mod? [Y/n] "))

            val input = readlnOrNull()?.trim()?.lowercase() ?: ""
            if (input.isEmpty() || input == "y" || input == "yes") {
                addCurseForgeRecursive(mod.id.toString(), version, config, side, key, depth = 0)
            } else {
                t.println(yellow("ℹ\uFE0F  Skipped fallback for '$query'."))
            }
        } else {
            t.println(red("! '$query' not found on Modrinth or CurseForge."))
        }
    }

    private suspend fun addModrinthRecursive(query: String, versionId: String?, config: PakConfig, requestedSide: String, depth: Int): Boolean {
        val project = ModrinthApi.getProject(query) ?: run {
            if (depth != 0)
                t.println(red("! Project '$query' not found on Modrinth (dependency)."))
            return false
        }

        if (visitedProjects.contains(project.id)) return true
        visitedProjects.add(project.id)

        val detectedSide = when {
            project.client_side == "unsupported" && project.server_side != "unsupported" -> "server"
            project.server_side == "unsupported" && project.client_side != "unsupported" -> "client"
            else -> "both"
        }

        val effectiveSide = if (requestedSide == "both") detectedSide else requestedSide

        val versions = ModrinthApi.getVersions(project.slug, config.loader, config.mcVersion)
        if (versions.isEmpty()) {
            t.println(red("! No valid versions found for '") + white(project.title) + red("' on ${config.loader} ${config.mcVersion}"))
            return true
        }

        val selected = versionId?.let { v -> versions.find { it.id == v || it.version_number == v } }
            ?: versions.firstOrNull()

        if (selected == null) {
            t.println(red("! Version '$versionId' not found for '") + white(project.title) + red("'"))
            return true
        }

        val isAlreadyInstalled = shouldSkip(project.id, project.slug)

        if (!isAlreadyInstalled) {
            val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

            printModStatus(project.title, isManual = false, manualLink = null, depth = depth, side = effectiveSide)

            saveMeta(LocalModMeta(
                name = project.title, slug = project.slug, provider = "mr", side = effectiveSide,
                fileName = file.filename, hashes = file.hashes, downloadUrl = file.url,
                fileSize = file.size, projectId = project.id
            ))

            track(project.id, project.slug)
        } else {
            if (depth == 0) {
                t.println(yellow("ℹ\uFE0F  Skipped '${project.title}' (already present)"))
            }
        }

        selected.dependencies.filter { it.dependency_type == "required" }.forEach { dep ->
            dep.project_id?.let { pid ->
                addModrinthRecursive(pid, dep.version_id, config, requestedSide, depth + 1)
            }
        }
        return true
    }

    private suspend fun addCurseForgeRecursive(query: String, version: String?, config: PakConfig, side: String, key: String?, depth: Int) {
        val mod = query.toIntOrNull()?.let { CurseForgeApi.getMod(it, key) } ?: CurseForgeApi.searchMod(query, key)

        if (mod == null) {
            t.println(red("! Mod '$query' not found on CurseForge."))
            return
        }

        if (visitedProjects.contains(mod.id.toString())) return
        visitedProjects.add(mod.id.toString())

        val files = CurseForgeApi.getFiles(mod.id, config.loader, config.mcVersion, key)
        val selected = version?.let { v -> files.find { it.displayName.contains(v) || it.fileName.contains(v) } }
            ?: files.firstOrNull()

        if (selected == null) {
            if (depth == 0) t.println(red("! No compatible files found for '${mod.name}' on MC ${config.mcVersion}"))
            return
        }

        val isAlreadyInstalled = shouldSkip(mod.id.toString(), mod.slug)

        if (!isAlreadyInstalled) {
            val sha1 = selected.hashes.find { it.algo == 1 }?.value ?: ""
            val isManual = selected.downloadUrl == null
            val dUrl = selected.downloadUrl ?: ""
            val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${mod.slug}/download/${selected.id}"

            printModStatus(mod.name, isManual, manualLink, depth, side)

            if (depth == 0 && side == "both") {
                t.println(yellow("   ⚠ Side defaulted to 'both'. Check if this is a Client/Server only mod."))
            }

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
        } else {
            if (depth == 0) {
                t.println(yellow("ℹ\uFE0F  Skipped '${mod.name}' (already present)"))
            }
        }

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

    private fun printModStatus(name: String, isManual: Boolean, manualLink: String?, depth: Int, side: String? = null) {
        val indent = "   ".repeat(depth)
        val symbol = if (depth == 0) "+ " else "└─ "
        val sideInfo = if (side != null && side != "both") gray(" ($side)") else ""

        if (isManual) {
            t.println(yellow("$indent$symbol Manual Download: ") + white(name) + sideInfo)
            t.println(gray("$indent   Link: ") + blue(manualLink ?: "Unknown"))
        } else {
            t.println(green("$indent$symbol Adding: ") + white(name) + sideInfo)
        }
    }

    private fun saveMeta(meta: LocalModMeta) {
        val path = "contents/mods/${meta.slug}.json".toPath()
        fs.createDirectories("contents/mods".toPath())
        fs.write(path) { writeUtf8(jsonFormat.encodeToString(meta)) }
    }
}