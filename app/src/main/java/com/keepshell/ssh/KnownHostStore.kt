package com.keepshell.ssh

import android.content.Context
import android.util.Base64
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.UserInfo
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class PendingHostKey(
    val host: String,
    val type: String,
    val key: ByteArray,
    val fingerprint: String,
    val previousType: String?,
    val previousKey: ByteArray?,
    val previousFingerprint: String?
)

class KnownHostStore(context: Context) : HostKeyRepository {
    private val preferences = context.getSharedPreferences("known_hosts", Context.MODE_PRIVATE)

    @Volatile
    var pending: PendingHostKey? = null
        private set

    @Synchronized
    override fun check(host: String, key: ByteArray): Int {
        val incoming = HostKey(host, key)
        val saved = read(host)
        if (saved == null) {
            pending = pending(host, incoming.type, key, null)
            return HostKeyRepository.NOT_INCLUDED
        }
        if (saved.key.contentEquals(key)) {
            pending = null
            return HostKeyRepository.OK
        }
        pending = pending(host, incoming.type, key, saved)
        return HostKeyRepository.CHANGED
    }

    @Synchronized
    fun trustPending(replaceChangedKey: Boolean): Boolean {
        val value = pending ?: return false
        if (value.previousKey != null && !replaceChangedKey) return false
        write(value.host, StoredHostKey(value.type, value.key))
        pending = null
        return true
    }

    @Synchronized
    fun clearPending() {
        pending = null
    }

    override fun add(hostkey: HostKey, userinfo: UserInfo?) {
        write(
            hostkey.host,
            StoredHostKey(
                hostkey.type,
                Base64.decode(hostkey.key, Base64.NO_WRAP)
            )
        )
    }

    override fun remove(host: String, type: String?) {
        val stored = read(host) ?: return
        if (type == null || stored.type == type) preferences.edit().remove(storageKey(host)).apply()
    }

    override fun remove(host: String, type: String?, key: ByteArray?) {
        val stored = read(host) ?: return
        if ((type == null || stored.type == type) && (key == null || stored.key.contentEquals(key))) {
            preferences.edit().remove(storageKey(host)).apply()
        }
    }

    override fun getKnownHostsRepositoryID(): String = "KeepShell local known hosts"

    override fun getHostKey(): Array<HostKey> = preferences.all.mapNotNull { (entry, value) ->
        val host = decodeHostKey(entry) ?: return@mapNotNull null
        decodeStored(value as? String) ?.let { stored -> HostKey(host, stored.key) }
    }.toTypedArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> {
        if (host == null) return getHostKey().filter { type == null || it.type == type }.toTypedArray()
        val stored = read(host) ?: return emptyArray()
        if (type != null && stored.type != type) return emptyArray()
        return arrayOf(HostKey(host, stored.key))
    }

    private fun pending(
        host: String,
        type: String,
        key: ByteArray,
        previous: StoredHostKey?
    ) = PendingHostKey(
        host = host,
        type = type,
        key = key.copyOf(),
        fingerprint = fingerprint(key),
        previousType = previous?.type,
        previousKey = previous?.key?.copyOf(),
        previousFingerprint = previous?.key?.let(::fingerprint)
    )

    private fun write(host: String, value: StoredHostKey) {
        val encoded = "${value.type}|${Base64.encodeToString(value.key, Base64.NO_WRAP)}"
        preferences.edit().putString(storageKey(host), encoded).apply()
    }

    private fun read(host: String): StoredHostKey? =
        decodeStored(preferences.getString(storageKey(host), null))

    private fun decodeStored(encoded: String?): StoredHostKey? {
        if (encoded == null) return null
        val parts = encoded.split("|", limit = 2)
        if (parts.size != 2) return null
        return runCatching {
            StoredHostKey(parts[0], Base64.decode(parts[1], Base64.NO_WRAP))
        }.getOrNull()
    }

    private fun storageKey(host: String): String {
        val encodedHost = Base64.encodeToString(
            host.toByteArray(StandardCharsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
        return "host_$encodedHost"
    }

    private fun decodeHostKey(storageKey: String): String? {
        if (!storageKey.startsWith("host_")) return null
        return runCatching {
            String(
                Base64.decode(
                    storageKey.removePrefix("host_"),
                    Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                ),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
    }

    private fun fingerprint(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        return "SHA256:" + Base64.encodeToString(
            digest,
            Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private data class StoredHostKey(val type: String, val key: ByteArray)
}
