package com.keepshell.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AuthMethod {
    PASSWORD,
    PRIVATE_KEY
}

@Entity(tableName = "hosts")
data class HostEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String,
    val port: Int = 22,
    val username: String,
    val authMethod: String = AuthMethod.PASSWORD.name,
    val keyDisplayName: String? = null,
    val keepAliveSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 15,
    val favorite: Boolean = false,
    val sortOrder: Int = 0,
    val lastConnectedAt: Long? = null
) {
    val displayAddress: String
        get() = "$username@$address:$port"

    val auth: AuthMethod
        get() = runCatching { AuthMethod.valueOf(authMethod) }.getOrDefault(AuthMethod.PASSWORD)
}

data class HostDraft(
    val id: Long = 0,
    val name: String = "",
    val address: String = "",
    val port: String = "22",
    val username: String = "",
    val authMethod: AuthMethod = AuthMethod.PASSWORD,
    val password: String = "",
    val privateKey: String = "",
    val privateKeyName: String? = null,
    val passphrase: String = "",
    val keepAliveSeconds: Int = 30,
    val connectTimeoutSeconds: Int = 15
) {
    fun validate(): Map<String, String> = buildMap {
        if (name.isBlank()) put("name", "请输入名称")
        if (address.isBlank()) put("address", "请输入主机地址")
        val parsedPort = port.toIntOrNull()
        if (parsedPort == null || parsedPort !in 1..65535) put("port", "端口应为 1–65535")
        if (username.isBlank()) put("username", "请输入用户名")
        if (authMethod == AuthMethod.PASSWORD && password.isEmpty()) {
            put("password", "请输入密码")
        }
        if (authMethod == AuthMethod.PRIVATE_KEY && privateKey.isEmpty()) {
            put("privateKey", "请选择 OpenSSH 私钥")
        }
    }

    fun toEntity(previous: HostEntity? = null): HostEntity = HostEntity(
        id = id,
        name = name.trim(),
        address = address.trim(),
        port = port.toIntOrNull() ?: 22,
        username = username.trim(),
        authMethod = authMethod.name,
        keyDisplayName = privateKeyName ?: previous?.keyDisplayName,
        keepAliveSeconds = keepAliveSeconds,
        connectTimeoutSeconds = connectTimeoutSeconds,
        favorite = previous?.favorite ?: false,
        sortOrder = previous?.sortOrder ?: 0,
        lastConnectedAt = previous?.lastConnectedAt
    )
}
