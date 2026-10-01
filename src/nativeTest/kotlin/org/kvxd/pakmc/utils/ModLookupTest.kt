package org.kvxd.pakmc.utils

import org.kvxd.pakmc.models.LocalModMeta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ModLookupTest {

    @Test
    fun curseForgeDependencyReusesModrinthMetadata() {
        val installed = mod("mr", "mr-id", "fabric-api", "Fabric API")

        val match = listOf(installed).findInstalledMod("cf", "306612", "fabric-api", "Fabric API")

        assertNotNull(match)
        assertSame(installed, match)
        assertEquals("mr", match.provider)
        assertEquals("client", match.side)
        assertEquals(true, match.sideOverride)
        assertEquals("installed.jar", match.fileName)
        assertEquals(mapOf("sha1" to "installed-hash"), match.hashes)
    }

    @Test
    fun modrinthDependencyReusesManualCurseForgeMetadataWithDifferentSlug() {
        val installed = mod("cf_manual", "123", "cf-library", "Shared Library")

        assertSame(
            installed,
            listOf(installed).findInstalledMod("mr", "mr-id", "mr-library", "Shared Library")
        )
    }

    @Test
    fun matchesRenamedProjectByProviderAndIdBeforeSlug() {
        val installed = mod("cf_manual", "123", "old-slug", "Old Name")
        val slugCollision = mod("mr", "other-id", "new-slug", "Other Mod")

        assertSame(
            installed,
            listOf(slugCollision, installed).findInstalledMod("cf", "123", "new-slug", "New Name")
        )
    }

    @Test
    fun projectIdsAreScopedToTheirProvider() {
        val installed = mod("mr", "123", "unrelated", "Unrelated")

        assertNull(listOf(installed).findInstalledMod("cf", "123", "library", "Library"))
    }

    @Test
    fun partialNamesDoNotMatchUnrelatedMods() {
        val installed = mod("mr", "mr-id", "library-extras", "Library Extras")

        assertNull(listOf(installed).findInstalledMod("cf", "123", "library", "Library"))
    }

    @Test
    fun crossProviderNamesAndSlugsAreCaseInsensitive() {
        val installed = mod("mr", "mr-id", "library", "Shared Library")

        assertSame(installed, listOf(installed).findInstalledMod("cf", "123", "LIBRARY", "Other Name"))
        assertSame(installed, listOf(installed).findInstalledMod("cf", "123", "other-slug", "SHARED LIBRARY"))
    }

    @Test
    fun localDependencyIsPreserved() {
        val installed = mod("local", "hash", "library", "Library")

        assertSame(installed, listOf(installed).findInstalledMod("cf", "123", "library", "Library"))
    }

    @Test
    fun explicitAddsDoNotMatchAnotherProviderByNameAlone() {
        val installed = mod("mr", "mr-id", "mr-library", "Shared Library")

        assertNull(listOf(installed).findInstalledMod("cf", "123", "cf-library"))
    }

    private fun mod(provider: String, projectId: String, slug: String, name: String) = LocalModMeta(
        name = name,
        slug = slug,
        provider = provider,
        side = "client",
        sideOverride = true,
        fileName = "installed.jar",
        hashes = mapOf("sha1" to "installed-hash"),
        downloadUrl = "https://example.com/installed.jar",
        fileSize = 100,
        projectId = projectId
    )
}
