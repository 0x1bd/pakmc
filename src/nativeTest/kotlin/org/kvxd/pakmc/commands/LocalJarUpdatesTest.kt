package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.main
import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.LocalModMeta
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ConfigIO
import org.kvxd.pakmc.utils.LocalJarNaming
import org.kvxd.pakmc.utils.ModIO
import org.kvxd.pakmc.utils.calculateHashes
import org.kvxd.pakmc.utils.fs
import platform.posix.chdir
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalJarUpdatesTest {
    @Test
    fun replacesVersionedJarWhilePreservingSideAndPin() = withModpack {
        write("example-mod-0.1.1-neoforge.jar", "old release")
        write("example-mod-0.1.2-neoforge.jar", "new release")

        AddCommand().main(arrayOf("example-mod-0.1.1-neoforge.jar", "--side", "client", "--pin"))
        AddCommand().main(arrayOf("example-mod-0.1.2-neoforge.jar"))

        val installed = ModIO.getAllMods().single()
        assertEquals("example-mod", installed.name)
        assertEquals("example-mod", installed.slug)
        assertEquals("example-mod.jar", installed.fileName)
        assertEquals("client", installed.side)
        assertTrue(installed.sideOverride)
        assertTrue(installed.pinned)
        assertEquals(calculateHashes("example-mod-0.1.2-neoforge.jar".toPath(), fs), installed.hashes)
        assertEquals("new release", fs.read("contents/jarmods/example-mod.jar".toPath()) { readUtf8() })
        assertEquals(listOf("example-mod.jar"), fs.list("contents/jarmods".toPath()).map { it.name })
    }

    @Test
    fun migratesPreviouslyTrackedVersionedJar() = withModpack {
        write("contents/jarmods/example-0.1.1-neoforge.jar", "old release")
        ModIO.save(LocalModMeta(
            name = "example-0.1.1-neoforge", slug = "example-0.1.1-neoforge", provider = "local",
            side = "dedicated-server", sideOverride = true, fileName = "example-0.1.1-neoforge.jar",
            hashes = emptyMap(), downloadUrl = "", fileSize = 11, projectId = "old", pinned = true
        ))
        write("example-0.1.2-neoforge.jar", "new release")

        AddCommand().main(arrayOf("example-0.1.2-neoforge.jar"))

        val installed = ModIO.getAllMods().single()
        assertEquals("example", installed.slug)
        assertEquals("dedicated-server", installed.side)
        assertTrue(installed.pinned)
        assertFalse(fs.exists("contents/mods/example-0.1.1-neoforge.json".toPath()))
        assertFalse(fs.exists("contents/jarmods/example-0.1.1-neoforge.jar".toPath()))
        assertEquals("new release", fs.read("contents/jarmods/example.jar".toPath()) { readUtf8() })
    }

    @Test
    fun replacesChangedContentsWithSameFileName() = withModpack {
        write("example.jar", "old")
        AddCommand().main(arrayOf("example.jar"))
        write("example.jar", "new")

        AddCommand().main(arrayOf("example.jar", "--side", "server"))

        assertEquals("new", fs.read("contents/jarmods/example.jar".toPath()) { readUtf8() })
        assertEquals("server", ModIO.getAllMods().single().side)
    }

    @Test
    fun refusesToOverwriteAnUntrackedDifferentJar() = withModpack {
        write("contents/jarmods/example.jar", "manual file")
        write("example-0.1.1.jar", "new")

        AddCommand().main(arrayOf("example-0.1.1.jar"))

        assertTrue(ModIO.getAllMods().isEmpty())
        assertEquals("manual file", fs.read("contents/jarmods/example.jar".toPath()) { readUtf8() })
    }

    @Test
    fun recognizesVersionSuffixesWithoutRemovingDigitsInsideNames() {
        assertEquals("example", LocalJarNaming.slug("example-v0.1.1+build.2.jar"))
        assertEquals("example", LocalJarNaming.slug("example_0.1.1_fabric.JAR"))
        assertEquals("example", LocalJarNaming.slug("example-12.jar"))
        assertEquals("3d-skin-layers", LocalJarNaming.slug("3d-skin-layers-1.2.3.jar"))
        assertEquals("example-3d", LocalJarNaming.slug("example-3d.jar"))
    }

    private fun write(path: String, contents: String) {
        val target = path.toPath()
        target.parent?.let(fs::createDirectories)
        fs.write(target) { writeUtf8(contents) }
    }

    private fun withModpack(block: () -> Unit) {
        val previousDirectory = fs.canonicalize(".".toPath())
        val root = "/tmp/pakmc-local-update-test-${Random.nextLong()}".toPath()
        fs.createDirectory(root, mustCreate = true)
        try {
            assertEquals(0, chdir(root.toString()))
            ConfigIO.save(PakConfig(name = "Local Test", mcVersion = "1.21.1", loader = "neoforge"))
            block()
        } finally {
            assertEquals(0, chdir(previousDirectory.toString()))
            fs.deleteRecursively(root)
        }
    }
}
