package com.pokke.music.ui.plugins

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pokke.music.data.plugins.InstalledPlugin
import com.pokke.music.data.plugins.PluginCatalogItem
import com.pokke.music.data.plugins.PluginRepository
import kotlinx.coroutines.launch

@Composable
fun PluginStoreSection(
    deepLinkUri: Uri? = null,
    onDeepLinkHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { PluginRepository(context) }
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<List<PluginCatalogItem>>(emptyList()) }
    var installed by remember { mutableStateOf<List<InstalledPlugin>>(emptyList()) }
    var selectedTab by remember { mutableStateOf("catalog") }
    var loading by remember { mutableStateOf(false) }
    var workingId by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingLink by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showManualImportWarning by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        runCatching { repository.fetchCatalog() }
            .onSuccess { catalog = it }
            .onFailure { message = it.message ?: "No se pudo cargar el catálogo." }
        installed = repository.installedPlugins()
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(deepLinkUri) {
        if (deepLinkUri != null) {
            val id = deepLinkUri.getQueryParameter("id").orEmpty()
            val url = deepLinkUri.getQueryParameter("url").orEmpty()
            if (deepLinkUri.scheme == "pokke" && deepLinkUri.host == "plugin" &&
                deepLinkUri.path == "/install" && id.isNotBlank() && url.startsWith(PluginRepository.PACKAGE_PREFIX)
            ) {
                pendingLink = id to url
            } else {
                message = "El enlace de instalación no es válido o no pertenece al almacenamiento oficial."
                onDeepLinkHandled()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            workingId = "manual"
            runCatching { repository.importZip(uri) }
                .onSuccess { message = "Plugin ${it.manifest.name} instalado." }
                .onFailure { message = it.message ?: "No se pudo importar el ZIP." }
            installed = repository.installedPlugins()
            workingId = null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Descubre extensiones de la comunidad. Los paquetes son declarativos: Tori nunca ejecuta código descargado.",
            color = Color.White.copy(alpha = .56f), fontSize = 12.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { selectedTab = "catalog" }) { Text("Catálogo", color = if (selectedTab == "catalog") Color.White else Color.White.copy(.55f)) }
            TextButton(onClick = { selectedTab = "installed"; installed = repository.installedPlugins() }) { Text("Instalados (${installed.size})", color = if (selectedTab == "installed") Color.White else Color.White.copy(.55f)) }
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { showManualImportWarning = true }) {
                Text("Importar ZIP", fontSize = 11.sp)
            }
        }

        if (loading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
        if (selectedTab == "catalog") {
            if (!loading && catalog.isEmpty()) {
                Text("El catálogo está vacío o no se pudo conectar.", color = Color.White.copy(.6f), fontSize = 12.sp)
                TextButton(onClick = { scope.launch { refresh() } }) { Text("Volver a intentar") }
            }
            catalog.forEach { item ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B23)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = item.iconUrl, contentDescription = null, modifier = Modifier.size(52.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("${item.category} · ${item.authorName}", color = Color.White.copy(.55f), fontSize = 11.sp)
                            Text(item.description, color = Color.White.copy(.75f), fontSize = 12.sp, maxLines = 3)
                            Text("v${item.currentVersion} · ${item.downloads} descargas", color = Color.White.copy(.42f), fontSize = 10.sp)
                        }
                        Button(
                            enabled = workingId == null,
                            onClick = {
                                scope.launch {
                                    workingId = item.id
                                    runCatching { repository.installFromCatalog(item) }
                                        .onSuccess { message = "${it.manifest.name} instalado y activado." }
                                        .onFailure { message = it.message ?: "Error al instalar." }
                                    installed = repository.installedPlugins()
                                    workingId = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB8A6FF), contentColor = Color(0xFF171321))
                        ) {
                            if (workingId == item.id) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Instalar", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            if (installed.isEmpty()) Text("Todavía no tienes plugins instalados.", color = Color.White.copy(.6f), fontSize = 12.sp)
            installed.forEach { plugin ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1B23)), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(plugin.manifest.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                Text("${plugin.manifest.id} · v${plugin.manifest.version}", color = Color.White.copy(.5f), fontSize = 11.sp)
                            }
                            Switch(
                                checked = plugin.enabled,
                                onCheckedChange = { enabled ->
                                    repository.setEnabled(plugin.manifest.id, enabled)
                                    installed = repository.installedPlugins()
                                }
                            )
                        }
                        Text("Permisos: ${plugin.manifest.permissions.joinToString().ifBlank { "ninguno" }}", color = Color.White.copy(.55f), fontSize = 11.sp)
                        TextButton(onClick = {
                            repository.uninstall(plugin.manifest.id)
                            installed = repository.installedPlugins()
                            message = "${plugin.manifest.name} desinstalado."
                        }) { Text("Desinstalar", color = Color(0xFFFF9A9A)) }
                    }
                }
            }
        }
        message?.let { text ->
            Text(text, color = Color(0xFFD8D0FF), fontSize = 12.sp)
            TextButton(onClick = { message = null }) { Text("Cerrar") }
        }
    }

    if (showManualImportWarning) {
        AlertDialog(
            onDismissRequest = { showManualImportWarning = false },
            title = { Text("Importar plugin externo") },
            text = { Text("Instala únicamente ZIPs de desarrolladores en quienes confíes. Tori valida manifiesto, rutas, capacidades y tipos de archivo, y nunca ejecuta código descargado; aun así, los recursos del paquete pueden ser maliciosos o engañosos.") },
            confirmButton = {
                TextButton(onClick = {
                    showManualImportWarning = false
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("Elegir ZIP") }
            },
            dismissButton = {
                TextButton(onClick = { showManualImportWarning = false }) { Text("Cancelar") }
            }
        )
    }

    pendingLink?.let { (id, url) ->
        AlertDialog(
            onDismissRequest = { pendingLink = null; onDeepLinkHandled() },
            title = { Text("Instalar plugin externo") },
            text = { Text("¿Quieres descargar e instalar el plugin $id desde el almacenamiento oficial de Tori? Se comprobará su SHA-256 antes de instalarlo.") },
            confirmButton = {
                TextButton(onClick = {
                    val link = pendingLink
                    pendingLink = null
                    onDeepLinkHandled()
                    if (link != null) scope.launch {
                        workingId = link.first
                        runCatching { repository.installDeepLink(link.first, link.second) }
                            .onSuccess { message = "${it.manifest.name} instalado." }
                            .onFailure { message = it.message ?: "No se pudo instalar el plugin." }
                        installed = repository.installedPlugins()
                        workingId = null
                    }
                }) { Text("Instalar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLink = null; onDeepLinkHandled() }) { Text("Cancelar") }
            }
        )
    }
}
