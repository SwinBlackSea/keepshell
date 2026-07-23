package com.keepshell.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class HostCredentials(
    val password: String? = null,
    val privateKey: String? = null,
    val passphrase: String? = null
)

class SecureCredentialStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    @Synchronized
    fun save(hostId: Long, credentials: HostCredentials) {
        val editor = preferences.edit()
        writeOrRemove(editor, key(hostId, "password"), credentials.password)
        writeOrRemove(editor, key(hostId, "private_key"), credentials.privateKey)
        writeOrRemove(editor, key(hostId, "passphrase"), credentials.passphrase)
        check(editor.commit()) { "Unable to store encrypted credentials" }
    }

    @Synchronized
    fun load(hostId: Long): HostCredentials = HostCredentials(
        password = read(key(hostId, "password")),
        privateKey = read(key(hostId, "private_key")),
        passphrase = read(key(hostId, "passphrase"))
    )

    @Synchronized
    fun delete(hostId: Long) {
        preferences.edit()
            .remove(key(hostId, "password"))
            .remove(key(hostId, "private_key"))
            .remove(key(hostId, "passphrase"))
            .apply()
    }

    private fun writeOrRemove(
        editor: android.content.SharedPreferences.Editor,
        key: String,
        value: String?
    ) {
        if (value == null) return
        if (value.isEmpty()) editor.remove(key) else editor.putString(key, encrypt(value))
    }

    private fun read(key: String): String? {
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching { decrypt(encoded) }.getOrNull()
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        return listOf(cipher.iv, cipherText)
            .joinToString(".") { Base64.encodeToString(it, Base64.NO_WRAP) }
    }

    private fun decrypt(encoded: String): String {
        val parts = encoded.split(".", limit = 2)
        require(parts.size == 2)
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(cipherText), StandardCharsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun key(hostId: Long, suffix: String) = "host_${hostId}_$suffix"

    companion object {
        private const val PREFS_NAME = "secure_credentials"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "keepshell.credentials.v1"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
