package com.kiddotime.app.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class PinRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kiddotime_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun savePin(pin: String) {
        prefs.edit().putString("parent_pin", pin).apply()
    }

    fun getPin(): String? {
        return prefs.getString("parent_pin", null)
    }

    fun hasPin(): Boolean {
        return getPin() != null
    }

    fun verifyPin(input: String): Boolean {
        return getPin() == input
    }

    fun clearPin() {
        prefs.edit().remove("parent_pin").apply()
    }
}