package com.qxdnzbl.lumichat

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureConfigStore {
    private const val STORE = "lumi_secure_config"
    private const val KEY_ALIAS = "lumi_api_key_aes"
    private const val KEY_CIPHER = "api_key_cipher"
    private const val KEY_IV = "api_key_iv"

    fun hasApiKey(context: Context): Boolean = loadApiKey(context).isNotBlank()

    fun saveApiKey(context: Context, apiKey: String) {
        val clean = apiKey.trim()
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (clean.isEmpty()) {
            prefs.edit().remove(KEY_CIPHER).remove(KEY_IV).apply()
            return
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHER, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun loadApiKey(context: Context): String {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_CIPHER, null) ?: return ""
        val iv = prefs.getString(KEY_IV, null) ?: return ""

        return runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
            )
            String(
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                Charsets.UTF_8
            )
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }
}
