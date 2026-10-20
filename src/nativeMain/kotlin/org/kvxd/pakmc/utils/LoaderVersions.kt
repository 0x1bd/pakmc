package org.kvxd.pakmc.utils

import org.kvxd.pakmc.models.PakConfig

object LoaderVersions {

    suspend fun prepare(
        config: PakConfig,
        resolveLatest: suspend (String, String) -> Pair<String, String> = LoaderResolver::resolve,
        confirmUpdate: (String, String) -> Boolean
    ): PakConfig {
        val current = config.loaderVersion?.takeIf { it.isNotBlank() && it != "*" }
        if (config.loaderPinned) {
            check(current != null) { "Pinned loader has no version. Use pakmc pin loader --version <version>." }
            return config
        }

        val latest = resolveLatest(config.loader, config.mcVersion).second
            .takeIf { it.isNotBlank() && it != "*" }
        check(latest != null || current != null) { "Could not resolve a version for ${config.loader}." }
        if (latest == null || latest == current) return config
        return if (current == null || confirmUpdate(current, latest)) {
            config.copy(loaderVersion = latest)
        } else {
            config
        }
    }
}
