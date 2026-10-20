package org.kvxd.pakmc.utils

object LocalJarNaming {

    fun name(fileName: String): String = fileName.dropLast(4)
        .replace(Regex("[-_][vV]?\\d+(?:\\.[0-9A-Za-z]+)*(?:[-_+].*)?$"), "")

    fun slug(fileName: String): String = name(fileName)
        .lowercase()
        .replace(Regex("[^a-z0-9._-]+"), "-")
        .trim('-')
}
