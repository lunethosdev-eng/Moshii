package com.pokke.music.remote

import androidx.activity.ComponentActivity
import android.content.Context
import android.media.AudioManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.view.KeyEvent
import com.pokke.music.player.PlayerController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ConcurrentHashMap
import java.util.Base64

sealed interface TvRemoteCommand {
    data object Home : TvRemoteCommand
    data class Search(val value: String) : TvRemoteCommand
}

object TvRemoteBus {
    private val _commands = MutableSharedFlow<TvRemoteCommand>(extraBufferCapacity = 32)
    val commands = _commands.asSharedFlow()
    private val _searchText = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val searchText = _searchText
    fun post(command: TvRemoteCommand) { _commands.tryEmit(command) }
    fun setSearchText(value: String) { _searchText.value = value }
}

/** LAN-only control endpoint. A TV-displayed PIN exchanges for an IP-bound, short-lived session token. */
object TvRemoteServer {
    private val running = AtomicBoolean(false)
    private val workers = Executors.newCachedThreadPool()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var nsdManager: NsdManager? = null
    @Volatile private var registrationListener: NsdManager.RegistrationListener? = null
    @Volatile var accessCode: String = ""
        private set
    @Volatile var port: Int = 0
        private set
    private data class RemoteSession(val address: String, val expiresAt: Long)
    private val sessions = ConcurrentHashMap<String, RemoteSession>()
    private val failedPins = ConcurrentHashMap<String, Pair<Int, Long>>()

    fun start(activity: ComponentActivity) {
        if (!running.compareAndSet(false, true)) return
        accessCode = (SecureRandom().nextInt(90_000_000) + 10_000_000).toString()
        workers.execute {
            try {
                val socket = ServerSocket(0)
                socket.reuseAddress = true
                serverSocket = socket
                if (!running.get()) { socket.close(); serverSocket = null; return@execute }
                port = socket.localPort
                registerNsd(activity.applicationContext, port)
                while (running.get()) {
                    val client = socket.accept()
                    workers.execute { handleClient(activity, client) }
                }
            } catch (_: Exception) {
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) { }
        serverSocket = null
        registrationListener?.let { listener -> try { nsdManager?.unregisterService(listener) } catch (_: Exception) { } }
        registrationListener = null
        port = 0
        accessCode = ""
        sessions.clear()
        failedPins.clear()
    }

    private fun registerNsd(context: Context, port: Int) {
        val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = manager
        val service = NsdServiceInfo().apply {
            serviceName = "Pokke TV ${Build.MODEL.take(18)}"
            serviceType = "_pokke-remote._tcp."
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) { }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) { }
            override fun onServiceUnregistered(info: NsdServiceInfo) { }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) { }
        }
        registrationListener = listener
        try { manager.registerService(service, NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) { }
    }

    private fun handleClient(activity: ComponentActivity, socket: Socket) {
        socket.use { client ->
            client.soTimeout = 5000
            val reader = BufferedReader(InputStreamReader(client.getInputStream()))
            val writer = PrintWriter(client.getOutputStream(), true)
            val request = runCatching { reader.readLine()?.let { JSONObject(it) } }.getOrNull()
            if (request == null) { writer.println("{\"ok\":false,\"error\":\"bad_request\"}"); return }
            if (!client.inetAddress.isSiteLocalAddress && !client.inetAddress.isLinkLocalAddress) {
                writer.println("{\"ok\":false,\"error\":\"local_network_only\"}")
                return
            }
            val action = request.optString("action")
            val remoteAddress = client.inetAddress.hostAddress.orEmpty()
            val now = System.currentTimeMillis()
            val failed = failedPins[remoteAddress]?.takeIf { it.second == 0L || it.second > now }
            if (failed != null && failed.second > now) {
                writer.println("{\"ok\":false,\"error\":\"temporarily_locked\"}")
                return
            }
            if (action == "auth") {
                val supplied = request.optString("code")
                if (!constantTimeEquals(accessCode, supplied)) {
                    val next = (failed?.first ?: 0) + 1
                    failedPins[remoteAddress] = next to if (next >= 6) System.currentTimeMillis() + 60_000L else 0L
                    writer.println("{\"ok\":false,\"error\":\"unauthorized\"}")
                    return
                }
                failedPins.remove(remoteAddress)
                val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
                sessions[token] = RemoteSession(remoteAddress, System.currentTimeMillis() + 10 * 60 * 1000L)
                writer.println(JSONObject().put("ok", true).put("token", token).toString())
                return
            }
            val token = request.optString("token")
            val session = sessions[token]
            if (session == null || session.address != remoteAddress || session.expiresAt < System.currentTimeMillis()) {
                sessions.remove(token)
                writer.println("{\"ok\":false,\"error\":\"unauthorized\"}")
                return
            }
            val payload = request.optString("value")
            val accepted = when (action) {
                "up" -> dispatch(activity, KeyEvent.KEYCODE_DPAD_UP)
                "down" -> dispatch(activity, KeyEvent.KEYCODE_DPAD_DOWN)
                "left" -> dispatch(activity, KeyEvent.KEYCODE_DPAD_LEFT)
                "right" -> dispatch(activity, KeyEvent.KEYCODE_DPAD_RIGHT)
                "select" -> dispatch(activity, KeyEvent.KEYCODE_DPAD_CENTER)
                "back" -> onUi(activity) { activity.onBackPressedDispatcher.onBackPressed() }
                "home" -> { activity.runOnUiThread { TvRemoteBus.post(TvRemoteCommand.Home) }; true }
                "search" -> { activity.runOnUiThread { TvRemoteBus.setSearchText(""); TvRemoteBus.post(TvRemoteCommand.Search("")) }; true }
                "play_pause" -> onUi(activity) { PlayerController.togglePlayPause() }
                "next" -> onUi(activity) { PlayerController.playNext() }
                "previous" -> onUi(activity) { PlayerController.playPrevious() }
                "volume_up" -> if (isVolumeFixed(activity)) false else onUi(activity) { adjustVolume(activity, AudioManager.ADJUST_RAISE) }
                "volume_down" -> if (isVolumeFixed(activity)) false else onUi(activity) { adjustVolume(activity, AudioManager.ADJUST_LOWER) }
                "mute" -> if (isVolumeFixed(activity)) false else onUi(activity) { adjustVolume(activity, AudioManager.ADJUST_MUTE) }
                "text" -> {
                    val safe = payload.take(120)
                    activity.runOnUiThread { TvRemoteBus.setSearchText(safe); TvRemoteBus.post(TvRemoteCommand.Search(safe)) }
                    true
                }
                else -> false
            }
            writer.println(JSONObject().put("ok", accepted).put("error", if (accepted) "" else "unsupported").toString())
        }
    }

    private fun dispatch(activity: ComponentActivity, keyCode: Int): Boolean {
        activity.runOnUiThread {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return true
    }

    private fun onUi(activity: ComponentActivity, action: () -> Unit): Boolean {
        activity.runOnUiThread { action() }
        return true
    }

    private fun isVolumeFixed(activity: ComponentActivity): Boolean =
        (activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager).isVolumeFixed

    private fun adjustVolume(activity: ComponentActivity, direction: Int) {
        val audio = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audio.isVolumeFixed) return
        if (direction == AudioManager.ADJUST_MUTE) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        } else audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    }

    private fun constantTimeEquals(expected: String, actual: String): Boolean {
        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), actual.toByteArray(Charsets.UTF_8))
    }
}
