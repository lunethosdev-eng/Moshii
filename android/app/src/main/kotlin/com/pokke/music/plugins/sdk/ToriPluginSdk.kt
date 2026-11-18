package com.pokke.music.plugins.sdk

/**
 * Stable, compile-time extension contract for trusted modules shipped inside Tori.
 * Community ZIP plugins remain declarative data and are never loaded as code.
 */
object ToriPluginApi {
    const val VERSION = 1
}

data class ToriPluginDescriptor(
    val id: String,
    val name: String,
    val version: String,
    val apiVersion: Int = ToriPluginApi.VERSION,
    val capabilities: Set<String>
)

sealed interface ToriPluginContribution {
    data class HomeCard(
        val id: String,
        val title: String,
        val subtitle: String = "",
        /** One of: home, library, search, profile, settings, player. */
        val destination: String
    ) : ToriPluginContribution
}

/** Implemented by trusted Kotlin modules that are compiled into the application. */
interface ToriNativeExtension {
    val descriptor: ToriPluginDescriptor
    fun contributions(): List<ToriPluginContribution> = emptyList()
}

/**
 * Explicit registry; no reflection, classpath scanning, downloaded bytecode or dynamic loading.
 * Registration is intended for the application's trusted module/bootstrap code.
 */
object ToriPluginRegistry {
    private val lock = Any()
    private val extensions = linkedMapOf<String, ToriNativeExtension>()
    private val supportedCapabilities = setOf("ui.home_widgets")
    private val allowedDestinations = setOf("home", "library", "search", "profile", "settings", "player")

    fun register(extension: ToriNativeExtension) = synchronized(lock) {
        val descriptor = extension.descriptor
        require(descriptor.id.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))) { "ID de extensión no válido." }
        require(descriptor.name.isNotBlank() && descriptor.name.length <= 80) { "Nombre de extensión no válido." }
        require(descriptor.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+([+-][A-Za-z0-9.-]+)?"))) { "Versión de extensión no válida." }
        require(descriptor.apiVersion == ToriPluginApi.VERSION) { "La extensión requiere otra versión de Tori Plugin API." }
        require(descriptor.capabilities.all { it in supportedCapabilities }) { "La extensión solicita capacidades no compatibles." }
        require(descriptor.id !in extensions) { "Ya existe una extensión registrada con ese ID." }
        val cards = extension.contributions().filterIsInstance<ToriPluginContribution.HomeCard>()
        require(cards.size <= 8) { "Una extensión no puede aportar más de ocho tarjetas." }
        require(cards.map { it.id }.distinct().size == cards.size) { "La extensión contiene IDs de tarjeta duplicados." }
        require(cards.isEmpty() || "ui.home_widgets" in descriptor.capabilities) {
            "Las tarjetas requieren la capacidad ui.home_widgets."
        }
        require(cards.all {
            it.id.matches(Regex("[a-zA-Z0-9._-]{1,64}")) &&
                it.title.isNotBlank() && it.title.length <= 60 &&
                it.subtitle.length <= 120 && it.destination in allowedDestinations
        }) { "La extensión contiene una tarjeta o acción no válida." }
        extensions[descriptor.id] = extension
    }

    fun unregister(id: String): Boolean = synchronized(lock) { extensions.remove(id) != null }

    fun homeCards(): List<Pair<String, ToriPluginContribution.HomeCard>> = synchronized(lock) {
        extensions.values.asSequence()
            .filter { "ui.home_widgets" in it.descriptor.capabilities }
            .flatMap { extension ->
                extension.contributions().filterIsInstance<ToriPluginContribution.HomeCard>()
                    .asSequence().map { extension.descriptor.id to it }
            }
            .take(8)
            .toList()
    }

    fun descriptors(): List<ToriPluginDescriptor> = synchronized(lock) {
        extensions.values.map { it.descriptor }
    }
}
