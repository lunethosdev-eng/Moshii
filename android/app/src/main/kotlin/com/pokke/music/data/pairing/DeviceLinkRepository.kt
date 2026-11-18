package com.pokke.music.data.pairing

import com.pokke.music.data.remote.KokoroConfig
import com.pokke.music.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID

/** Supabase-backed, short-lived TV pairing. TV receives a capability secret; account actions require the owner's JWT. */
class DeviceLinkRepository(private val auth: AuthRepository? = null) {
    data class TvPairing(val id: String, val code: String, val pin: String, val secret: String, val expiresAt: Long)
    data class LinkedDevice(val id: String, val name: String, val kind: String, val createdAt: String, val isPrimary: Boolean, val trusted: Boolean = false, val canCloseOtherSessions: Boolean = false)
    data class SecurityEvent(val id: String, val eventType: String, val deviceName: String, val details: String, val createdAt: String)

    suspend fun createTvPairing(deviceName: String): Result<TvPairing> = withContext(Dispatchers.IO) { runCatching {
        val code = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
        val pin = (100000..999999).random().toString()
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val response = rpc("create_tv_pairing", JSONObject().put("p_code", code).put("p_pin", pin).put("p_secret", secret).put("p_device_name", deviceName), null)
        val row = response.optJSONObject(0) ?: throw IOException("Supabase no devolvió la solicitud de vinculación")
        TvPairing(row.getString("id"), code, pin, secret, row.getLong("expires_at"))
    } }

    suspend fun pollTvPairing(pairing: TvPairing): Result<Boolean> = withContext(Dispatchers.IO) { runCatching {
        val result = rpc("poll_tv_pairing", JSONObject().put("p_id", pairing.id).put("p_secret", pairing.secret), null)
        result.optJSONObject(0)?.optBoolean("approved", false) ?: false
    } }

    suspend fun approveTvPairing(code: String, pin: String, phoneName: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión en Pokke antes de vincular dispositivos")
        rpc("approve_tv_pairing", JSONObject().put("p_code", code.trim().uppercase()).put("p_pin", pin.trim()).put("p_phone_name", phoneName), session.accessToken)
        Unit
    } }

    suspend fun linkedDevices(): Result<List<LinkedDevice>> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para administrar tus dispositivos")
        val rows = JSONArray(rest("/rest/v1/pokke_linked_devices?select=id,device_name,device_kind,created_at,is_primary,is_trusted,can_close_other_sessions&revoked_at=is.null&order=created_at.asc", "GET", null, session.accessToken))
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { LinkedDevice(it.getString("id"), it.optString("device_name"), it.optString("device_kind"), it.optString("created_at"), it.optBoolean("is_primary"), it.optBoolean("is_trusted"), it.optBoolean("can_close_other_sessions")) } }
    } }

    suspend fun blockDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para administrar tus dispositivos")
        rpc("block_pokke_device", JSONObject().put("p_device_id", deviceId), session.accessToken)
        Unit
    } }

    suspend fun revokeDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para administrar tus dispositivos")
        rpc("revoke_pokke_device", JSONObject().put("p_device_id", deviceId), session.accessToken)
        Unit
    } }


    suspend fun tvCloseOtherSessions(deviceSecret: String): Result<Int> = withContext(Dispatchers.IO) { runCatching {
        val result = rpc("tv_close_other_sessions", JSONObject().put("p_device_secret", deviceSecret), null)
        result.optJSONObject(0)?.optInt("closed_sessions", 0) ?: 0
    } }

    suspend fun updateDevicePermissions(deviceId: String, trusted: Boolean, canCloseOtherSessions: Boolean): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para administrar permisos")
        rpc("set_pokke_device_permissions", JSONObject().put("p_device_id", deviceId).put("p_is_trusted", trusted).put("p_can_close_other_sessions", canCloseOtherSessions), session.accessToken)
        Unit
    } }


    suspend fun blockedDevices(): Result<List<LinkedDevice>> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para consultar la lista de bloqueo")
        val rows = JSONArray(rest("/rest/v1/pokke_linked_devices?select=id,device_name,device_kind,created_at,is_primary,is_trusted,can_close_other_sessions&blocked_at=not.is.null&order=blocked_at.desc", "GET", null, session.accessToken))
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { LinkedDevice(it.getString("id"), it.optString("device_name"), it.optString("device_kind"), it.optString("created_at"), it.optBoolean("is_primary"), it.optBoolean("is_trusted"), it.optBoolean("can_close_other_sessions")) } }
    } }

    suspend fun unblockDevice(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para administrar la lista de bloqueo")
        rpc("unblock_pokke_device", JSONObject().put("p_device_id", deviceId), session.accessToken)
        Unit
    } }

    suspend fun securityHistory(): Result<List<SecurityEvent>> = withContext(Dispatchers.IO) { runCatching {
        val session = auth?.session?.value ?: throw IOException("Inicia sesión para consultar el historial")
        val rows = JSONArray(rest("/rest/v1/pokke_device_security_events?select=id,event_type,device_name,details,created_at&order=created_at.desc&limit=30", "GET", null, session.accessToken))
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { SecurityEvent(it.getString("id"), it.optString("event_type"), it.optString("device_name"), it.optString("details"), it.optString("created_at")) } }
    } }

    private fun rpc(name: String, body: JSONObject, token: String?): JSONArray = JSONArray(rest("/rest/v1/rpc/$name", "POST", body.toString(), token))
    private fun rest(path: String, method: String, body: String?, token: String?): String {
        val c = (URL(KokoroConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 12000; readTimeout = 15000
            setRequestProperty("apikey", KokoroConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${token ?: KokoroConfig.SUPABASE_ANON_KEY}")
            setRequestProperty("Accept", "application/json")
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray()) }
            val status = c.responseCode
            val response = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw IOException("Supabase HTTP $status: ${response.take(240)}")
            return response
        } finally { c.disconnect() }
    }
}
