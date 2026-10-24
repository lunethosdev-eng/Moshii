package com.pokke.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ryanheise.audioservice.AudioServiceActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : AudioServiceActivity() {
    private val audioChannel = "com.pokke.app/audio_output"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, audioChannel)
            .setMethodCallHandler { call, result ->
                if (call.method == "getAudioOutput") {
                    result.success(currentOutput())
                } else {
                    result.notImplemented()
                }
            }
    }

    private fun currentOutput(): Map<String, String> {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            // Prefer Bluetooth, then wired, then built-in speaker
            val bt = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                    (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
            val wired = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            }
            val chosen = bt ?: wired
            if (chosen != null) {
                val name = chosen.productName?.toString()?.trim().orEmpty()
                val type = when (chosen.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "bluetooth"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "wired"
                    else -> "other"
                }
                return mapOf(
                    "name" to if (name.isNotEmpty()) name else if (type == "bluetooth") "Bluetooth" else "Auriculares",
                    "type" to type
                )
            }
        }
        // Fallback pre-M / speaker
        @Suppress("DEPRECATION")
        if (am.isBluetoothA2dpOn) {
            return mapOf("name" to "Bluetooth", "type" to "bluetooth")
        }
        @Suppress("DEPRECATION")
        if (am.isWiredHeadsetOn) {
            return mapOf("name" to "Auriculares", "type" to "wired")
        }
        return mapOf("name" to "Este teléfono", "type" to "speaker")
    }
}
