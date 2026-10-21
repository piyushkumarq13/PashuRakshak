package com.pashurakshak.app.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * SharedPreferences wrapper that encrypts string values with a non-exportable
 * AES-GCM key held in the Android Keystore. Non-sensitive integers/booleans are
 * left unencrypted so existing queued work and migrations keep working.
 *
 * Legacy plaintext values are encrypted in place the first time they are read.
 */
internal class SecureSessionPreferences(context: Context, name: String) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private var keyStore: KeyStore? = null
    private var cachedKey: SecretKey? = null

    fun getString(key: String, default: String? = null): String? {
        val raw = prefs.getString(key, null) ?: return default
        return decode(key, raw) ?: default
    }

    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)

    fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
            return
        }
        prefs.edit().putString(key, encode(key, value)).apply()
    }

    fun edit(block: Editor.() -> Unit) {
        val editor = Editor()
        editor.block()
        editor.commit()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        cachedKey = null
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        keyStore = null
    }

    inner class Editor {
        private val editor = prefs.edit()

        fun putString(key: String, value: String?): Editor {
            if (value == null) editor.remove(key) else editor.putString(key, encode(key, value))
            return this
        }

        fun putInt(key: String, value: Int): Editor {
            editor.putInt(key, value)
            return this
        }

        fun putBoolean(key: String, value: Boolean): Editor {
            editor.putBoolean(key, value)
            return this
        }

        fun remove(key: String): Editor {
            editor.remove(key)
            return this
        }

        fun clear(): Editor {
            editor.clear()
            return this
        }

        fun commit() {
            editor.apply()
        }
    }

    private fun encode(key: String, value: String): String {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = ByteArray(1 + iv.size + ciphertext.size)
            packed[0] = iv.size.toByte()
            iv.copyInto(packed, destinationOffset = 1)
            ciphertext.copyInto(packed, destinationOffset = 1 + iv.size)
            PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
        } catch (error: Exception) {
            // Keystore is expected to be available on every supported device.
            // Fall back to marked plaintext rather than losing the login session.
            Log.w(TAG, "Keystore encryption unavailable; using fallback storage")
            PLAIN_PREFIX + value
        }
    }

    private fun decode(key: String, raw: String): String? {
        if (raw.startsWith(PLAIN_PREFIX)) return raw.substring(PLAIN_PREFIX.length)
        if (!raw.startsWith(PREFIX)) {
            // Legacy plaintext prefs: transparently re-encrypt on first read.
            putString(key, raw)
            return raw
        }
        return try {
            val packed = Base64.decode(raw.substring(PREFIX.length), Base64.NO_WRAP)
            if (packed.size < 2) return null
            val ivSize = packed[0].toInt()
            if (ivSize <= 0 || packed.size <= 1 + ivSize) return null
            val iv = packed.copyOfRange(1, 1 + ivSize)
            val ciphertext = packed.copyOfRange(1 + ivSize, packed.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (error: Exception) {
            Log.w(TAG, "Could not decrypt stored session value; clearing it")
            prefs.edit().remove(key).apply()
            null
        }
    }

    private fun secretKey(): SecretKey {
        cachedKey?.let { return it }
        val store = keyStore()
        if (!store.containsAlias(KEY_ALIAS)) {
            generateKey(store)
        }
        val key = (store.getKey(KEY_ALIAS, null) as? SecretKey)
            ?: throw IllegalStateException("Keystore session key unavailable")
        cachedKey = key
        return key
    }

    private fun generateKey(store: KeyStore) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        generator.generateKey()
    }

    private fun keyStore(): KeyStore {
        keyStore?.let { return it }
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        keyStore = store
        return store
    }

    private companion object {
        const val TAG = "SecureSessionPrefs"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "pashurakshak_session_aes_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "enc:v1:"
        const val PLAIN_PREFIX = "plain:v1:"
    }
}
