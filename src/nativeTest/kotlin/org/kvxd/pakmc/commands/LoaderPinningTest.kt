package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.main
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.PakConfig
import org.kvxd.pakmc.utils.ConfigIO
import org.kvxd.pakmc.utils.LoaderVersions
import org.kvxd.pakmc.utils.fs
import org.kvxd.pakmc.utils.jsonFormat
import platform.posix.chdir
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoaderPinningTest {
    private val config = PakConfig(
        name = "Loader Test", mcVersion = "1.21.1", loader = "neoforge", loaderVersion = "21.1.1"
    )

    @Test
    fun oldConfigsDefaultToAnUnpinnedUnresolvedLoader() {
        val legacy = """{"name":"Legacy","mcVersion":"1.21.1","loader":"neoforge"}"""
        val decoded = jsonFormat.decodeFromString<PakConfig>(legacy)
        assertEquals(null, decoded.loaderVersion)
        assertFalse(decoded.loaderPinned)
    }

    @Test
    fun pinsAndUnpinsLoaderWithoutChangingVersion() = withModpack {
        PinCommand().main(arrayOf("loader"))
        assertEquals(config.copy(loaderPinned = true), savedConfig())
        PinCommand().main(arrayOf("loader"))
        assertEquals(config.copy(loaderPinned = true), savedConfig())
        UnpinCommand().main(arrayOf("loader"))
        assertEquals(config, savedConfig())
    }

    @Test
    fun pinsAnExplicitVersionWithoutResolvingLatest() = withModpack {
        ConfigIO.save(config.copy(loaderVersion = null))
        PinCommand().main(arrayOf("loader", "--version", "21.1.2"))
        assertEquals(config.copy(loaderVersion = "21.1.2", loaderPinned = true), savedConfig())
        PinCommand().main(arrayOf("loader", "--version", "21.1.3"))
        assertEquals(config.copy(loaderVersion = "21.1.3", loaderPinned = true), savedConfig())
    }

    @Test
    fun pinnedLoaderSkipsBothLookupAndPrompt() = withModpack {
        runBlocking {
            val pinned = config.copy(loaderPinned = true)
            ConfigIO.save(pinned)
            val result = BuildCommand().prepareLoader(
                pinned,
                resolveLatest = { _, _ -> error("Must not query pinned loaders") },
                readAnswer = { error("Must not prompt for pinned loaders") }
            )
            assertEquals(pinned, result)
            assertEquals(pinned, savedConfig())
        }
    }

    @Test
    fun updateRequiresExplicitConfirmationAndPersistsOnlyOnAcceptance() = withModpack {
        runBlocking {
            for (answer in listOf(null, "", "n", "no", "anything", "y", " YES ")) {
                ConfigIO.save(config)
                var prompts = 0
                val result = BuildCommand().prepareLoader(
                    config,
                    resolveLatest = { loader, mc ->
                        assertEquals("neoforge", loader)
                        assertEquals("1.21.1", mc)
                        "neoforge" to "21.1.2"
                    },
                    readAnswer = { prompts++; answer }
                )
                assertEquals(1, prompts)
                val expected = if (answer?.trim()?.lowercase() in setOf("y", "yes")) {
                    config.copy(loaderVersion = "21.1.2")
                } else config
                assertEquals(expected, result)
                assertEquals(expected, savedConfig())
            }
        }
    }

    @Test
    fun resolvesAndPersistsFirstVersionWithoutAnUpdatePrompt() = withModpack {
        runBlocking {
            val result = BuildCommand().prepareLoader(
                config.copy(loaderVersion = null),
                resolveLatest = { _, _ -> "neoforge" to "21.1.2" },
                readAnswer = { error("Initial resolution is not an update") }
            )
            assertEquals("21.1.2", result.loaderVersion)
            assertEquals(result, savedConfig())
        }
    }

    @Test
    fun unchangedOrUnresolvedLatestKeepsCurrentVersionWithoutPrompting(): Unit = runBlocking {
        for (latest in listOf("21.1.1", "*", "")) {
            assertEquals(config, LoaderVersions.prepare(config, { _, _ -> "neoforge" to latest }) { _, _ ->
                error("No update to confirm")
            })
        }
        assertFailsWith<IllegalStateException> {
            LoaderVersions.prepare(config.copy(loaderVersion = null), { _, _ -> "neoforge" to "*" }) { _, _ ->
                error("No valid version to select")
            }
        }
        assertFailsWith<IllegalStateException> {
            LoaderVersions.prepare(config.copy(loaderVersion = null, loaderPinned = true)) { _, _ ->
                error("Invalid pin cannot update")
            }
        }
    }

    @Test
    fun clientAndServerBuildsUseThePinnedVersionOffline() = withModpack {
        val pinned = config.copy(loaderPinned = true)
        ConfigIO.save(pinned)
        BuildCommand().main(arrayOf("client"))
        val index = fs.read("build/client/modrinth.index.json".toPath()) { readUtf8() }
        assertTrue(index.contains("\"neoforge\": \"21.1.1\""))
        assertTrue(fs.exists("Loader Test-1.0.0.mrpack".toPath()))
        BuildCommand().main(arrayOf("server"))
        assertTrue(fs.exists("Loader Test-1.0.0-server.zip".toPath()))
        assertEquals(pinned, savedConfig())
    }

    private fun savedConfig() = jsonFormat.decodeFromString<PakConfig>(
        fs.read("pakmc.json".toPath()) { readUtf8() }
    )

    private fun withModpack(block: () -> Unit) {
        val previousDirectory = fs.canonicalize(".".toPath())
        val root = "/tmp/pakmc-loader-test-${Random.nextLong()}".toPath()
        fs.createDirectory(root, mustCreate = true)
        try {
            assertEquals(0, chdir(root.toString()))
            ConfigIO.save(config)
            block()
        } finally {
            assertEquals(0, chdir(previousDirectory.toString()))
            fs.deleteRecursively(root)
        }
    }
}
