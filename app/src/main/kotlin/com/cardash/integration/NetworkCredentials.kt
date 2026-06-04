package com.cardash.integration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure storage for ESP32 WiFi credentials
 */
class NetworkCredentials(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create encrypted prefs, falling back to regular", e)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "NetworkCredentials"
        private const val PREFS_NAME = "esp32_credentials"
        private const val KEY_SSID = "ssid"
        private const val KEY_PASSWORD = "password"
        private const val KEY_HAS_CUSTOM = "has_custom"
    }

    /**
     * Save custom ESP32 network credentials
     */
    fun saveCredentials(ssid: String, password: String) {
        prefs.edit().apply {
            putString(KEY_SSID, ssid)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_HAS_CUSTOM, true)
            apply()
        }
        Log.d(TAG, "Saved credentials for: $ssid")
    }

    /**
     * Get saved SSID
     */
    fun getSsid(): String? {
        return prefs.getString(KEY_SSID, null)
    }

    /**
     * Get saved password
     */
    fun getPassword(): String? {
        return prefs.getString(KEY_PASSWORD, null)
    }

    /**
     * Check if custom credentials are configured
     */
    fun hasCustomCredentials(): Boolean {
        return prefs.getBoolean(KEY_HAS_CUSTOM, false) &&
               !getSsid().isNullOrEmpty() &&
               !getPassword().isNullOrEmpty()
    }

    /**
     * Clear saved credentials
     */
    fun clearCredentials() {
        prefs.edit().clear().apply()
        Log.d(TAG, "Cleared all credentials")
    }

    /**
     * Get both credentials as a pair
     */
    fun getCredentials(): Pair<String, String>? {
        val ssid = getSsid()
        val password = getPassword()
        return if (!ssid.isNullOrEmpty() && !password.isNullOrEmpty()) {
            Pair(ssid, password)
        } else {
            null
        }
    }
}
