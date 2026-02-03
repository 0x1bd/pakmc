package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.*
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import org.kvxd.pakmc.utils.VersionSelector

class AddCommand : PakCommand(name = "add", help = "Add mod(s) to the pack") {

    private val queries by argument(help = "Mod slug(s), ID(s), URL(s), or name(s)").multiple(required = true)

    private val version by option("-v", "--version", help = "Specific version ID")
    private val selectVersion by option(
        "-sv",
        "--select-version",
        help = "Interactively select a version"
    ).flag(default = false)

    private val provider by option("--provider", help = "Default mod provider").choice(
        "mr",
        "modrinth",
        "cf",
        "curseforge"
    ).default("mr")
    private val side by option("--side", help = "Side restriction").choice("c", "client", "s", "server").default("both")
    private val allowUnstable by option("--allow-unstable", help = "Allow Beta/Alpha versions").flag(default = false)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    private val visitedProjects = mutableSetOf<String>()

    override suspend fun execute(config: PakConfig) {
        visitedProjects.clear()

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
        Regex("modrinth\\.com/.*?/([^/?#]+)")
            .find(input)?.let { return it.groupValues[1] to "mr" }

        Regex("curseforge\\.com/minecraft/mc-mods/([^/?#]+)")
            .find(input)?.let { return it.groupValues[1] to "cf" }

        return input to defaultProvider
    }

    private suspend fun fallbackToCurseForge(
        query: String,
        version: String?,
        config: PakConfig,
        side: String,
        key: String?
    ) {
        val mod = CurseForgeApi.searchMod(query, key)

        if (mod != null) {
            t.println(yellow("? Project '$query' not found on Modrinth."))
            t.print(white("  Found ") + cyan(mod.name) + white(" on CurseForge. Add? [Y/n] "))

            val input = readlnOrNull()?.trim()?.lowercase() ?: ""
            if (input.isEmpty() || input == "y" || input == "yes") {
                addCurseForgeRecursive(mod.id.toString(), version, config, side, key, depth = 0)
            } else {
                t.println(yellow("ℹ\uFE0F  Skipped fallback."))
            }
        } else {
            t.println(red("! '$query' not found on Modrinth or CurseForge."))
        }
    }

    private suspend fun addModrinthRecursive(
        query: String,
        versionId: String?,
        config: PakConfig,
        requestedSide: String,
        depth: Int
    ): Boolean {
        val project = ModrinthApi.getProject(query) ?: run {
            if (depth > 0) t.println(red("! Project '$query' not found on Modrinth (dependency)."))
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

        val allVersions = ModrinthApi.getVersions(project.slug, config.loader, config.mcVersion)
        val compatibleVersions = if (allowUnstable) allVersions else allVersions.filter { it.version_type == "release" }

        if (compatibleVersions.isEmpty()) {
            if (depth == 0) t.println(red("! No compatible versions found for '") + white(project.title) + red("'"))
            return true
        }

        val selected = if (depth == 0 && selectVersion) {
            val candidates = VersionSelector.fromModrinth(compatibleVersions)
            VersionSelector.prompt(candidates, project.title)
        } else {
            versionId?.let { v -> compatibleVersions.find { it.id == v || it.version_number == v } }
                ?: compatibleVersions.firstOrNull()
        }

        if (selected == null) {
            if (!selectVersion) t.println(red("! Version not found or selection cancelled."))
            return true
        }

        val currentMod = ModIO.findLocalMod(project.slug)
        val isAlreadyInstalled = currentMod != null && currentMod.projectId == project.id

        if (!isAlreadyInstalled) {
            val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

            printModStatus(project.title, isManual = false, manualLink = null, depth = depth, side = effectiveSide)

            ModIO.save(
                LocalModMeta(
                    name = project.title, slug = project.slug, provider = "mr", side = effectiveSide,
                    fileName = file.filename, hashes = file.hashes, downloadUrl = file.url,
                    fileSize = file.size, projectId = project.id
                )
            )
        } else {
            if (depth == 0) t.println(yellow("ℹ\uFE0F  Skipped '${project.title}' (already present)"))
        }

        selected.dependencies.filter { it.dependency_type == "required" }.forEach { dep ->
            dep.project_id?.let { pid -> addModrinthRecursive(pid, dep.version_id, config, effectiveSide, depth + 1) }
        }

        return true
    }

    private suspend fun addCurseForgeRecursive(
        query: String,
        version: String?,
        config: PakConfig,
        side: String,
        key: String?,
        depth: Int
    ) {
        val mod = query.toIntOrNull()?.let { CurseForgeApi.getMod(it, key) } ?: CurseForgeApi.searchMod(query, key)

        if (mod == null) {
            t.println(red("! Mod '$query' not found on CurseForge."))
            return
        }

        if (visitedProjects.contains(mod.id.toString())) return
        visitedProjects.add(mod.id.toString())

        val allFiles = CurseForgeApi.getFiles(mod.id, config.loader, config.mcVersion, key)
        val compatibleFiles = if (allowUnstable) allFiles else allFiles.filter { it.releaseType == 1 }

        if (compatibleFiles.isEmpty()) {
            if (depth == 0) t.println(red("! No compatible files found for '${mod.name}'"))
            return
        }

        val selected = if (depth == 0 && selectVersion) {
            val candidates = VersionSelector.fromCurseForge(compatibleFiles)
            VersionSelector.prompt(candidates, mod.name)
        } else {
            version?.let { v -> compatibleFiles.find { it.displayName.contains(v) || it.fileName.contains(v) } }
                ?: compatibleFiles.firstOrNull()
        }

        if (selected == null) return

        val currentMod = ModIO.findLocalMod(mod.slug)
        val isAlreadyInstalled = currentMod != null && currentMod.projectId == mod.id.toString()

        if (!isAlreadyInstalled) {
            val sha1 = selected.hashes.find { it.algo == 1 }?.value ?: ""
            val isManual = selected.downloadUrl == null
            val dUrl = selected.downloadUrl ?: ""
            val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${mod.slug}/download/${selected.id}"

            printModStatus(mod.name, isManual, manualLink, depth, side)
            if (depth == 0 && side == "both") t.println(yellow("   ⚠ Side defaulted to 'both'."))

            ModIO.save(
                LocalModMeta(
                    name = mod.name, slug = mod.slug, provider = if (isManual) "cf_manual" else "cf",
                    side = side, fileName = selected.fileName, hashes = mapOf("sha1" to sha1),
                    downloadUrl = dUrl, fileSize = selected.fileLength, projectId = mod.id.toString(),
                    manualLink = if (isManual) manualLink else null
                )
            )
        } else {
            if (depth == 0) t.println(yellow("ℹ\uFE0F  Skipped '${mod.name}' (already present)"))
        }

        selected.dependencies.filter { it.relationType == 3 }.forEach { dep ->
            addCurseForgeRecursive(dep.modId.toString(), null, config, side, key, depth + 1)
        }
    }

    private fun printModStatus(name: String, isManual: Boolean, manualLink: String?, depth: Int, side: String? = null) {
        val indent = "   ".repeat(depth)
        val symbol = if (depth == 0) "+ " else "└─ "
        val sideInfo = if (side != null && side != "both") gray(" ($side)") else ""

        if (isManual) {
            t.println(yellow("$indent$symbol Manual: ") + white(name) + sideInfo)
            t.println(gray("$indent   Link: ") + blue(manualLink ?: "Unknown"))
        } else {
            t.println(green("$indent$symbol Adding: ") + white(name) + sideInfo)
        }
    }
}