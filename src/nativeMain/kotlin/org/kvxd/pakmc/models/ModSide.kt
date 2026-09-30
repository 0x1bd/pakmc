package org.kvxd.pakmc.models

object ModSide {
    const val BOTH = "both"
    const val CLIENT = "client"
    const val SERVER = "server"
    const val DEDICATED_SERVER = "dedicated-server"

    fun normalize(side: String): String = when (side) {
        "b" -> BOTH
        "c" -> CLIENT
        "s" -> SERVER
        "d" -> DEDICATED_SERVER
        else -> side
    }

    fun fromModrinth(versionEnvironment: String?, project: MrProject): String =
        fromModrinthEnvironment(versionEnvironment)
            ?: project.environment.firstNotNullOfOrNull(::fromModrinthEnvironment)
            ?: fromLegacyModrinth(project.client_side, project.server_side)

    fun fromModrinthEnvironment(environment: String?): String? = when (environment) {
        "client_only", "singleplayer_only" -> CLIENT
        "server_only" -> SERVER
        "dedicated_server_only" -> DEDICATED_SERVER
        "client_and_server",
        "client_only_server_optional",
        "server_only_client_optional",
        "client_or_server",
        "client_or_server_prefers_both" -> BOTH
        else -> null
    }

    fun toMrPackEnvironment(side: String): MrPackEnv = when (normalize(side)) {
        CLIENT -> MrPackEnv(client = "required", server = "unsupported")
        DEDICATED_SERVER -> MrPackEnv(client = "unsupported", server = "required")
        else -> MrPackEnv(client = "required", server = "required")
    }

    fun isIncludedOnServer(side: String): Boolean = normalize(side) != CLIENT

    fun isIncludedOnClient(side: String): Boolean = normalize(side) != DEDICATED_SERVER

    private fun fromLegacyModrinth(clientSide: String, serverSide: String): String = when {
        clientSide == "unsupported" && serverSide != "unsupported" -> SERVER
        serverSide == "unsupported" && clientSide != "unsupported" -> CLIENT
        else -> BOTH
    }
}