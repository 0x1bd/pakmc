package org.kvxd.pakmc.utils

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.terminal.Terminal
import org.kvxd.pakmc.models.CfFile
import org.kvxd.pakmc.models.MrVersion

data class VersionCandidate<T>(
    val original: T,
    val displayName: String,
    val type: String, // release, beta, alpha
    val date: String,
    val isCurrent: Boolean = false
)

object VersionSelector {
    private val t = Terminal()

    fun <T> prompt(candidates: List<VersionCandidate<T>>, title: String): T? {
        if (candidates.isEmpty()) return null

        t.println(cyan("Versions for $title:"))
        val limit = minOf(candidates.size, 15)

        candidates.take(limit).forEachIndexed { index, v ->
            val marker = if (v.isCurrent) green("*") else " "
            val typeColor = if (v.type.equals("release", true)) green else yellow
            val typeLabel = v.type.take(1).uppercase()

            t.println("$marker ${index + 1}. ${typeColor(typeLabel)} ${white(v.displayName)} ${gray("(${v.date.take(10)})")}")
        }

        t.print("Select version (1-$limit): ")
        val input = readlnOrNull()?.toIntOrNull()

        if (input != null && input in 1..limit) {
            return candidates[input - 1].original
        }

        t.println(yellow("Selection cancelled."))
        return null
    }

    fun fromModrinth(versions: List<MrVersion>, currentFilename: String? = null): List<VersionCandidate<MrVersion>> {
        return versions.map { v ->
            VersionCandidate(
                original = v,
                displayName = v.version_number,
                type = v.version_type,
                date = v.date_published,
                isCurrent = if (currentFilename != null) v.files.any { it.filename == currentFilename } else false
            )
        }
    }

    fun fromCurseForge(files: List<CfFile>, currentFilename: String? = null): List<VersionCandidate<CfFile>> {
        return files.map { f ->
            val typeStr = when (f.releaseType) { 1 -> "release"; 2 -> "beta"; else -> "alpha" }
            VersionCandidate(
                original = f,
                displayName = f.displayName,
                type = typeStr,
                date = f.fileDate,
                isCurrent = f.fileName == currentFilename
            )
        }
    }
}