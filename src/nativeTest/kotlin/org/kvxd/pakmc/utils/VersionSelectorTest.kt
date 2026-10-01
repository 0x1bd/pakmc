package org.kvxd.pakmc.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionSelectorTest {

    private val stable = candidate("stable", "release", "2026-09-01T12:00:00Z")
    private val beta = candidate("beta", "beta", "2026-09-02T12:00:00Z")
    private val alpha = candidate("alpha", "alpha", "2026-09-03T12:00:00Z")

    @Test
    fun acceptingInstallsTheNewestUnstableVersion() {
        val selected = VersionSelector.selectForAdd(listOf(stable, alpha, beta), "Mod", false) {
            assertEquals(alpha, it)
            true
        }

        assertEquals("alpha", selected)
    }

    @Test
    fun decliningUsesTheNewestStableVersion() {
        val olderStable = candidate("old", "release", "2026-08-01T12:00:00Z")
        var prompts = 0

        val selected = VersionSelector.selectForAdd(listOf(olderStable, beta, stable, alpha), "Mod", false) {
            prompts++
            false
        }

        assertEquals("stable", selected)
        assertEquals(1, prompts)
    }

    @Test
    fun decliningWithoutAStableReleaseSkipsTheMod() {
        assertNull(VersionSelector.selectForAdd(listOf(beta, alpha), "Mod", false) { false })
    }

    @Test
    fun allowUnstableBypassesConfirmation() {
        assertEquals(
            "beta",
            VersionSelector.selectForAdd(listOf(stable, beta), "Mod", true) {
                error("--allow-unstable must not prompt")
            }
        )
    }

    @Test
    fun olderUnstableVersionsDoNotPromptWhenTheNewestVersionIsStable() {
        val latestStable = candidate("new-stable", "release", "2026-09-04T12:00:00Z")

        assertEquals(
            "new-stable",
            VersionSelector.selectForAdd(listOf(beta, latestStable, alpha), "Mod", false) {
                error("A stable latest version must not prompt")
            }
        )
    }

    @Test
    fun anExplicitUnstableSelectionRequiresConfirmation() {
        assertEquals("beta", VersionSelector.selectForAdd(listOf(beta), "Mod", false) { true })
        assertNull(VersionSelector.selectForAdd(listOf(beta), "Mod", false) { false })
    }

    @Test
    fun missingVersionsDoNotPromptOrSelectAnotherVersion() {
        assertNull(VersionSelector.selectForAdd(emptyList<VersionCandidate<String>>(), "Mod", false) {
            error("Missing versions must not prompt")
        })
    }

    private fun candidate(name: String, type: String, date: String) = VersionCandidate(
        original = name,
        displayName = name,
        type = type,
        date = date
    )
}