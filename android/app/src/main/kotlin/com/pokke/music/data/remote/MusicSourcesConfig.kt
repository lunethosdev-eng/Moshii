package com.pokke.music.data.remote

/**
 * Multiple music catalog backends. App merges search from all healthy sources.
 * Auto-switch: sources marked unhealthy are skipped until cooldown expires.
 */
data class MusicSource(
    val id: String,
    val name: String,
    val label: String,
    val supabaseUrl: String,
    val publishableKey: String,
    val isEnabled: Boolean = true,
    val isPrimary: Boolean = false,
    val softLimit: Int = 50_000
)

object MusicSourcesConfig {
    val defaults: List<MusicSource> = listOf(
        MusicSource(
            id = "ukiyo",
            name = "ukiyo",
            label = "Ukiyo (principal)",
            supabaseUrl = UkiyoConfig.SUPABASE_URL,
            publishableKey = UkiyoConfig.SUPABASE_PUBLISHABLE_KEY,
            isPrimary = true,
            softLimit = 80_000
        ),
        MusicSource(
            id = "pokke_server_2",
            name = "pokke_server_2",
            label = "Pokke Server 2",
            supabaseUrl = "https://zcsbmighlvwefvafpecm.supabase.co",
            publishableKey = "sb_publishable_biM1cObx3ewgE7wvZ28hFA_2zD97_At",
            isPrimary = false,
            softLimit = 80_000
        )
    )

    @Volatile
    var runtime: List<MusicSource> = defaults

    private val unhealthyUntil = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun markUnhealthy(sourceId: String, cooldownMs: Long = 60_000L) {
        unhealthyUntil[sourceId] = System.currentTimeMillis() + cooldownMs
    }

    fun isHealthy(sourceId: String): Boolean {
        val until = unhealthyUntil[sourceId] ?: return true
        if (System.currentTimeMillis() >= until) {
            unhealthyUntil.remove(sourceId)
            return true
        }
        return false
    }

    fun enabled(): List<MusicSource> =
        runtime.filter {
            it.isEnabled &&
                it.supabaseUrl.isNotBlank() &&
                !it.supabaseUrl.contains("placeholder") &&
                isHealthy(it.id)
        }

    fun activeWriteTarget(): MusicSource? {
        val all = enabled()
        return all.firstOrNull { it.isPrimary } ?: all.firstOrNull()
    }
}
