package com.pokke.music.ui.sheets

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pokke.music.ui.liquid.LocalAppBackdrop
import com.pokke.music.ui.backdrop.drawBackdrop
import com.pokke.music.ui.backdrop.effects.blur
import com.pokke.music.ui.backdrop.effects.lens
import com.pokke.music.ui.backdrop.effects.vibrancy
import kotlinx.coroutines.delay

private data class OutDevice(val id: Int, val name: String, val type: String, val isActive: Boolean)

@Composable
fun AudioOutputSheet(onDismiss: () -> Unit, glass: Boolean = true) {
    val context = LocalContext.current
    val backdrop = LocalAppBackdrop.current
    var devices by remember { mutableStateOf<List<OutDevice>>(emptyList()) }
    var activeId by remember { mutableStateOf(-1) }

    fun refresh() {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val list = mutableListOf<OutDevice>()
        if (Build.VERSION.SDK_INT >= 23) {
            val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            outs.forEach { d ->
                val type = when (d.type) {
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "speaker"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bt"
                    AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "usb"
                    else -> "other"
                }
                if (type == "other") return@forEach
                val name = d.productName?.toString()?.ifBlank { null }
                    ?: when (type) {
                        "speaker" -> "Altavoz del teléfono"
                        "wired" -> "Auriculares cableados"
                        "bt" -> "Bluetooth"
                        "usb" -> "USB"
                        else -> "Salida de audio"
                    }
                list.add(OutDevice(d.id, name, type, false))
            }
        }
        if (list.none { it.type == "speaker" }) {
            list.add(0, OutDevice(-1, "Altavoz del teléfono", "speaker", true))
        }
        // Prefer wired/bt as active hint
        val preferred = list.firstOrNull { it.type == "wired" || it.type == "bt" || it.type == "usb" }
        activeId = preferred?.id ?: list.firstOrNull()?.id ?: -1
        devices = list.distinctBy { it.name }
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(2500)
            refresh()
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(if (glass && backdrop != null) Modifier.drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(28.dp) },
                effects = { vibrancy(); blur(22f.dp.toPx()); lens(18f.dp.toPx(), 24f.dp.toPx()) },
                onDrawSurface = { drawRect(Color(0xB31A1A1D)) }
            ) else Modifier.background(Color(0xFF17171B), RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)))
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .navigationBarsPadding()
    ) {
        Text("Salida de audio", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Se detectan auriculares automáticamente", color = Color.White.copy(.5f), fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        devices.forEach { d ->
            val selected = d.id == activeId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) (if (glass) Color.White.copy(.16f) else Color(0xFF303037)) else Color.Transparent)
                    .clickable { activeId = d.id }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (d.type) {
                        "speaker" -> Icons.Default.PhoneAndroid
                        "wired" -> Icons.Default.Headset
                        "bt" -> Icons.Default.Bluetooth
                        else -> Icons.Default.Speaker
                    },
                    null,
                    tint = if (selected) Color.White else Color.White.copy(.7f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Text(d.name, color = Color.White, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Default.Check, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}
