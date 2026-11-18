package com.pokke.music.remote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

 data class DiscoveredTv(val name: String, val host: InetAddress, val port: Int)

class PhoneRemoteDiscovery(context: Context) {
    private val manager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var listener: NsdManager.DiscoveryListener? = null
    private val resolving = ConcurrentHashMap.newKeySet<String>()

    fun start(onDevice: (DiscoveredTv) -> Unit, onError: (String) -> Unit = {}) {
        stop()
        val callback = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) { }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!resolving.add(service.serviceName)) return
                try {
                    manager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) { resolving.remove(info.serviceName) }
                        override fun onServiceResolved(info: NsdServiceInfo) {
                            resolving.remove(info.serviceName)
                            val host = info.host ?: return
                            if (info.port > 0) onDevice(DiscoveredTv(info.serviceName, host, info.port))
                        }
                    })
                } catch (_: Exception) { resolving.remove(service.serviceName) }
            }
            override fun onServiceLost(service: NsdServiceInfo) { resolving.remove(service.serviceName) }
            override fun onDiscoveryStopped(serviceType: String) { }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { onError("No se pudo buscar TVs en esta red ($errorCode)"); stop() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) { onError("No se pudo detener la búsqueda ($errorCode)") }
        }
        listener = callback
        try { manager.discoverServices("_pokke-remote._tcp.", NsdManager.PROTOCOL_DNS_SD, callback) }
        catch (e: Exception) { onError(e.message ?: "No se pudo iniciar la búsqueda") }
    }

    fun stop() {
        listener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        listener = null
        resolving.clear()
    }
}

object PhoneRemoteClient {
    fun authenticate(device: DiscoveredTv, code: String): Result<String> = runCatching {
        val response = request(device, JSONObject().put("action", "auth").put("code", code.trim()))
        response.optString("token").takeIf { it.isNotBlank() } ?: error("La TV no entregó una sesión de control")
    }

    fun send(device: DiscoveredTv, token: String, action: String, value: String = ""): Result<Boolean> = runCatching {
        val response = request(device, JSONObject().put("token", token).put("action", action).put("value", value))
        if (!response.optBoolean("ok")) {
            val reason = response.optString("error")
            if (reason == "unauthorized") error("La sesión de control expiró. Vuelve a conectar.")
            error("La TV rechazó el comando ($reason)")
        }
        true
    }

    private fun request(device: DiscoveredTv, body: JSONObject): JSONObject {
        Socket().use { socket ->
            socket.connect(java.net.InetSocketAddress(device.host, device.port), 2500)
            socket.soTimeout = 3000
            val writer = socket.getOutputStream().bufferedWriter()
            val reader = socket.getInputStream().bufferedReader()
            writer.write(body.toString()); writer.newLine(); writer.flush()
            val response = reader.readLine()?.let { JSONObject(it) } ?: error("La TV no respondió")
            if (!response.optBoolean("ok")) {
                val reason = response.optString("error")
                if (reason == "unauthorized") error("Código incorrecto o sesión expirada. Vuelve a conectar.")
                if (reason == "temporarily_locked") error("Demasiados intentos. Espera un minuto.")
            }
            return response
        }
    }
}
