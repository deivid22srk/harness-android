package dev.harness.core.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider API keys encrypted with a hardware-backed AES key in the
 * Android Keystore. Nothing sensitive is ever written in plaintext, logged,
 * backed up, or committed (see PROMPT §11.1).
 *
 * The encrypted blob lives in the app's private files dir; the key material
 * never leaves the Keystore and, on most devices, never leaves the TEE.
 */
class SecretStore(context: Context) {

    private val file = File(context.filesDir, "secrets.bin")
    private val prefs = context.getSharedPreferences("harness_secrets", Context.MODE_PRIVATE)

    fun put(name: String, value: String) {
        val current = load()
        val updated = current.toMutableMap()
        if (value.isBlank()) updated.remove(name) else updated[name] = value
        save(updated)
    }

    fun get(name: String): String? = load()[name]

    fun all(): Map<String, String> = load()

    fun clear() {
        file.delete()
        prefs.edit().clear().apply()
    }

    // -----------------------------------------------------------------------

    private fun load(): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv()))
            val plain = cipher.doFinal(file.readBytes())
            decodeMap(String(plain, Charsets.UTF_8))
        } catch (_: Throwable) {
            // Undecryptable (e.g. key invalidated after a lock change): start clean.
            emptyMap()
        }
    }

    private fun save(map: Map<String, String>) {
        if (map.isEmpty()) {
            file.delete()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv()))
        val bytes = cipher.doFinal(encodeMap(map).toByteArray(Charsets.UTF_8))
        file.writeBytes(bytes)
    }

    private fun iv(): ByteArray {
        val stored = prefs.getString(KEY_IV, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val fresh = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        prefs.edit().putString(KEY_IV, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
        return fresh
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
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
        return generator.generateKey()
    }

    private fun encodeMap(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) ->
            "${Base64.encodeToString(k.toByteArray(), Base64.NO_WRAP)}=" +
                Base64.encodeToString(v.toByteArray(), Base64.NO_WRAP)
        }

    private fun decodeMap(text: String): Map<String, String> {
        if (text.isBlank()) return emptyMap()
        return text.lineSequence().mapNotNull { line ->
            val eq = line.indexOf('=')
            if (eq <= 0) null
            else {
                val k = String(Base64.decode(line.substring(0, eq), Base64.NO_WRAP))
                val v = String(Base64.decode(line.substring(eq + 1), Base64.NO_WRAP))
                k to v
            }
        }.toMap()
    }

    private companion object {
        const val KEY_ALIAS = "harness_secret_key_v1"
        const val KEY_IV = "secrets_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
    }
}
