package com.pokke.music.plugins

import android.content.Context
import android.net.Uri
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Installs declarative Tori plugin packages. ZIPs are data-only: executable
 * Android code, DEX, native libraries and scripts are rejected.
 */
object ToriPluginInstaller {
    private const val MAX_ARCHIVE_BYTES = 25L * 1024L * 1024L
    private const val MAX_ENTRY_BYTES = 8L * 1024L * 1024L
    private const val MAX_ENTRIES = 300
    private val json = Json { ignoreUnknownKeys = false; isLenient = false }

    data class InspectedPackage(
        val manifest: ToriPluginManifest,
        internal val files: Map<String, ByteArray>
    )

    fun inspect(context: Context, uri: Uri): InspectedPackage {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_ARCHIVE_BYTES) }
            ?: error("No se pudo leer el archivo ZIP.")
        return inspect(bytes)
    }

    fun inspect(bytes: ByteArray): InspectedPackage {
        require(bytes.size.toLong() <= MAX_ARCHIVE_BYTES) { "El paquete supera el límite de 25 MB." }
        val files = linkedMapOf<String, ByteArray>()
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= MAX_ENTRIES) { "El ZIP contiene demasiados archivos." }
                val path = validatePath(entry.name)
                if (!entry.isDirectory) {
                    require(!isExecutable(path)) { "Los plugins no pueden incluir código ejecutable: $path" }
                    require(path !in files) { "El ZIP contiene una ruta duplicada: $path" }
                    val content = zip.readBounded(MAX_ENTRY_BYTES)
                    total += content.size
                    require(total <= MAX_ARCHIVE_BYTES) { "El contenido descomprimido supera 25 MB." }
                    files[path] = content
                }
                zip.closeEntry()
            }
        }
        val manifestBytes = files["plugin.json"] ?: error("Falta plugin.json en la raíz del ZIP.")
        val manifest = json.decodeFromString<ToriPluginManifest>(manifestBytes.decodeToString())
        validateManifest(manifest)
        return InspectedPackage(manifest, files)
    }

    fun install(
        context: Context,
        inspected: InspectedPackage,
        grantedPermissions: Set<String>
    ): InstalledToriPlugin {
        val requested = inspected.manifest.permissions.toSet()
        require(grantedPermissions.containsAll(requested)) { "Debes aprobar todos los permisos solicitados." }
        val root = File(context.filesDir, "tori-plugins")
        val staging = File(root, ".install-${inspected.manifest.id}-${System.nanoTime()}")
        val destination = File(root, inspected.manifest.id)
        require(!staging.exists() && staging.mkdirs()) { "No se pudo preparar la instalación." }
        try {
            inspected.files.forEach { (relative, content) ->
                val output = File(staging, relative)
                require(output.canonicalPath.startsWith(staging.canonicalPath + File.separator)) {
                    "Ruta no permitida en el paquete."
                }
                output.parentFile?.mkdirs()
                output.writeBytes(content)
            }
            File(staging, "granted-permissions.txt").writeText(requested.sorted().joinToString("\n"))
            if (destination.exists()) destination.deleteRecursively()
            check(staging.renameTo(destination)) { "No se pudo finalizar la instalación." }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        }
        return InstalledToriPlugin(
            manifest = inspected.manifest,
            directory = destination.absolutePath,
            grantedPermissions = requested
        )
    }

    fun listInstalled(context: Context): List<InstalledToriPlugin> {
        val root = File(context.filesDir, "tori-plugins")
        return root.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.mapNotNull { dir ->
            runCatching {
                val manifestFile = File(dir, "plugin.json")
                val manifest = json.decodeFromString<ToriPluginManifest>(manifestFile.readText())
                val grants = File(dir, "granted-permissions.txt").takeIf { it.exists() }
                    ?.readLines()?.filter(String::isNotBlank)?.toSet().orEmpty()
                InstalledToriPlugin(manifest, dir.absolutePath, grants)
            }.getOrNull()
        }?.sortedBy { it.manifest.name } ?: emptyList()
    }

    fun uninstall(context: Context, pluginId: String): Boolean {
        require(pluginId.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))) { "Identificador no válido." }
        val root = File(context.filesDir, "tori-plugins").canonicalFile
        val target = File(root, pluginId).canonicalFile
        require(target.parentFile == root) { "Ruta de plugin no válida." }
        return target.deleteRecursively()
    }

    private fun validateManifest(manifest: ToriPluginManifest) {
        require(manifest.id.matches(Regex("[a-z0-9][a-z0-9._-]{2,63}"))) { "ID de plugin no válido." }
        require(manifest.name.isNotBlank() && manifest.name.length <= 80) { "Nombre de plugin no válido." }
        require(manifest.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+([+-][A-Za-z0-9.-]+)?"))) {
            "La versión debe usar SemVer, por ejemplo 1.0.0."
        }
        require(manifest.permissions.size <= 20) { "Demasiados permisos solicitados." }
        val unknown = manifest.permissions.toSet() - ToriPluginCapabilities.allowed
        require(unknown.isEmpty()) { "Permisos desconocidos: ${unknown.joinToString()}" }
        if (manifest.ui?.theme != null) {
            require("ui.theme" in manifest.permissions) { "Un tema debe declarar ui.theme." }
        }
        if (manifest.ui?.homeCards?.isNotEmpty() == true) {
            require("ui.home_widgets" in manifest.permissions) { "Las tarjetas de inicio deben declarar ui.home_widgets." }
        }
        manifest.musicSource?.let {
            require("catalog.music_source" in manifest.permissions && "network.http" in manifest.permissions) {
                "Una fuente musical debe declarar catalog.music_source y network.http."
            }
            require(it.catalogEndpoint.startsWith("https://")) { "El catálogo musical debe usar HTTPS." }
        }
    }

    private fun validatePath(raw: String): String {
        require(raw.isNotBlank() && !raw.startsWith("/") && !raw.contains('\\')) { "Ruta insegura en ZIP." }
        val segments = raw.split('/')
        require(segments.none { it == ".." || it == "." || it.isBlank() }) { "Ruta insegura en ZIP." }
        return raw
    }

    private fun isExecutable(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("dex", "jar", "apk", "so", "class", "js", "mjs", "cjs", "kt", "java", "py", "sh", "bat", "exe", "dll", "wasm")
    }

    private fun java.io.InputStream.readBounded(limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var count = 0L
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            count += read
            require(count <= limit) { "El archivo supera el límite permitido." }
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }
}
