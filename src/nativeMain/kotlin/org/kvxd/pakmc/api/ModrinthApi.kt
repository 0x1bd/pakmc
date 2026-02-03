package org.kvxd.pakmc.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import org.kvxd.pakmc.models.*
import org.kvxd.pakmc.utils.*

object ModrinthApi {

    const val BASE_ENDPOINT = "https://api.modrinth.com/v2"

    suspend fun getProject(slug: String): MrProject? {
        val resp = client.get("${BASE_ENDPOINT}/project/$slug")

        if (resp.status != HttpStatusCode.OK) return null

        return resp.body()
    }

    suspend fun getVersions(slug: String, loader: String, mcVersion: String): List<MrVersion> {
        val resp = client.get("${BASE_ENDPOINT}/project/$slug/version") {
            parameter("loaders", "[\"$loader\"]")
            parameter("game_versions", "[\"$mcVersion\"]")
        }

        if (resp.status != HttpStatusCode.OK) return emptyList()

        return resp.body()
    }
}