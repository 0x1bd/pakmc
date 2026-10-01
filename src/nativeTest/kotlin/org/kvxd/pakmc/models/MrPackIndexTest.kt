package org.kvxd.pakmc.models

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.kvxd.pakmc.utils.jsonFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MrPackIndexTest {
    @Test
    fun manifestIncludesRequiredFileSizeWithoutIntegerTruncation() {
        val index = MrPackIndex(
            versionId = "1.0.0",
            name = "Test pack",
            dependencies = mapOf("minecraft" to "1.21.1", "fabric-loader" to "0.16.0"),
            files = listOf(
                MrPackFile(
                    path = "mods/example.jar",
                    hashes = mapOf("sha1" to "a".repeat(40), "sha512" to "b".repeat(128)),
                    env = MrPackEnv(client = "required", server = "unsupported"),
                    downloads = listOf("https://cdn.modrinth.com/example.jar"),
                    fileSize = 5_000_000_000L
                )
            )
        )

        val encoded = jsonFormat.encodeToString(index)
        val manifest = jsonFormat.parseToJsonElement(encoded).jsonObject
        val file = manifest.getValue("files").jsonArray.single().jsonObject

        assertEquals(5_000_000_000L, file.getValue("fileSize").jsonPrimitive.long)
        assertEquals(index, jsonFormat.decodeFromString<MrPackIndex>(encoded))
    }

    @Test
    @OptIn(ExperimentalSerializationApi::class)
    fun missingFileSizeIsRejectedInsteadOfDefaultingToZero() {
        val manifest = """
            {
              "versionId": "1.0.0",
              "name": "Test pack",
              "dependencies": {"minecraft": "1.21.1"},
              "files": [{
                "path": "mods/example.jar",
                "hashes": {"sha1": "abc", "sha512": "def"},
                "downloads": ["https://cdn.modrinth.com/example.jar"]
              }]
            }
        """.trimIndent()

        assertFailsWith<MissingFieldException> {
            jsonFormat.decodeFromString<MrPackIndex>(manifest)
        }
    }
}