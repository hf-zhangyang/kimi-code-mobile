package com.kimicode.pocket

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PairingStore(context: Context) {
    private val appContext = context.applicationContext
    private val legacyPreferences = appContext.getSharedPreferences(
        LEGACY_FILE_NAME,
        Context.MODE_PRIVATE
    )
    private val encryptedPreferences: SharedPreferences? = createEncryptedPreferences()

    fun getUrl(): String? {
        val encrypted = encryptedPreferences
        if (encrypted == null) {
            return legacyPreferences.getString(KEY_URL, null)
        }
        val stored = encrypted.getString(KEY_URL, null)
        if (stored != null) {
            clearLegacyValue()
            return stored
        }
        val legacy = legacyPreferences.getString(KEY_URL, null) ?: return null
        if (encrypted.edit().putString(KEY_URL, legacy).commit()) {
            clearLegacyValue()
        }
        return legacy
    }

    fun saveUrl(url: String) {
        val encrypted = encryptedPreferences
        if (encrypted == null) {
            legacyPreferences.edit().putString(KEY_URL, url).apply()
            return
        }
        encrypted.edit().putString(KEY_URL, url).apply()
        clearLegacyValue()
    }

    fun clear() {
        encryptedPreferences?.edit()?.clear()?.apply()
        legacyPreferences.edit().clear().apply()
    }

    private fun clearLegacyValue() {
        if (legacyPreferences.contains(KEY_URL)) {
            legacyPreferences.edit().remove(KEY_URL).apply()
        }
    }

    private fun createEncryptedPreferences(): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                ENCRYPTED_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (error: Exception) {
            Log.e(TAG, "无法初始化加密配对存储，回退到旧版存储", error)
            null
        }
    }

    private companion object {
        const val KEY_URL = "remote_url"
        const val LEGACY_FILE_NAME = "desktop_pairing"
        const val ENCRYPTED_FILE_NAME = "desktop_pairing_secure"
        const val TAG = "KimiPocket"
    }
}
