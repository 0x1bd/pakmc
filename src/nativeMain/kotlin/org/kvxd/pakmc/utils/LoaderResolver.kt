package org.kvxd.pakmc.utils

import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

object LoaderResolver {

    @Serializable
    data class FabricLoader(val loader: VersionObj)
    @Serializable
    data class VersionObj(val version: String)

    @Serializable
    data class NeoForgeMaven(val versions: List<String>)

    suspend fun resolve(loader: String, mcVersion: String): Pair<String, String> {
        return when (loader.lowercase()) {
            "fabric" -> "fabric-loader" to getLatestFabric(mcVersion)
            "neoforge" -> "neoforge" to getLatestNeoForge(mcVersion)
            "forge" -> "forge" to getLatestForge(mcVersion)
            else -> loader to "*"
        }
    }

    private suspend fun getLatestFabric(mc: String): String {
        return try {
            val url = "https://meta.fabricmc.net/v2/versions/loader/$mc"
            val resp: List<FabricLoader> = client.get(url).body()
            resp.firstOrNull()?.loader?.version ?: "*"
        } catch (e: Exception) { "*" }
    }

    private suspend fun getLatestNeoForge(mc: String): String {
        return try {
            val url = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge"
            val resp: NeoForgeMaven = client.get(url).body()

            val targetMajor = if (mc.startsWith("1.")) mc.removePrefix("1.").split(".")[0] else "0"
            val targetMinor = if (mc.startsWith("1.")) mc.removePrefix("1.").split(".").getOrElse(1) { "0" } else "0"

            val validVersions = resp.versions.filter { v ->
                val parts = v.split(".")
                if (parts.size < 2) return@filter false

                parts[0] == targetMajor && parts[1] == targetMinor
            }

            val stable = validVersions.filter { !it.contains("-beta") && !it.contains("-alpha") && !it.contains("-rc") }

            val candidates = stable.ifEmpty { validVersions }

            candidates.maxWithOrNull(compareNatural()) ?: "*"
        } catch (e: Exception) { "*" }
    }

    private suspend fun getLatestForge(mc: String): String {
        return try {
            val url = "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json"
            val jsonStr: String = client.get(url).body()
            val json = jsonFormat.parseToJsonElement(jsonStr).jsonObject
            val promos = json["promos"]?.jsonObject

            promos?.get("$mc-recommended")?.jsonPrimitive?.content
                ?: promos?.get("$mc-latest")?.jsonPrimitive?.content
                ?: "*"

        } catch (e: Exception) { "*" }
    }

    private fun compareNatural(): Comparator<String> = Comparator { s1, s2 ->
        val parts1 = s1.split(".", "-").mapNotNull { it.toIntOrNull() }
        val parts2 = s2.split(".", "-").mapNotNull { it.toIntOrNull() }
        val length = maxOf(parts1.size, parts2.size)

        for (i in 0 until length) {
            val v1 = parts1.getOrElse(i) { 0 }
            val v2 = parts2.getOrElse(i) { 0 }
            if (v1 != v2) return@Comparator v1 - v2
        }
        0
    }
}