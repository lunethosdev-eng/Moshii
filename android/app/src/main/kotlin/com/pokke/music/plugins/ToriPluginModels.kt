package com.pokke.music.plugins

import kotlinx.serialization.Serializable

@Serializable
data class ToriPluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val type: String = "feature",
    val minAppVersion: String = "1.0.0",
    val permissions: List<String> = emptyList(),
    val entryPoints: List<String> = emptyList(),
    val ui: PluginUiDefinition? = null,
    val musicSource: PluginMusicSource? = null
)

@Serializable
data class PluginUiDefinition(
    val theme: PluginTheme? = null,
    val homeCards: List<PluginHomeCard> = emptyList()
)

@Serializable
data class PluginTheme(
    val accentColor: String? = null,
    val backgroundColor: String? = null,
    val cornerRadiusDp: Float? = null
)

@Serializable
data class PluginHomeCard(
    val title: String,
    val subtitle: String = "",
    val icon: String = "extension",
    val action: String
)

@Serializable
data class PluginMusicSource(
    val displayName: String,
    val catalogEndpoint: String,
    val searchEndpoint: String? = null,
    val artworkBaseUrl: String? = null
)

data class InstalledToriPlugin(
    val manifest: ToriPluginManifest,
    val directory: String,
    val grantedPermissions: Set<String>
)

data class PluginCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val author: String,
    val version: String,
    val packageUrl: String,
    val sha256: String,
    val downloads: Long = 0L
)

object ToriPluginCapabilities {
    val allowed = setOf(
        "ui.theme",
        "ui.home_widgets",
        "ui.player_overlay",
        "catalog.music_source",
        "player.queue.read",
        "player.queue.write",
        "player.metadata.read",
        "network.http"
    )
}
