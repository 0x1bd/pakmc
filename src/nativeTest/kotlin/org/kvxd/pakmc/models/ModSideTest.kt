package org.kvxd.pakmc.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModSideTest {
    @Test
    fun serverSideModsAreIncludedInClientPacksForSingleplayer() {
        assertEquals(
            MrPackEnv(client = "required", server = "required"),
            ModSide.toMrPackEnvironment(ModSide.SERVER)
        )
    }

    @Test
    fun dedicatedServerModsAreExcludedFromClientPacks() {
        assertEquals(
            MrPackEnv(client = "unsupported", server = "required"),
            ModSide.toMrPackEnvironment(ModSide.DEDICATED_SERVER)
        )
    }

    @Test
    fun mapsNewModrinthEnvironmentsToPhysicalPackSides() {
        val project = MrProject(
            id = "id",
            slug = "slug",
            title = "Title",
            client_side = "unsupported",
            server_side = "required"
        )

        assertEquals(ModSide.SERVER, ModSide.fromModrinth("server_only", project))
        assertEquals(ModSide.DEDICATED_SERVER, ModSide.fromModrinth("dedicated_server_only", project))
        assertEquals(ModSide.CLIENT, ModSide.fromModrinth("singleplayer_only", project))
        assertEquals(ModSide.BOTH, ModSide.fromModrinth("client_and_server", project))
        assertEquals(ModSide.BOTH, ModSide.fromModrinthEnvironment("client_and_server"))
        assertEquals(null, ModSide.fromModrinthEnvironment("unknown"))
    }

    @Test
    fun normalizesCommandAliasesAndFiltersServerBuilds() {
        assertEquals(ModSide.BOTH, ModSide.normalize("b"))
        assertEquals(ModSide.CLIENT, ModSide.normalize("c"))
        assertEquals(ModSide.SERVER, ModSide.normalize("s"))
        assertEquals(ModSide.DEDICATED_SERVER, ModSide.normalize("d"))
        assertFalse(ModSide.isIncludedOnServer(ModSide.CLIENT))
        assertTrue(ModSide.isIncludedOnServer(ModSide.SERVER))
        assertTrue(ModSide.isIncludedOnServer(ModSide.DEDICATED_SERVER))
    }

    @Test
    fun filtersPhysicalClientPackContents() {
        assertTrue(ModSide.isIncludedOnClient(ModSide.CLIENT))
        assertTrue(ModSide.isIncludedOnClient(ModSide.SERVER))
        assertTrue(ModSide.isIncludedOnClient(ModSide.BOTH))
        assertFalse(ModSide.isIncludedOnClient(ModSide.DEDICATED_SERVER))
    }
}