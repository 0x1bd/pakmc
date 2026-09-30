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
    }

    @Test
    fun normalizesCommandAliasesAndFiltersServerBuilds() {
        assertEquals(ModSide.CLIENT, ModSide.normalize("c"))
        assertEquals(ModSide.SERVER, ModSide.normalize("s"))
        assertEquals(ModSide.DEDICATED_SERVER, ModSide.normalize("d"))
        assertFalse(ModSide.isIncludedOnServer(ModSide.CLIENT))
        assertTrue(ModSide.isIncludedOnServer(ModSide.SERVER))
        assertTrue(ModSide.isIncludedOnServer(ModSide.DEDICATED_SERVER))
    }
}