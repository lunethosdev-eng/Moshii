package com.pokke.music.data.repository

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts Supabase access/refresh tokens with a key held by Android Keystore. */
internal class SecureSessionStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pokke_secure_auth", Context.MODE_PRIVATE)

    fun save(session: AuthRepository.AuthSession) {
        val json = JSONObject().apply {
            put("access_token", session.accessToken)
            put("refresh_token", session.refreshToken)
            put("user_id", session.userId)
            put("email", session.email)
            put("expires_at", session.expiresAt)
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(json)
        val payload = cipher.iv + encrypted
        prefs.edit().putString("session", Base64.encodeToString(payload, Base64.NO_WRAP)).apply()
    }

    fun read(): AuthRepository.AuthSession? {
        return runCatching {
        val encoded = prefs.getString("session", null) ?: return@runCatching null
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12)
        val iv = payload.copyOfRange(0, 12)
        val encrypted = payload.copyOfRange(12, payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        val json = JSONObject(String(cipher.doFinal(encrypted), Charsets.UTF_8))
        AuthRepository.AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.optString("refresh_token").takeIf(String::isNotBlank),
            userId = json.getString("user_id"),
            email = json.optString("email").takeIf(String::isNotBlank),
            expiresAt = json.optLong("expires_at", 0L)
        )
        }.getOrNull()
    }

    fun clear() { prefs.edit().remove("session").apply() }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object { const val KEY_ALIAS = "pokke_supabase_session_v1" }
}
