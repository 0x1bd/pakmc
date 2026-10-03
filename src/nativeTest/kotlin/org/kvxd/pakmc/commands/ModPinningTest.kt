package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.main
import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ModIO
import org.kvxd.pakmc.utils.fs
import org.kvxd.pakmc.utils.jsonFormat
import platform.posix.chdir
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModPinningTest {

    @Test
    fun existingMetadataDefaultsToUnpinned() {
        val legacy = """
            {
                "name": "Example", "slug": "example", "provider": "mr", "side": "both",
                "fileName": "example-1.jar", "hashes": {"sha1": "original-hash"},
                "downloadUrl": "https://example.com/example-1.jar", "fileSize": 100,
                "projectId": "project-id"
            }
        """.trimIndent()

        assertFalse(jsonFormat.decodeFromString<LocalModMeta>(legacy).pinned)
    }

    @Test
    fun pinsAndUnpinsMultipleModsWithoutChangingTheirVersions() = withModpack {
        val mods = listOf(mod("mr"), mod("cf_manual"))
        mods.forEach(ModIO::save)

        PinCommand().main(arrayOf("mr", "Mod cf_manual"))
        mods.forEach { original ->
            assertEquals(original.copy(pinned = true), ModIO.findLocalMod(original.slug))
        }

        // Repeating a pin must leave all version and provider metadata intact.
        PinCommand().main(arrayOf("mr", "cf_manual"))
        mods.forEach { original ->
            assertEquals(original.copy(pinned = true), ModIO.findLocalMod(original.slug))
        }

        UnpinCommand().main(arrayOf("mr", "cf_manual"))
        mods.forEach { original -> assertEquals(original, ModIO.findLocalMod(original.slug)) }
        UnpinCommand().main(arrayOf("mr", "cf_manual"))
        mods.forEach { original -> assertEquals(original, ModIO.findLocalMod(original.slug)) }
    }

    @Test
    fun missingModDoesNotPreventPinningOtherMods() = withModpack {
        val installed = mod("mr")
        ModIO.save(installed)

        PinCommand().main(arrayOf("missing", "mr"))
        assertEquals(listOf(installed.copy(pinned = true)), ModIO.getAllMods())

        UnpinCommand().main(arrayOf("missing", "mr"))
        assertEquals(listOf(installed), ModIO.getAllMods())
    }

    @Test
    fun addPinsLocalModsAndPreservesPinsWhenAddingAgain() = withModpack {
        fs.write("example.jar".toPath()) { writeUtf8("local jar contents") }

        AddCommand().main(arrayOf("example.jar", "--pin", "--side", "client"))
        val installed = requireNotNull(ModIO.findLocalMod("example"))
        assertTrue(installed.pinned)
        assertEquals("client", installed.side)
        assertEquals("local jar contents", fs.read("contents/jarmods/example.jar".toPath()) { readUtf8() })

        AddCommand().main(arrayOf("example.jar", "--side", "client"))
        assertEquals(installed, ModIO.findLocalMod("example"))

        UnpinCommand().main(arrayOf("example"))
        AddCommand().main(arrayOf("example.jar", "--pin", "--side", "client"))
        assertEquals(installed, ModIO.findLocalMod("example"))
    }

    @Test
    fun addKeepsModsUnpinnedByDefault() = withModpack {
        fs.write("example.jar".toPath()) { writeUtf8("local jar contents") }

        AddCommand().main(arrayOf("example.jar"))

        assertFalse(requireNotNull(ModIO.findLocalMod("example")).pinned)
    }

    @Test
    fun updatePreservesPinnedVersionsForEveryProviderEvenWhenUnstableVersionsAreAllowed() = withModpack {
        val mods = listOf("mr", "cf", "cf_manual", "local").map { mod(it).copy(pinned = true) }
        mods.forEach(ModIO::save)
        val before = mods.associate { meta ->
            meta.slug to fs.read("contents/mods/${meta.slug}.json".toPath()) { readUtf8() }
        }

        UpdateCommand().main(emptyArray())
        UpdateCommand().main(arrayOf("--allow-unstable"))

        mods.forEach { original ->
            assertEquals(original, ModIO.findLocalMod(original.slug))
            assertEquals(before.getValue(original.slug), fs.read("contents/mods/${original.slug}.json".toPath()) { readUtf8() })
            assertTrue(requireNotNull(ModIO.findLocalMod(original.slug)).pinned)
        }
    }

    private val config = PakConfig(name = "Pinning Test", mcVersion = "1.21.1", loader = "fabric")

    private fun mod(provider: String) = LocalModMeta(
        name = "Mod $provider",
        slug = provider,
        provider = provider,
        side = "client",
        sideOverride = true,
        fileName = "$provider-1.jar",
        hashes = mapOf("sha1" to "original-hash"),
        downloadUrl = "https://example.com/$provider-1.jar",
        fileSize = 100,
        projectId = "project-id",
        manualLink = if (provider == "cf_manual") "https://example.com/manual" else null
    )

    private fun withModpack(block: () -> Unit) {
        val previousDirectory = fs.canonicalize(".".toPath())
        val root = "/tmp/pakmc-pinning-test-${Random.nextLong()}".toPath()
        fs.createDirectory(root, mustCreate = true)
        try {
            fs.write(root / "pakmc.json") { writeUtf8(jsonFormat.encodeToString(config)) }
            assertEquals(0, chdir(root.toString()))
            block()
        } finally {
            assertEquals(0, chdir(previousDirectory.toString()))
            fs.deleteRecursively(root)
        }
    }
}
