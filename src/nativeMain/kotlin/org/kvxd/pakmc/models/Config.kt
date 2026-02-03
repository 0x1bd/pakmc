package org.kvxd.pakmc.models

import kotlinx.serialization.Serializable

@Serializable
data class PakConfig(
    val name: String,
    val mcVersion: String,
    val loader: String,
    val version: String = "1.0.0",
    val author: String = "Unknown",
    val curseForgeApiKey: String? = null
)

@Serializable
data class LocalModMeta(
    val name: String,
    val slug: String,
    val provider: String,
    val side: String,
    val fileName: String,
    val hashes: Map<String, String>,
    val downloadUrl: String,
    val fileSize: Long,
    val projectId: String,
    val manualLink: String? = null
)