package org.kvxd.pakmc.utils

import okio.Path.Companion.toPath
import org.kvxd.pakmc.models.PakConfig

object ConfigIO {

    fun save(config: PakConfig) {
        fs.write("pakmc.json".toPath()) { writeUtf8(jsonFormat.encodeToString(config)) }
    }
}
