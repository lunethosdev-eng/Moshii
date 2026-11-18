package com.pokke.music.ui.social

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.data.repository.SocialRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Free open map via Leaflet + OSM tiles inside WebView.
 * No Google / no MapLibre native SDK (avoids crash if SDK not initialized).
 * Location share is OFF until the user grants permission AND picks a mode.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FriendsMapScreen(authRepository: AuthRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val social = remember(authRepository) { SocialRepository(authRepository) }
    val scope = rememberCoroutineScope()
    var locations by remember { mutableStateOf<List<SocialRepository.FriendLocation>>(emptyList()) }
    var shareMode by remember { mutableStateOf("none") }
    var shareMusic by remember { mutableStateOf(false) }
    var shareBattery by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var privacyExpanded by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val ok = granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (!ok) {
            shareMode = "none"
            error = "Necesitas permiso de ubicación para compartir"
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    fun refresh() {
        scope.launch {
            social.friendLocations()
                .onSuccess {
                    locations = it
                    // push markers to leaflet
                    val js = buildString {
                        append("if(window.clearMarkers)clearMarkers();")
                        it.forEach { loc ->
                            if (loc.lat != 0.0 || loc.lng != 0.0) {
                                val name = (loc.displayName ?: loc.username ?: "Amigo")
                                    .replace("'", "\\'")
                                append("if(window.addFriendMarker)addFriendMarker(${loc.lat},${loc.lng},'$name');")
                            }
                        }
                    }
                    webView?.evaluateJavascript(js, null)
                }
                .onFailure { error = it.message }
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
        refresh()
        while (true) {
            delay(20_000)
            refresh()
        }
    }

    val html = remember {
        """
        <!DOCTYPE html><html><head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
        <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
        <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
        <style>html,body,#map{margin:0;height:100%;background:#111} .leaflet-control-attribution{font-size:9px;opacity:.6}</style>
        </head><body>
        <div id="map"></div>
        <script>
          var map = L.map('map', {zoomControl:true}).setView([13.6929,-89.2182], 12);
          L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '© OpenStreetMap'
          }).addTo(map);
          var markers = [];
          function clearMarkers(){ markers.forEach(m=>map.removeLayer(m)); markers=[]; }
          function addFriendMarker(lat,lng,name){
            var m = L.marker([lat,lng]).addTo(map).bindPopup(name);
            markers.push(m);
          }
          window.clearMarkers = clearMarkers;
          window.addFriendMarker = addFriendMarker;
        </script>
        </body></html>
        """.trimIndent()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B))
            .statusBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text("Mapas con amigos", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("OpenStreetMap · gratis · ubicación off por defecto", color = Color.White.copy(.5f), fontSize = 12.sp)
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .padding(horizontal = 12.dp)
                .background(Color(0xFF121218), RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://unpkg.com/", html, "text/html", "UTF-8", null)
                        webView = this
                    }
                }
            )
        }

        Card(
            Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161A)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
            Modifier
                .fillMaxWidth()
                .clickable { privacyExpanded = !privacyExpanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Privacidad", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(if (privacyExpanded) "Ocultar" else "Mostrar", color = Color.White.copy(.5f), fontSize = 12.sp)
        }
        if (privacyExpanded) {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .background(Color(0xFF1C1C22), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                listOf(
                    "none" to "No compartir",
                    "all_friends" to "Todos mis amigos",
                    "selected" to "Solo seleccionados"
                ).forEach { (id, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = shareMode == id,
                            onClick = {
                                if (id != "none" && !hasLocationPermission()) {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                                shareMode = id
                                scope.launch {
                                    if (id == "none") social.stopSharingLocation()
                                }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFE5C17A))
                        )
                        Text(label, color = Color.White, fontSize = 14.sp)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(shareMusic, { shareMusic = it }, enabled = shareMode != "none")
                    Text("Compartir música", color = Color.White.copy(.8f), fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(shareBattery, { shareBattery = it }, enabled = shareMode != "none")
                    Text("Compartir batería", color = Color.White.copy(.8f), fontSize = 13.sp)
                }
            }
        }

        error?.let {
            Text(it, color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Text(
            "Amigos visibles (${locations.size})",
            color = Color.White.copy(.65f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(locations, key = { it.userId }) { loc ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .background(Color(0xFF16161A), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(loc.displayName ?: loc.username ?: loc.userId.take(8), color = Color.White, fontWeight = FontWeight.Medium)
                    Text("lat ${"%.4f".format(loc.lat)} · lng ${"%.4f".format(loc.lng)}", color = Color.White.copy(.55f), fontSize = 12.sp)
                }
            }
        }
    }
}
}
}
