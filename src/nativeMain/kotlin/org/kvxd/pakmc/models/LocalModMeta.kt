package org.kvxd.pakmc.models

import kotlinx.serialization.Serializable

@Serializable
data class LocalModMeta(
    val name: String,
    val slug: String,
    val provider: String,
    val side: String,
    val sideOverride: Boolean = false,
    val fileName: String,
    val hashes: Map<String, String>,
    val downloadUrl: String,
    val fileSize: Long,
    val projectId: String,
    val manualLink: String? = null,
    val pinned: Boolean = false
)
