package org.kvxd.pakmc.commands

import okio.Path
import okio.Path.Companion.toPath
import org.kvxd.pakmc.utils.fs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BuildOverridesTest {

    @Test
    fun preservesInstancePathsForClientAndServerBuilds() = withTempDirectory { root ->
        val contents = root / "contents"
        val files = listOf(
            "config/example.toml",
            "defaultconfigs/example.toml",
            "kubejs/server_scripts/example.js",
            "options.txt"
        )
        files.forEach { write(contents / "overrides" / it, it) }

        val destinations = listOf(root / "client/overrides", root / "server")
        destinations.forEach { destination ->
            BuildCommand().copyOverrides(contents, destination)
            files.forEach { path ->
                assertEquals(path, fs.read(destination / path) { readUtf8() })
            }
            assertFalse(fs.exists(destination / "overrides"))
        }
    }

    @Test
    fun overridesReplaceGeneratedFilesAndLegacyConfigs() = withTempDirectory { root ->
        val contents = root / "contents"
        val destination = root / "server"
        write(destination / "mods/example.jar", "generated")
        write(contents / "configs/example.toml", "legacy")
        write(contents / "configs/legacy-only.toml", "legacy-only")
        write(contents / "overrides/config/example.toml", "override")
        write(contents / "overrides/mods/example.jar", "replacement")

        BuildCommand().copyOverrides(contents, destination)

        assertEquals("override", fs.read(destination / "config/example.toml") { readUtf8() })
        assertEquals("replacement", fs.read(destination / "mods/example.jar") { readUtf8() })
        assertEquals("legacy-only", fs.read(destination / "config/legacy-only.toml") { readUtf8() })
        assertEquals("legacy", fs.read(contents / "configs/example.toml") { readUtf8() })
    }

    @Test
    fun missingOverridesLeaveGeneratedFilesIntact() = withTempDirectory { root ->
        val destination = root / "server"
        write(destination / "mods/example.jar", "generated")

        BuildCommand().copyOverrides(root / "contents", destination)

        assertEquals("generated", fs.read(destination / "mods/example.jar") { readUtf8() })
        assertFalse(fs.exists(destination / "config"))
    }

    private fun write(path: Path, value: String) {
        fs.createDirectories(requireNotNull(path.parent))
        fs.write(path) { writeUtf8(value) }
    }

    private fun withTempDirectory(block: (Path) -> Unit) {
        val root = "/tmp/pakmc-overrides-test-${Random.nextLong()}".toPath()
        fs.createDirectory(root, mustCreate = true)
        try {
            block(root)
        } finally {
            fs.deleteRecursively(root)
        }
    }
}
