package com.pokke.music.data.plugins

import android.content.Context
import android.net.Uri
import com.pokke.music.data.remote.KokoroConfig
import com.pokke.music.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream

data class PluginCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val category: String,
    val authorName: String,
    val currentVersion: String,
    val iconUrl: String?,
    val packageUrl: String?,
    val sha256: String,
    val downloads: Long
)

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val type: String,
    val minAppVersion: String,
    val permissions: Set<String>,
    val ui: JSONObject
)

data class InstalledPlugin(
    val manifest: PluginManifest,
    val directory: File,
    val enabled: Boolean,
    val sha256: String
)

class PluginRepository(private val context: Context) {
    companion object {
        const val PACKAGE_PREFIX =
            "https://llkatqhbganntyhppchl.supabase.co/storage/v1/object/public/tori-plugin-packages/"
        private const val MAX_ARCHIVE_BYTES = 25 * 1024 * 1024
        private const val MAX_UNPACKED_BYTES = 50L * 1024 * 1024
        private const val MAX_ENTRIES = 256
        private val ALLOWED_PERMISSIONS = setOf("ui.theme", "ui.home_widgets", "ui.icons")
        private val ALLOWED_ASSET_EXTENSIONS = setOf(
            "png", "jpg", "jpeg", "webp", "gif", "wav", "ogg", "mp3", "json", "ttf"
        )
    }

    private val root = File(context.filesDir, "community-plugins").apply { mkdirs() }
    private val state = context.getSharedPreferences("community_plugin_state", Context.MODE_PRIVATE)

    suspend fun fetchCatalog(): List<PluginCatalogItem> = withContext(Dispatchers.IO) {
        val url = URL(
            "${KokoroConfig.SUPABASE_URL.trimEnd('/')}/rest/v1/plugins" +
                "?select=id,name,description,category,author_name,current_version,icon_url,package_url,sha256,downloads" +
                "&status=eq.published&order=downloads.desc"
        )
        val json = JSONArray(request(url, "GET"))
        buildList {
            for (i in 0 until json.length()) {
                val o = json.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                add(
                    PluginCatalogItem(
                        id = id,
                        name = o.optString("name", id),
                        description = o.optString("description"),
                        category = o.optString("category", "Otros"),
                        authorName = o.optString("author_name", "Comunidad"),
                        currentVersion = o.optString("current_version", "1.0.0"),
                        iconUrl = o.optString("icon_url").takeIf { it.startsWith("https://") },
                        packageUrl = o.optString("package_url").takeIf { it.isNotBlank() },
                        sha256 = o.optString("sha256").lowercase(Locale.ROOT),
                        downloads = o.optLong("downloads")
                    )
                )
            }
        }
    }

    suspend fun installFromCatalog(item: PluginCatalogItem): InstalledPlugin = withContext(Dispatchers.IO) {
        require(item.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "El catálogo no contiene un SHA-256 válido." }
        val rpcBody = JSONObject().put("p_id", item.id).toString()
        val response = request(
            URL("${KokoroConfig.SUPABASE_URL}/rest/v1/rpc/plugin_download"),
            "POST",
            rpcBody
        )
        val packageUrl = extractPackageUrl(response)
        require(packageUrl.startsWith(PACKAGE_PREFIX)) { "El paquete no pertenece al almacenamiento oficial de plugins." }
        require(packageUrl == item.packageUrl) { "La URL devuelta por el servidor no coincide con la ficha del catálogo." }
        val bytes = download(URL(packageUrl))
        val actualHash = sha256(bytes)
        require(actualHash.equals(item.sha256, ignoreCase = true)) {
            "SHA-256 incorrecto. La instalación se canceló."
        }
        installArchive(bytes, item.id, actualHash)
    }

    suspend fun installDeepLink(pluginId: String, packageUrl: String): InstalledPlugin = withContext(Dispatchers.IO) {
        require(pluginId.matches(Regex("[A-Za-z0-9._-]{2,80}"))) { "ID de plugin inválido." }
        require(packageUrl.startsWith(PACKAGE_PREFIX)) { "Solo se aceptan enlaces del almacenamiento oficial." }
        val item = fetchCatalog().firstOrNull { it.id == pluginId }
            ?: throw IOException("El plugin no está publicado en el catálogo oficial.")
        require(packageUrl == item.packageUrl) { "El enlace no coincide con el paquete publicado para este plugin." }
        require(item.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "SHA-256 no válido en el catálogo." }
        val bytes = download(URL(packageUrl))
        val digest = sha256(bytes)
        require(digest.equals(item.sha256, true)) { "SHA-256 incorrecto. No se instaló el plugin." }
        installArchive(bytes, pluginId, digest)
    }

