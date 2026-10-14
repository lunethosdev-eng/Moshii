package com.example.pokke

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

object PokkeChannels {
    fun register(engine: FlutterEngine, context: Context) {
        val messenger = engine.dartExecutor.binaryMessenger

        MethodChannel(messenger, "pokke/cast").setMethodCallHandler { call, result ->
            when (call.method) {
                "listDevices" -> result.success(emptyList<Map<String, String>>())
                "castMedia" -> result.success(false)
                "showCastPicker" -> {
                    try {
                        val intent = Intent(Settings.ACTION_CAST_SETTINGS)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        result.success(true)
                    } catch (e: Exception) {
                        result.success(false)
                    }
                }
                else -> result.notImplemented()
            }
        }

        EventChannel(messenger, "pokke/audio_route").setStreamHandler(object : EventChannel.StreamHandler {
            var callback: AudioDeviceCallback? = null
            var audioManager: AudioManager? = null
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && events != null) {
                    callback = object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                            for (d in addedDevices) {
                                if (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                    d.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                    d.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                    d.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                                    d.type == AudioDeviceInfo.TYPE_USB_HEADSET
                                ) {
                                    val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                                        d.productName?.toString() ?: "Auriculares"
                                    else "Auriculares"
                                    events.success(name)
                                }
                            }
                        }
                        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {}
                    }
                    audioManager?.registerAudioDeviceCallback(callback!!, null)
                }
            }
            override fun onCancel(arguments: Any?) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && callback != null) {
                    audioManager?.unregisterAudioDeviceCallback(callback!!)
                }
            }
        })
    }
}
