package com.pokke.music.plugins

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var catalog by remember { mutableStateOf<List<PluginCatalogItem>>(emptyList()) }
    var installed by remember { mutableStateOf(ToriPluginInstaller.listInstalled(context)) }
    var candidate by remember { mutableStateOf<ToriPluginInstaller.InspectedPackage?>(null) }
    var sourceLabel by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refreshInstalled() { installed = ToriPluginInstaller.listInstalled(context) }
    fun refreshCatalog() {
        scope.launch {
            loading = true
            ToriPluginCatalogRepository.fetchCatalog()
                .onSuccess { catalog = it; message = null }
                .onFailure { message = it.localizedMessage ?: "No se pudo cargar el catálogo." }
            loading = false
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching { ToriPluginInstaller.inspect(context, uri) }
                    .onSuccess {
                        candidate = it
                        sourceLabel = "Archivo ZIP local"
                    }
                    .onFailure { message = it.localizedMessage ?: "El paquete no es válido." }
            }
        }
    }

    LaunchedEffect(Unit) { refreshCatalog() }

    Scaffold(
        containerColor = Color(0xFF09090B),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Tienda de plugins", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Extensiones de la comunidad para Tori", color = Color.White.copy(.55f), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = ::refreshCatalog) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar catálogo", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF17171C)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Extension, null, tint = Color(0xFFB8A7FF))
                            Text("Descubre nuevas extensiones", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 9.dp))
                        }
                        Text(
                            "Temas, widgets, funciones y fuentes musicales creadas por la comunidad. Los paquetes se validan antes de instalarse.",
                            color = Color.White.copy(.62f),
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.UploadFile, null)
                            Text("Instalar desde ZIP", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CATÁLOGO", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (loading) CircularProgressIndicator()
                }
            }
            if (catalog.isEmpty()) {
                item {
                    Text(
                        if (ToriPluginCatalogRepository.isConfigured())
                            "Todavía no hay plugins publicados o no se pudo conectar. Puedes instalar un ZIP de confianza."
                        else
                            "El catálogo online necesita la URL y la clave pública de tu proyecto Supabase. La instalación manual por ZIP ya está disponible.",
                        color = Color.White.copy(.55f),
                        fontSize = 12.sp
                    )
                }
            }
            items(catalog, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171C))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("${item.category} · v${item.version} · ${item.author}", color = Color(0xFFB8A7FF), fontSize = 11.sp)
                        Text(item.description, color = Color.White.copy(.65f), fontSize = 12.sp)
                        Text("Descargas: ${item.downloads}", color = Color.White.copy(.4f), fontSize = 10.sp)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    loading = true
                                    ToriPluginCatalogRepository.fetchPackage(item)
                                        .onSuccess {
                                            candidate = it
                                            sourceLabel = "Catálogo de Tori"
                                        }
                                        .onFailure { message = it.localizedMessage ?: "No se pudo descargar el plugin." }
                                    loading = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !loading
                        ) { Text("Descargar e instalar") }
                    }
                }
            }
            item { Text("INSTALADOS", color = Color.White, fontWeight = FontWeight.Bold) }
            if (installed.isEmpty()) {
                item { Text("Todavía no has instalado plugins.", color = Color.White.copy(.5f), fontSize = 12.sp) }
            }
            items(installed, key = { it.manifest.id }) { plugin ->
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF17171C))) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(plugin.manifest.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text("v${plugin.manifest.version} · ${plugin.manifest.author}", color = Color.White.copy(.5f), fontSize = 11.sp)
                            Text("Permisos: ${plugin.grantedPermissions.size}", color = Color.White.copy(.45f), fontSize = 10.sp)
                        }
                        TextButton(onClick = {
                            runCatching { ToriPluginInstaller.uninstall(context, plugin.manifest.id) }
                                .onSuccess { refreshInstalled() }
                                .onFailure { message = it.localizedMessage }
                        }) { Text("Desinstalar") }
                    }
                }
            }
            message?.let { error ->
                item { Text(error, color = Color(0xFFFF9A9A), fontSize = 12.sp) }
            }
        }
    }

    candidate?.let { inspected ->
        val requested = inspected.manifest.permissions
        AlertDialog(
            onDismissRequest = { candidate = null },
            containerColor = Color(0xFF202026),
            title = { Text("Permisos del plugin", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${inspected.manifest.name} · v${inspected.manifest.version}", color = Color.White)
                    Text(sourceLabel, color = Color.White.copy(.5f), fontSize = 11.sp)
                    Text(
                        inspected.manifest.description.ifBlank { "Este plugin no incluye descripción." },
                        color = Color.White.copy(.7f),
                        fontSize = 12.sp
                    )
                    if (requested.isEmpty()) Text("No solicita permisos adicionales.", color = Color.White.copy(.7f))
                    else {
                        Text("Solicita acceso a:", color = Color.White, fontWeight = FontWeight.SemiBold)
                        requested.forEach { permission ->
                            Text("• ${permissionLabel(permission)}", color = Color.White.copy(.75f), fontSize = 12.sp)
                        }
                    }
                    Text(
                        "Por seguridad, los ZIP no pueden instalar APK, DEX, bibliotecas nativas ni scripts ejecutables. Los permisos Android del sistema solo pueden concederse si Tori ya los declara en su propia versión.",
                        color = Color(0xFFFFD18A),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching { ToriPluginInstaller.install(context, inspected, requested.toSet()) }
                        .onSuccess { candidate = null; refreshInstalled(); message = "Plugin instalado. Reinicia Tori para activar permisos nuevos." }
                        .onFailure { message = it.localizedMessage ?: "No se pudo instalar el plugin." }
                }) { Text("Aceptar e instalar") }
            },
            dismissButton = {
                TextButton(onClick = { candidate = null }) { Text("Cancelar") }
            }
        )
    }
}

private fun permissionLabel(permission: String): String = when (permission) {
    "ui.theme" -> "Cambiar temas y colores"
    "ui.home_widgets" -> "Añadir tarjetas al inicio"
    "ui.player_overlay" -> "Añadir componentes al reproductor"
    "catalog.music_source" -> "Registrar un catálogo musical"
    "player.queue.read" -> "Leer la cola de reproducción"
    "player.queue.write" -> "Modificar la cola de reproducción"
    "player.metadata.read" -> "Leer metadatos de la canción actual"
    "network.http" -> "Conectarse a servidores HTTPS"
    else -> permission
}
