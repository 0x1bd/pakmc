package org.kvxd.pakmc.commands

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import io.ktor.client.call.*
import io.ktor.client.request.*
import okio.Path
import okio.Path.Companion.toPath
import org.kvxd.pakmc.core.PakCommand
import org.kvxd.pakmc.models.*
import org.kvxd.pakmc.utils.*
import kotlin.system.exitProcess

class BuildCommand : PakCommand(name = "build", help = "Build the pack (client .mrpack or server .zip)") {

    private val target by argument(help = "Build target").choice("client", "server")

    override suspend fun execute(config: PakConfig) {
        validateManualMods()
        if (target == "client") buildClient(config) else buildServer(config)
    }

    private fun validateManualMods() {
        val missingMods = ModIO.getAllMods().filter { meta ->
            meta.downloadUrl.isBlank() && !fs.exists("contents/jarmods/${meta.fileName}".toPath())
        }

        if (missingMods.isNotEmpty()) {
            t.println(red(bold("BUILD FAILED: Missing Manual Dependencies")))
            t.println(white("The following restricted mods must be downloaded manually to: ") + cyan("contents/jarmods/"))
            t.println("")
            missingMods.forEach { mod ->
                t.println(red(" [MISSING] ") + bold(mod.fileName))
                t.println(gray("    Link: ") + blue(mod.manualLink ?: "Unknown"))
            }

            exitProcess(1)
        }
    }

    private suspend fun buildClient(config: PakConfig) {
        t.println(bold("Building Client Package (.mrpack)..."))

        t.println(gray(" -> Resolving loader version for ${config.loader} (MC ${config.mcVersion})..."))
        val (loaderKey, loaderVer) = LoaderResolver.resolve(config.loader, config.mcVersion)
        t.println(green(" + Selected Loader: ") + white("$loaderKey $loaderVer"))

        val buildDir = "build/client".toPath()
        fs.deleteRecursively(buildDir)
        fs.createDirectories(buildDir)

        val mrFiles = mutableListOf<MrPackFile>()

        ModIO.getAllMods().forEach { meta ->
            if (meta.downloadUrl.isBlank()) return@forEach

            val finalHashes = if (meta.hashes.containsKey("sha512")) {
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
                } catch (_: Exception) {
                    t.println(red(" ! Hash failed: ${meta.name}"))
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

        val index = MrPackIndex(
            versionId = config.version, name = config.name,
            dependencies = mapOf("minecraft" to config.mcVersion, loaderKey to loaderVer),
            files = mrFiles
        )

        fs.write(buildDir / "modrinth.index.json") { writeUtf8(jsonFormat.encodeToString(index)) }

        val overrides = buildDir / "overrides"
        fs.createDirectories(overrides)
        copyDir("contents/configs".toPath(), overrides / "config")
        copyDir("contents/jarmods".toPath(), overrides / "mods")

        zipDir(buildDir, "${config.name}-${config.version}.mrpack")
    }

    private suspend fun buildServer(config: PakConfig) {
        t.println(bold("Building Server Package (.zip)..."))
        val buildDir = "build/server".toPath()
        fs.deleteRecursively(buildDir)
        fs.createDirectories(buildDir / "mods")

        ModIO.getAllMods().forEach { meta ->
            if (meta.side == "client") return@forEach
            val dest = buildDir / "mods" / meta.fileName

            if (meta.downloadUrl.isBlank()) {
                fs.copy("contents/jarmods/${meta.fileName}".toPath(), dest)
                t.println(cyan(" [LOCAL] ") + white(meta.fileName))
            } else {
                t.println(gray(" [NET] Downloading: ") + white(meta.fileName))
                try {
                    val bytes: ByteArray = client.get(meta.downloadUrl).body()
                    fs.write(dest) { write(bytes) }
                } catch (_: Exception) {
                    t.println(red(" ! Download failed: ${meta.name}"))
                }
            }
        }

        copyDir("contents/configs".toPath(), buildDir / "config")
        copyDir("contents/jarmods".toPath(), buildDir / "mods")

        zipDir(buildDir, "${config.name}-${config.version}-server.zip")
    }

    private fun copyDir(src: Path, dest: Path) {
        if (!fs.exists(src)) return
        fs.createDirectories(dest)
        fs.list(src).forEach { file ->
            val d = dest / file.name
            if (fs.metadata(file).isDirectory) copyDir(file, d)
            else if(!fs.exists(d)) fs.copy(file, d)
        }
    }

    private fun zipDir(src: Path, outName: String) {
        t.println(gray(" -> Compressing..."))
        if (runCommand("cd $src && zip -q -r ../../$outName .") == 0) {
            t.println(green("Success: ") + white(outName))
        } else {
            t.println(red("Error: Zip command failed."))
        }
    }
}