    suspend fun importZip(uri: Uri): InstalledPlugin = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_ARCHIVE_BYTES) { "El ZIP supera el límite de 25 MB." }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: throw IOException("No se pudo abrir el ZIP.")
        installArchive(bytes, expectedId = null, digest = sha256(bytes))
    }

    fun installedPlugins(): List<InstalledPlugin> {
        if (!root.exists()) return emptyList()
        return root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { dir ->
                runCatching {
                    val manifestFile = File(dir, "plugin.json")
                    val manifest = parseManifest(manifestFile.readText())
                    InstalledPlugin(
                        manifest = manifest,
                        directory = dir,
                        enabled = state.getBoolean("enabled:${manifest.id}", true),
                        sha256 = state.getString("sha256:${manifest.id}", "").orEmpty()
                    )
                }.getOrNull()
            }?.sortedBy { it.manifest.name.lowercase(Locale.ROOT) }.orEmpty()
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        state.edit().putBoolean("enabled:$pluginId", enabled).apply()
    }

    fun uninstall(pluginId: String) {
        root.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
            val manifest = File(dir, "plugin.json")
            val belongs = runCatching { JSONObject(manifest.readText()).optString("id") == pluginId }.getOrDefault(false)
            if (belongs) dir.deleteRecursively()
        }
        state.edit().remove("enabled:$pluginId").remove("sha256:$pluginId").apply()
    }

    fun activeThemeBackground(): File? = installedPlugins()
        .filter { it.enabled && "ui.theme" in it.manifest.permissions }
        .firstNotNullOfOrNull { plugin ->
            val path = plugin.manifest.ui.optJSONObject("theme")?.optString("background").orEmpty()
            resolveAsset(plugin.directory, path)
        }

    fun activeThemeBackgroundColor(): String? = installedPlugins()
        .filter { it.enabled && "ui.theme" in it.manifest.permissions }
        .firstNotNullOfOrNull { plugin ->
            plugin.manifest.ui.optJSONObject("theme")?.optString("backgroundColor")
                ?.takeIf { it.matches(Regex("#[A-Fa-f0-9]{6}([A-Fa-f0-9]{2})?")) }
        }

    fun activeClickSound(): File? = installedPlugins()
        .filter { it.enabled && "ui.theme" in it.manifest.permissions }
        .firstNotNullOfOrNull { plugin ->
            val theme = plugin.manifest.ui.optJSONObject("theme") ?: return@firstNotNullOfOrNull null
            resolveAsset(plugin.directory, theme.optString("clickSound"))
        }

    fun activeMotionPreset(): JSONObject? = installedPlugins()
        .filter { it.enabled && "ui.theme" in it.manifest.permissions }
        .firstNotNullOfOrNull { plugin ->
            val theme = plugin.manifest.ui.optJSONObject("theme") ?: return@firstNotNullOfOrNull null
            theme.optJSONObject("motion") ?: resolveAsset(plugin.directory, theme.optString("motionAsset"))
                ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        }

    fun activeHomeCards(): List<JSONObject> = installedPlugins()
        .filter { it.enabled && "ui.home_widgets" in it.manifest.permissions }
        .flatMap { plugin ->
            val cards = plugin.manifest.ui.optJSONArray("homeCards") ?: JSONArray()
            (0 until cards.length()).mapNotNull { index ->
                cards.optJSONObject(index)?.let { JSONObject(it.toString()).put("_pluginId", plugin.manifest.id).put("_pluginDir", plugin.directory.absolutePath) }
            }
        }.take(8)

    private fun installArchive(bytes: ByteArray, expectedId: String?, digest: String): InstalledPlugin {
        require(bytes.isNotEmpty() && bytes.size <= MAX_ARCHIVE_BYTES) { "ZIP vacío o demasiado grande." }
        val staging = File(root, ".staging-${System.nanoTime()}").apply { mkdirs() }
        try {
            var total = 0L
            var count = 0
            val seenEntries = mutableSetOf<String>()
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    count++
                    require(count <= MAX_ENTRIES) { "El ZIP contiene demasiados archivos." }
                    val raw = entry.name.replace('\\', '/').trimEnd('/')
                    require(seenEntries.add(raw)) { "El ZIP contiene una ruta duplicada." }
                    require(!raw.startsWith("/") && !raw.contains(':')) { "Ruta no permitida en el ZIP." }
                    val segments = raw.split('/')
                    require(segments.none { it == ".." || it == "." || it.isBlank() }) { "Ruta insegura en el ZIP." }
                    require(raw == "plugin.json" || raw == "assets" || raw.startsWith("assets/")) {
                        "Solo se permiten plugin.json y archivos dentro de assets/."
                    }
                    if (!entry.isDirectory) {
                        val ext = raw.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        require(raw == "plugin.json" || ext in ALLOWED_ASSET_EXTENSIONS) {
                            "Tipo de archivo no permitido: $raw"
                        }
                    }
                    val outFile = File(staging, raw)
                    require(outFile.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                        "Ruta ZIP fuera del directorio del plugin."
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                total += read
                                require(total <= MAX_UNPACKED_BYTES) { "El contenido descomprimido supera 50 MB." }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
            val manifestFile = File(staging, "plugin.json")
            require(manifestFile.isFile) { "Falta plugin.json en la raíz del ZIP." }
            require(manifestFile.length() <= 128 * 1024) { "plugin.json supera 128 KB." }
            val manifest = parseManifest(manifestFile.readText())
            require(expectedId == null || manifest.id == expectedId) { "El ID del manifiesto no coincide con el catálogo." }
            validateAssetReferences(staging, manifest)
            val previous = root.listFiles()?.filter { candidate ->
                candidate.isDirectory && candidate != staging && !candidate.name.startsWith(".backup-") &&
                    runCatching { JSONObject(File(candidate, "plugin.json").readText()).optString("id") == manifest.id }
                        .getOrDefault(false)
            }.orEmpty()
            val backups = mutableListOf<Pair<File, File>>()
            val destination = File(root, "${manifest.id}-${manifest.version}")
            try {
                previous.forEach { old ->
                    val backup = File(root, ".backup-${manifest.id}-${System.nanoTime()}")
                    check(old.renameTo(backup)) { "No se pudo preparar la actualización sin perder la versión anterior." }
                    backups += old to backup
                }
                if (destination.exists()) destination.deleteRecursively()
                check(staging.renameTo(destination)) { "No se pudo guardar el plugin en el almacenamiento privado." }
            } catch (installError: Throwable) {
                backups.asReversed().forEach { (old, backup) ->
                    if (backup.exists()) backup.renameTo(old)
                }
                throw installError
            }
            backups.forEach { (_, backup) -> backup.deleteRecursively() }
            state.edit()
                .putBoolean("enabled:${manifest.id}", true)
                .putString("sha256:${manifest.id}", digest)
                .apply()
            return InstalledPlugin(manifest, destination, true, digest)
        } catch (t: Throwable) {
            staging.deleteRecursively()
            throw t
        }
    }

    private fun parseManifest(source: String): PluginManifest {
        val json = JSONObject(source)
        val id = json.optString("id")
        val name = json.optString("name")
        val version = json.optString("version")
        val type = json.optString("type", "ui")
        val minVersion = json.optString("minAppVersion", "1.0.0")
        require(id.matches(Regex("[A-Za-z0-9._-]{2,80}"))) { "ID inválido en plugin.json." }
        require(name.isNotBlank() && name.length <= 80) { "Nombre de plugin inválido." }
        require(version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+([+-][A-Za-z0-9.-]+)?"))) { "Versión inválida." }
        require(type in setOf("ui", "function", "catalog", "theme")) { "Tipo de plugin no admitido." }
        require(compareVersions(minVersion, BuildConfig.VERSION_NAME) <= 0) {
            "Este plugin necesita Tori $minVersion o posterior."
        }
        val permissionsJson = if (json.has("permissions")) {
            json.optJSONArray("permissions") ?: throw IllegalArgumentException("permissions debe ser una lista.")
        } else JSONArray()
        require(permissionsJson.length() <= 20) { "El plugin declara demasiadas capacidades." }
        val declaredPermissions = (0 until permissionsJson.length()).map { permissionsJson.optString(it) }
        require(declaredPermissions.distinct().size == declaredPermissions.size) { "El manifiesto contiene capacidades duplicadas." }
        val unknownPermissions = declaredPermissions.toSet() - ALLOWED_PERMISSIONS
        require(unknownPermissions.isEmpty()) {
            "Capacidades no compatibles con esta versión de Tori: ${unknownPermissions.joinToString()}"
        }
        val permissions = declaredPermissions.toSet()
        val ui = json.optJSONObject("ui") ?: JSONObject()
        if ("ui.theme" !in permissions) ui.remove("theme")
        if ("ui.home_widgets" !in permissions) ui.remove("homeCards")
        if ("ui.icons" !in permissions) ui.remove("icons")
        val cards = ui.optJSONArray("homeCards")
        if (cards != null) {
            val safeCards = JSONArray()
            val allowedScreens = setOf("home", "library", "search", "profile", "settings", "player")
            for (i in 0 until cards.length()) {
                val card = cards.optJSONObject(i) ?: continue
                val action = card.optString("action")
                if (!action.startsWith("open:") || action.removePrefix("open:") !in allowedScreens) continue
                val title = card.optString("title").take(60)
                if (title.isBlank()) continue
                val safe = JSONObject().put("title", title)
                    .put("subtitle", card.optString("subtitle").take(120))
                    .put("action", action)
                if ("ui.icons" in permissions) {
                    val iconAsset = card.optString("iconAsset")
                    if (iconAsset.startsWith("assets/")) safe.put("iconAsset", iconAsset)
                }
                safeCards.put(safe)
            }
            ui.put("homeCards", safeCards)
        }
        return PluginManifest(id, name, version, type, minVersion, permissions, ui)
    }

    private fun validateAssetReferences(directory: File, manifest: PluginManifest) {
        val theme = manifest.ui.optJSONObject("theme")
        val paths = listOf(
            theme?.optString("background").orEmpty(),
            theme?.optString("clickSound").orEmpty(),
            theme?.optString("font").orEmpty(),
            theme?.optString("motionAsset").orEmpty()
        ) + listOf("homeCards", "icons").flatMap { key ->
            val arr = manifest.ui.optJSONArray(key) ?: JSONArray()
            (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.optString("iconAsset") }
        }
        paths.filter { it.isNotBlank() }.forEach { path ->
            require(path.startsWith("assets/")) { "Los recursos deben estar dentro de assets/." }
            require(resolveAsset(directory, path) != null) { "No existe el recurso declarado: $path" }
        }
    }

    private fun resolveAsset(directory: File, path: String): File? {
        if (path.isBlank() || !path.startsWith("assets/") || path.contains("..")) return null
        val file = File(directory, path)
        return file.takeIf { it.isFile && it.canonicalPath.startsWith(directory.canonicalPath + File.separator) }
    }

    private fun extractPackageUrl(response: String): String {
        val trimmed = response.trim()
        val value = if (trimmed.startsWith("[")) {
            JSONArray(trimmed).optJSONObject(0)?.optString("package_url").orEmpty()
        } else {
            runCatching { JSONObject(trimmed).optString("package_url") }.getOrDefault(trimmed.trim('"'))
        }
        return value
    }

    private fun compareVersions(required: String, current: String): Int {
        fun parts(value: String) = value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(required)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val left = a.getOrElse(i) { 0 }
            val right = b.getOrElse(i) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return 0
    }

    private fun request(url: URL, method: String, body: String? = null): String {
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${KokoroConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw IOException("Supabase respondió HTTP $code: ${response.take(500)}")
        return response
    }

    private fun download(url: URL): ByteArray {
        require(url.protocol == "https") { "Solo se permiten descargas HTTPS." }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 25_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/zip")
        }
        val code = connection.responseCode
        require(connection.url.toString().startsWith(PACKAGE_PREFIX)) {
            "La descarga redirigió fuera del bucket oficial."
        }
        if (code !in 200..299) throw IOException("Descarga fallida: HTTP $code")
        val out = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_ARCHIVE_BYTES) { "El ZIP supera el límite de 25 MB." }
                out.write(buffer, 0, read)
            }
        }
        connection.disconnect()
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
