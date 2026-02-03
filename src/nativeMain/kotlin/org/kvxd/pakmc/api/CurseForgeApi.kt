package org.kvxd.pakmc.api

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.decodeBase64String
import kotlinx.serialization.Serializable
import org.kvxd.pakmc.models.*
import org.kvxd.pakmc.utils.client

object CurseForgeApi {

    private const val BASE_URL = "https://api.curseforge.com/v1"
    private const val GAME_ID_MC = 432

    // https://github.com/packwiz/packwiz/blob/52b123018f9e19b49703f76e78ad415642acf5c5/curseforge/request.go#L19C26-L19C106
    private const val DEFAULT_KEY_B64 =
        "JDJhJDEwJHNBWVhqblU1N0EzSmpzcmJYM3JVdk92UWk2NHBLS3BnQ2VpbGc1TUM1UGNKL0RYTmlGWWxh"

    private fun resolveKey(userKey: String?): String {
        return if (userKey.isNullOrBlank()) {
            DEFAULT_KEY_B64.decodeBase64String()
        } else {
            userKey
        }
    }

    private fun getLoaderType(loader: String): Int {
        return when (loader.lowercase()) {
            "forge" -> 1
            "fabric" -> 4
            "quilt" -> 5
            "neoforge" -> 6
            else -> 0
        }
    }

    suspend fun searchMod(query: String, userKey: String?): CfMod? {
        val apiKey = resolveKey(userKey)

        val resp = client.get("$BASE_URL/mods/search") {
            header("x-api-key", apiKey)
            parameter("gameId", GAME_ID_MC)
            parameter("classId", 6)
            parameter("searchFilter", query)
            parameter("sortField", 2)
            parameter("sortOrder", "desc")
            parameter("pageSize", 20)
        }

        if (resp.status != HttpStatusCode.OK) return null

        val search: CfSearchResponse = resp.body()
        val results = search.data

        if (results.isEmpty()) return null

        val exactSlug = results.find { it.slug.equals(query, ignoreCase = true) }
        if (exactSlug != null) return exactSlug

        val exactName = results.find { it.name.equals(query, ignoreCase = true) }
        if (exactName != null) return exactName

        val startsWith = results.find { it.name.startsWith(query, ignoreCase = true) }
        if (startsWith != null) return startsWith

        return results.first()
    }

    suspend fun getFiles(modId: Int, loader: String, mcVersion: String, userKey: String?): List<CfFile> {
        val apiKey = resolveKey(userKey)
        val loaderType = getLoaderType(loader)

        val resp = client.get("$BASE_URL/mods/$modId/files") {
            header("x-api-key", apiKey)
            header(HttpHeaders.Accept, "application/json")

            parameter("gameVersion", mcVersion)

            if (loaderType != 0) {
                parameter("modLoaderType", loaderType)
            }
        }

        if (resp.status != HttpStatusCode.OK) return emptyList()

        val filesRes: CfFilesResponse = resp.body()

        return filesRes.data.filter { file ->
            val matchesVersion = file.gameVersions.contains(mcVersion)
            val matchesLoader = if (loaderType != 0) {
                file.gameVersions.any { it.equals(loader, ignoreCase = true) }
            } else true

            matchesVersion && matchesLoader
        }
    }

    suspend fun getMod(modId: Int, userKey: String?): CfMod? {
        val apiKey = resolveKey(userKey)
        val resp = client.get("$BASE_URL/mods/$modId") {
            header("x-api-key", apiKey)
        }

        if (resp.status != HttpStatusCode.OK) return null

        @Serializable
        data class SingleResponse(val data: CfMod)
        return resp.body<SingleResponse>().data
    }
}