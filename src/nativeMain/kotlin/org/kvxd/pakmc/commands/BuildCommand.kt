package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.terminal.Terminal
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.kvxd.pakmc.models.*
import okio.Path
import okio.Path.Companion.toPath
import org.kvxd.pakmc.utils.*
import kotlin.system.exitProcess

class BuildCommand : CliktCommand(name = "build") {

    private val target by argument(help = "Build target").choice("client", "server")
    private val t = Terminal()

    override fun help(context: Context): String = "Build the pack (client .mrpack or server .zip)"

    override fun run() = runBlocking {
        if (!fs.exists("pakmc.json".toPath())) {
            t.println(red("Error: pakmc.json not found."))
            return@runBlocking
        }

        val configStr = fs.read("pakmc.json".toPath()) { readUtf8() }
        val config = jsonFormat.decodeFromString<PakConfig>(configStr)

        // --- STEP 1: PRE-FLIGHT CHECK ---
        validateManualMods()

        // --- STEP 2: BUILD ---
        if (target == "client") buildClient(config) else buildServer(config)
    }

    private fun validateManualMods() {
        val modsPath = "contents/mods".toPath()
        if (!fs.exists(modsPath)) return

        val missingMods = mutableListOf<LocalModMeta>()

        fs.list(modsPath).filter { it.name.endsWith(".json") }.forEach { path ->
            val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })

            if (meta.downloadUrl.isBlank()) {
                val expectedPath = "contents/jarmods/${meta.fileName}".toPath()
                if (!fs.exists(expectedPath)) {
                    missingMods.add(meta)
                }
            }
        }

        if (missingMods.isNotEmpty()) {
            t.println(red(bold("BUILD FAILED: Missing Manual Dependencies")))
            t.println(white("The following restricted mods must be downloaded manually to: ") + cyan("contents/jarmods/"))
            t.println("")

            missingMods.forEach { mod ->
                t.println(red(" [MISSING] ") + bold(mod.fileName))
                t.println(gray("    Link: ") + blue(mod.manualLink ?: "Unknown"))
            }
            t.println("")
            exitProcess(1)
        }
    }

    private suspend fun buildClient(config: PakConfig) {
        t.println(bold("Building Client Package (.mrpack)..."))

        t.println(gray(" -> Resolving loader version for ${config.loader} (MC ${config.mcVersion})..."))
        val (loaderKey, loaderVer) = LoaderResolver.resolve(config.loader, config.mcVersion)

        if (loaderVer == "*") {
            t.println(yellow(" ! Warning: Could not resolve exact loader version. Using wildcard '*' (this may fail in Prism)."))
        } else {
            t.println(green(" + Selected Loader: ") + white("$loaderKey $loaderVer"))
        }

        val buildDir = "build/client".toPath()
        fs.deleteRecursively(buildDir)
        fs.createDirectories(buildDir)

        val mrFiles = mutableListOf<MrPackFile>()
        val modsPath = "contents/mods".toPath()

        if (fs.exists(modsPath)) {
            fs.list(modsPath).filter { it.name.endsWith(".json") }.forEach { path ->
                val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })

                // Manual mods go to overrides, not index
                if (meta.downloadUrl.isBlank()) return@forEach

                val finalHashes = if (meta.hashes.containsKey("sha512") && meta.hashes.containsKey("sha1")) {
                    meta.hashes
                } else {
                    t.println(gray(" -> Computing hashes: ") + white(meta.fileName))
                    val tempPath = buildDir / "${meta.slug}.tmp"
                    try {
                        val bytes: ByteArray = client.get(meta.downloadUrl).body()
                        fs.write(tempPath) { write(bytes) }
                        val computed = calculateHashes(tempPath, fs)
                        fs.delete(tempPath)
                        computed
                    } catch (e: Exception) {
                        t.println(red(" ! Hash calculation failed: ") + white(meta.name))
                        meta.hashes
                    }
                }

                val env = when (meta.side) {
                    "client" -> MrPackEnv("required", "unsupported")
                    "server" -> MrPackEnv("unsupported", "required")
                    else -> MrPackEnv("required", "required")
                }

                mrFiles.add(MrPackFile(
                    path = "mods/${meta.fileName}",
                    hashes = finalHashes,
                    env = env,
                    downloads = listOf(meta.downloadUrl)
                ))
            }
        }

        val index = MrPackIndex(
            versionId = config.version,
            name = config.name,
            dependencies = mapOf(
                "minecraft" to config.mcVersion,
                loaderKey to loaderVer
            ),
            files = mrFiles
        )

        fs.write(buildDir / "modrinth.index.json") {
            writeUtf8(jsonFormat.encodeToString(index))
        }

        val overrides = buildDir / "overrides"
        fs.createDirectories(overrides)
        copyDir("contents/configs".toPath(), overrides / "config")
        copyDir("contents/jarmods".toPath(), overrides / "mods")

        val outFile = "${config.name}-${config.version}.mrpack"
        zipDir(buildDir, outFile)
    }

    private suspend fun buildServer(config: PakConfig) {
        t.println(bold("Building Server Package (.zip)..."))
        val buildDir = "build/server".toPath()
        fs.deleteRecursively(buildDir)
        fs.createDirectories(buildDir / "mods")

        val modsPath = "contents/mods".toPath()
        if (fs.exists(modsPath)) {
            fs.list(modsPath).filter { it.name.endsWith(".json") }.forEach { path ->
                val meta = jsonFormat.decodeFromString<LocalModMeta>(fs.read(path) { readUtf8() })
                if (meta.side == "client") return@forEach

                val dest = buildDir / "mods" / meta.fileName

                if (meta.downloadUrl.isBlank()) {
                    val localSource = "contents/jarmods/${meta.fileName}".toPath()
                    fs.copy(localSource, dest)
                    t.println(cyan(" [LOCAL] ") + white(meta.fileName))
                } else {
                    t.println(gray(" [NET] Downloading: ") + white(meta.fileName))
                    try {
                        val bytes: ByteArray = client.get(meta.downloadUrl).body()
                        fs.write(dest) { write(bytes) }
                    } catch (e: Exception) {
                        t.println(red(" ! Download failed: ") + white(meta.name))
                    }
                }
            }
        }

        copyDir("contents/configs".toPath(), buildDir / "config")
        copyDir("contents/jarmods".toPath(), buildDir / "mods")

        val outFile = "${config.name}-${config.version}-server.zip"
        zipDir(buildDir, outFile)
    }

    private fun copyDir(src: Path, dest: Path) {
        if (!fs.exists(src)) return
        fs.createDirectories(dest)
        fs.list(src).forEach { file ->
            val d = dest / file.name
            if (fs.metadata(file).isDirectory) copyDir(file, d)
            else {
                if(!fs.exists(d)) fs.copy(file, d)
            }
        }
    }

    private fun zipDir(src: Path, outName: String) {
        t.println(gray(" -> Compressing archive..."))
        val cmd = "cd $src && zip -q -r ../../$outName ."
        val result = runCommand(cmd)
        if (result == 0) t.println(green("Success: ") + white(outName))
        else t.println(red("Error: Zip command failed (code $result)."))
    }
}