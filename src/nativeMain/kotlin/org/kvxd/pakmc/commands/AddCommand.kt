package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.*
import okio.Path.Companion.toPath
import org.kvxd.pakmc.api.CurseForgeApi
import org.kvxd.pakmc.api.ModrinthApi
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.ModSide
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import org.kvxd.pakmc.utils.VersionSelector
import org.kvxd.pakmc.utils.calculateHashes
import org.kvxd.pakmc.utils.findInstalledMod
import org.kvxd.pakmc.utils.fs

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
        "curseforge",
        "local"
    ).default("mr")
    private val side by option(
        "--side",
        help = "Install side (both, client, server, or dedicated-server)"
    ).choice(
        "b",
        "both",
        "c",
        "client",
        "s",
        "server",
        "d",
        "dedicated-server"
    )
    private val allowUnstable by option(
        "--allow-unstable",
        help = "Allow Beta/Alpha versions without confirmation"
    ).flag(default = false)
    private val apiKey by option("--api-key", help = "CurseForge API Key")

    private val visitedProjects = mutableSetOf<String>()

    override suspend fun execute(config: PakConfig) {
        visitedProjects.clear()

        val defaultNormProvider = when (provider) {
            "cf", "curseforge" -> "cf"
            "local" -> "local"
            else -> "mr"
        }
        val cfKey = apiKey ?: config.curseForgeApiKey
        val normalizedSide = side?.let(ModSide::normalize)

        queries.forEach { rawQuery ->
            if (defaultNormProvider == "local" || rawQuery.endsWith(".jar", ignoreCase = true)) {
                addLocalJar(rawQuery, normalizedSide)
                return@forEach
            }

            val (query, detectedProvider) = resolveIdentity(rawQuery, defaultNormProvider)

            if (detectedProvider == "mr") {
                val foundOnMr = addModrinthRecursive(query, version, config, normalizedSide, depth = 0)
                val isDirectUrl = rawQuery.contains("modrinth.com")

                if (!foundOnMr && !isDirectUrl) {
                    fallbackToCurseForge(query, version, config, normalizedSide, cfKey)
                }
            } else {
                addCurseForgeRecursive(query, version, config, normalizedSide, cfKey, depth = 0)
            }
        }
    }

    private fun addLocalJar(rawPath: String, requestedSide: String?) {
        val source = rawPath.toPath()
        if (!rawPath.endsWith(".jar", ignoreCase = true)) {
            terminal.println(red("! Local mod must be a .jar file: $rawPath"))
            return
        }
        if (!fs.exists(source) || !fs.metadata(source).isRegularFile) {
            terminal.println(red("! Local mod not found: $rawPath"))
            return
        }

        val fileName = source.name
        val displayName = fileName.dropLast(4)
        val slug = displayName
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
        if (slug.isBlank()) {
            terminal.println(red("! Could not derive a mod name from: $fileName"))
            return
        }

        val existing = ModIO.getAllMods().find { it.slug.equals(slug, ignoreCase = true) }
        if (existing != null && existing.provider != "local") {
            terminal.println(red("! '$slug' is already tracked from ${existing.provider}."))
            return
        }

        val hashes = calculateHashes(source, fs)
        val destination = "contents/jarmods/$fileName".toPath()
        if (fs.exists(destination)) {
            val destinationHashes = calculateHashes(destination, fs)
            if (destinationHashes != hashes) {
                terminal.println(red("! A different file already exists at $destination"))
                return
            }
        } else {
            fs.createDirectories(requireNotNull(destination.parent))
            fs.copy(source, destination)
        }

        val side = requestedSide ?: ModSide.BOTH
        ModIO.save(
            LocalModMeta(
                name = displayName,
                slug = slug,
                provider = "local",
                side = side,
                sideOverride = requestedSide != null,
                fileName = fileName,
                hashes = hashes,
                downloadUrl = "",
                fileSize = fs.metadata(destination).size ?: 0L,
                projectId = hashes.getValue("sha1")
            )
        )

        val verb = if (existing == null) "Adding" else "Updated"
        terminal.println(green("+ $verb local mod: ") + white(fileName) + gray(" ($side)"))
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
        side: String?,
        key: String?
    ) {
        val mod = CurseForgeApi.searchMod(query, key)

        if (mod != null) {
            terminal.println(yellow("? Project '$query' not found on Modrinth."))
            terminal.print(white("  Found ") + cyan(mod.name) + white(" on CurseForge. Add? [Y/n] "))

            val input = readlnOrNull()?.trim()?.lowercase() ?: ""
            if (input.isEmpty() || input == "y" || input == "yes") {
                addCurseForgeRecursive(mod.id.toString(), version, config, side, key, depth = 0)
            } else {
                terminal.println(yellow("ℹ\uFE0F  Skipped fallback."))
            }
        } else {
            terminal.println(red("! '$query' not found on Modrinth or CurseForge."))
        }
    }

    private suspend fun addModrinthRecursive(
        query: String,
        versionId: String?,
        config: PakConfig,
        requestedSide: String?,
        depth: Int
    ): Boolean {
        val project = ModrinthApi.getProject(query) ?: run {
            if (depth > 0) terminal.println(red("! Project '$query' not found on Modrinth (dependency)."))
            return false
        }

        if (!visitedProjects.add("mr:${project.id}")) return true

        val currentMod = ModIO.getAllMods().findInstalledMod(
            "mr", project.id, project.slug, if (depth > 0) project.title else null
        )
        // Dependencies must not replace an installed mod or change its side or provider
        if (depth > 0 && currentMod != null) return true

        val allVersions = ModrinthApi.getVersions(project.slug, config.loader, config.mcVersion)

        if (allVersions.isEmpty()) {
            if (depth == 0) terminal.println(red("! No compatible versions found for '") + white(project.title) + red("'"))
            return true
        }

        val candidates = VersionSelector.fromModrinth(allVersions)
        val choices = if (depth == 0 && selectVersion) {
            val choice = VersionSelector.prompt(candidates, project.title) ?: return true
            candidates.filter { it.original == choice }
        } else {
            versionId?.let { v -> candidates.filter { it.original.id == v || it.original.version_number == v } }
                ?: candidates
        }
        val isAlreadyInstalled = currentMod != null && currentMod.provider == "mr" &&
            currentMod.projectId == project.id
        val selected = VersionSelector.selectForAdd(choices, project.title, allowUnstable || isAlreadyInstalled)

        if (selected == null) {
            terminal.println(yellow("ℹ\uFE0F  Skipped '${project.title}' (no version selected)."))
            return true
        }

        val detectedSide = ModSide.fromModrinth(selected.environment, project)
        val hasSideOverride = depth == 0 && requestedSide != null
        val effectiveSide = if (hasSideOverride) requireNotNull(requestedSide) else detectedSide

        if (!isAlreadyInstalled) {
            val file = selected.files.find { it.filename.endsWith(".jar") } ?: selected.files.first()

            printModStatus(project.title, isManual = false, manualLink = null, depth = depth, side = effectiveSide)

            ModIO.save(
                LocalModMeta(
                    name = project.title, slug = project.slug, provider = "mr", side = effectiveSide,
                    sideOverride = hasSideOverride,
                    fileName = file.filename, hashes = file.hashes, downloadUrl = file.url,
                    fileSize = file.size, projectId = project.id
                )
            )
        } else {
            val updatedSide = if (currentMod.sideOverride && !hasSideOverride) currentMod.side else effectiveSide
            val updatedOverride = currentMod.sideOverride || hasSideOverride
            if (updatedSide != currentMod.side || updatedOverride != currentMod.sideOverride) {
                ModIO.save(currentMod.copy(side = updatedSide, sideOverride = updatedOverride))
                terminal.println(green("↔ Updated side: ") + white(project.title) + gray(" -> $updatedSide"))
            } else if (depth == 0) {
                terminal.println(yellow("ℹ\uFE0F  Skipped '${project.title}' (already present)"))
            }
        }

        selected.dependencies.filter { it.dependency_type == "required" }.forEach { dep ->
            dep.project_id?.let { pid -> addModrinthRecursive(pid, dep.version_id, config, null, depth + 1) }
        }

        return true
    }

    private suspend fun addCurseForgeRecursive(
        query: String,
        version: String?,
        config: PakConfig,
        requestedSide: String?,
        key: String?,
        depth: Int
    ) {
        val mod = query.toIntOrNull()?.let { CurseForgeApi.getMod(it, key) } ?: CurseForgeApi.searchMod(query, key)

        if (mod == null) {
            terminal.println(red("! Mod '$query' not found on CurseForge."))
            return
        }

        if (!visitedProjects.add("cf:${mod.id}")) return

        val currentMod = ModIO.getAllMods().findInstalledMod(
            "cf", mod.id.toString(), mod.slug, if (depth > 0) mod.name else null
        )
        if (depth > 0 && currentMod != null) return

        val allFiles = CurseForgeApi.getFiles(mod.id, config.loader, config.mcVersion, key)

        if (allFiles.isEmpty()) {
            if (depth == 0) terminal.println(red("! No compatible files found for '${mod.name}'"))
            return
        }

        val candidates = VersionSelector.fromCurseForge(allFiles)
        val choices = if (depth == 0 && selectVersion) {
            val choice = VersionSelector.prompt(candidates, mod.name) ?: return
            candidates.filter { it.original == choice }
        } else {
            version?.let { v -> candidates.filter {
                it.original.id.toString() == v || it.original.displayName.contains(v) || it.original.fileName.contains(v)
            } } ?: candidates
        }
        val isAlreadyInstalled = currentMod != null && currentMod.provider in setOf("cf", "cf_manual") &&
            currentMod.projectId == mod.id.toString()
        val selected = VersionSelector.selectForAdd(choices, mod.name, allowUnstable || isAlreadyInstalled)

        if (selected == null) {
            terminal.println(yellow("ℹ\uFE0F  Skipped '${mod.name}' (no version selected)."))
            return
        }

        val side = requestedSide ?: ModSide.BOTH

        if (!isAlreadyInstalled) {
            val sha1 = selected.hashes.find { it.algo == 1 }?.value ?: ""
            val isManual = selected.downloadUrl == null
            val dUrl = selected.downloadUrl ?: ""
            val manualLink = "https://www.curseforge.com/minecraft/mc-mods/${mod.slug}/download/${selected.id}"

            printModStatus(mod.name, isManual, manualLink, depth, side)
            if (depth == 0 && side == "both") terminal.println(yellow("   ⚠ Side defaulted to 'both'."))

            ModIO.save(
                LocalModMeta(
                    name = mod.name, slug = mod.slug, provider = if (isManual) "cf_manual" else "cf",
                    side = side, sideOverride = requestedSide != null,
                    fileName = selected.fileName, hashes = mapOf("sha1" to sha1),
                    downloadUrl = dUrl, fileSize = selected.fileLength, projectId = mod.id.toString(),
                    manualLink = if (isManual) manualLink else null
                )
            )
        } else {
            if (depth == 0) terminal.println(yellow("ℹ\uFE0F  Skipped '${mod.name}' (already present)"))
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
            terminal.println(yellow("$indent$symbol Manual: ") + white(name) + sideInfo)
            terminal.println(gray("$indent   Link: ") + blue(manualLink ?: "Unknown"))
        } else {
            terminal.println(green("$indent$symbol Adding: ") + white(name) + sideInfo)
        }
    }
}