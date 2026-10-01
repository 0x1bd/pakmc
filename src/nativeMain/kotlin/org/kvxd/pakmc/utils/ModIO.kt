package org.kvxd.pakmc.utils

import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.LocalModMeta

object ModIO {
    private val MODS_DIR = "contents/mods".toPath()

    fun exists(): Boolean = fs.exists(MODS_DIR)

    fun save(meta: LocalModMeta) {
        val path = MODS_DIR.div("${meta.slug}.json")
        fs.createDirectories(MODS_DIR)
        fs.write(path) { writeUtf8(jsonFormat.encodeToString(meta)) }
    }

    fun tryRemove(slug: String): Boolean {
        val path = MODS_DIR.div("${slug}.json")

        if (!fs.exists(path)) {
            return false
        }

        fs.delete(path)
        return !fs.exists(path)
    }

    fun getAllMods(): List<LocalModMeta> {
        if (!fs.exists(MODS_DIR)) return emptyList()

        return fs.list(MODS_DIR)
            .filter { it.name.endsWith(".json") }
            .mapNotNull { tryParse(it) }
    }

    fun findLocalMod(query: String): LocalModMeta? {
        if (!fs.exists(MODS_DIR)) return null

        val exactPath = MODS_DIR.div("$query.json")
        if (fs.exists(exactPath)) {
            return tryParse(exactPath)
        }

        return fs.list(MODS_DIR).firstNotNullOfOrNull { path ->
            if (!path.name.endsWith(".json")) return@firstNotNullOfOrNull null
            val meta = tryParse(path) ?: return@firstNotNullOfOrNull null

            if (meta.slug.equals(query, ignoreCase = true) ||
                meta.name.contains(query, ignoreCase = true)
            ) {
                meta
            } else {
                null
            }
        }
    }

    private fun tryParse(path: okio.Path): LocalModMeta? {
        return try {
            jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })
        } catch (_: Exception) {
            null
        }
    }
}

internal fun List<LocalModMeta>.findInstalledMod(
    provider: String,
    projectId: String,
    slug: String,
    name: String? = null
): LocalModMeta? {
    fun sameProvider(mod: LocalModMeta): Boolean =
        mod.provider.removeSuffix("_manual") == provider.removeSuffix("_manual")

    return find { sameProvider(it) && it.projectId == projectId }
        ?: find { it.slug.equals(slug, ignoreCase = true) }
        ?: name?.let { title -> find { !sameProvider(it) && it.name.equals(title, ignoreCase = true) } }
}
