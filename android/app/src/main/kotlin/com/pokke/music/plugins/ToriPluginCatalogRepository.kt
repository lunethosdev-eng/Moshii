package com.pokke.music.plugins

import com.pokke.music.data.remote.KokoroConfig
import com.pokke.music.data.plugins.PluginRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ToriPluginCatalogRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun isConfigured(): Boolean =
        KokoroConfig.SUPABASE_URL.startsWith("https://") &&
            KokoroConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

    suspend fun fetchCatalog(): Result<List<PluginCatalogItem>> = withContext(Dispatchers.IO) {
        runCatching {
            check(isConfigured()) { "La tienda todavía no está conectada al proyecto Supabase." }
            val base = KokoroConfig.SUPABASE_URL.trimEnd('/')
            val url = "$base/rest/v1/plugins?select=id,name,description,category,author_name,current_version,package_url,sha256,downloads&status=eq.published&order=downloads.desc"
            val request = Request.Builder()
                .url(url)
                .header("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
                .header("Authorization", "Bearer ${KokoroConfig.SUPABASE_PUBLISHABLE_KEY}")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Catálogo no disponible (HTTP ${response.code})." }
                val array = JSONArray(response.body?.string().orEmpty())
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.getJSONObject(index)
                        add(
                            PluginCatalogItem(
                                id = item.getString("id"),
                                name = item.getString("name"),
                                description = item.optString("description"),
                                category = item.optString("category", "feature"),
                                author = item.optString("author_name", "Comunidad"),
                                version = item.getString("current_version"),
                                packageUrl = item.getString("package_url"),
                                sha256 = item.getString("sha256"),
                                downloads = item.optLong("downloads", 0L)
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun fetchPackage(item: PluginCatalogItem): Result<ToriPluginInstaller.InspectedPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(item.packageUrl.startsWith(PluginRepository.PACKAGE_PREFIX)) { "El paquete debe pertenecer al bucket oficial de Tori." }
                require(item.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "El catálogo no contiene un SHA-256 válido." }
                val request = Request.Builder().url(item.packageUrl).get().build()
                val bytes = client.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "No se pudo descargar el plugin (HTTP ${response.code})." }
                    check(response.request.url.toString().startsWith(PluginRepository.PACKAGE_PREFIX)) {
                        "La descarga redirigió fuera del bucket oficial de Tori."
                    }
                    val body = response.body ?: error("El servidor devolvió un archivo vacío.")
                    check(body.contentLength() in -1L..(25L * 1024L * 1024L)) { "El paquete supera 25 MB." }
                    body.byteStream().use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            check(total <= 25 * 1024 * 1024) { "El paquete supera 25 MB." }
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                }
                require(sha256(bytes).equals(item.sha256, ignoreCase = true)) {
                    "La verificación SHA-256 no coincide."
                }
                val inspected = ToriPluginInstaller.inspect(bytes)
                require(inspected.manifest.id == item.id && inspected.manifest.version == item.version) {
                    "El ZIP no coincide con la ficha publicada en el catálogo."
                }
                inspected
            }
        }

    suspend fun downloadAndInstall(
        context: android.content.Context,
        item: PluginCatalogItem,
        grantedPermissions: Set<String>
    ): Result<InstalledToriPlugin> = fetchPackage(item).mapCatching {
        ToriPluginInstaller.install(context, it, grantedPermissions)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
