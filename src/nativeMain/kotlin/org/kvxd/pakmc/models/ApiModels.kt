package org.kvxd.pakmc.models

import kotlinx.serialization.Serializable

@Serializable
data class MrProject(
    val id: String,
    val slug: String,
    val title: String,
    val client_side: String,
    val server_side: String
)

@Serializable
data class MrVersion(
    val id: String,
    val project_id: String,
    val version_number: String,
    val version_type: String,
    val date_published: String,
    val files: List<MrFile>,
    val dependencies: List<MrDependency> = emptyList()
)

@Serializable
data class MrDependency(
    val project_id: String? = null,
    val version_id: String? = null,
    val dependency_type: String
)

@Serializable
data class MrFile(
    val hashes: Map<String, String>,
    val url: String,
    val filename: String,
    val size: Long
)

@Serializable
data class CfSearchResponse(val data: List<CfMod>)

@Serializable
data class CfMod(
    val id: Int,
    val name: String,
    val slug: String,
    val links: Map<String, String?>
)

@Serializable
data class CfFilesResponse(val data: List<CfFile>)

@Serializable
data class CfFile(
    val id: Int,
    val displayName: String,
    val fileName: String,
    val fileLength: Long,
    val downloadUrl: String? = null,
    val releaseType: Int,
    val fileDate: String,
    val hashes: List<CfHash>,
    val gameVersions: List<String>,
    val dependencies: List<CfFileDependency> = emptyList()
)

@Serializable
data class CfFileDependency(
    val modId: Int,
    val relationType: Int
)

@Serializable
data class CfHash(val value: String, val algo: Int)

@Serializable
data class MrPackIndex(
    val formatVersion: Int = 1,
    val game: String = "minecraft",
    val versionId: String,
    val name: String,
    val dependencies: Map<String, String>,
    val files: List<MrPackFile>
)

@Serializable
data class MrPackFile(
    val path: String,
    val hashes: Map<String, String>,
    val env: MrPackEnv? = null,
    val downloads: List<String>
)

@Serializable
data class MrPackEnv(
    val client: String,
    val server: String
)