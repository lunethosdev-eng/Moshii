package com.pokke.music.ui.pairing

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.data.pairing.DeviceLinkRepository
import com.pokke.music.data.repository.AuthRepository
import com.pokke.music.ui.theme.PokkeTheme
import kotlinx.coroutines.launch

class PairDeviceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val data = intent?.data
        setContent {
            PokkeTheme {
                val auth = remember { AuthRepository(this@PairDeviceActivity) }
                val repository = remember { DeviceLinkRepository(auth) }
                val scope = rememberCoroutineScope()
                var code by remember { mutableStateOf(data?.getQueryParameter("code").orEmpty()) }
                var pin by remember { mutableStateOf(data?.getQueryParameter("pin").orEmpty()) }
                var status by remember { mutableStateOf<String?>(null) }
                var busy by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { auth.restoreSession() }
                Column(Modifier.fillMaxSize().background(Color(0xFF0B0B10)).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Vincular Android TV", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Confirma el código que aparece en tu televisión.", color = Color.LightGray)
                    Spacer(Modifier.height(18.dp))
                    OutlinedTextField(code, { code = it.uppercase().take(12) }, label = { Text("Código") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(pin, { pin = it.filter(Char::isDigit).take(6) }, label = { Text("PIN de 6 dígitos") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                    Button(enabled = !busy && code.length == 12 && pin.length == 6, onClick = {
                        busy = true
                        scope.launch {
                            val result = repository.approveTvPairing(code, pin, android.os.Build.MODEL ?: "Pokke phone")
                            busy = false
                            result.onSuccess { status = "TV vinculada correctamente ✓"; Toast.makeText(this@PairDeviceActivity, status, Toast.LENGTH_LONG).show(); finish() }
                                .onFailure { status = it.message ?: "No se pudo vincular" }
                        }
                    }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) { Text(if (busy) "Vinculando…" else "Autorizar esta TV") }
                    status?.let { Spacer(Modifier.height(12.dp)); Text(it, color = if (it.contains("correctamente")) Color(0xFF9BE7B0) else Color(0xFFFF8A80)) }
                }
            }
        }
    }
}
