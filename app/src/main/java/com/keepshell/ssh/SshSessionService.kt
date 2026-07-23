package com.keepshell.ssh

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.keepshell.KeepShellApplication
import com.keepshell.MainActivity
import com.keepshell.R
import com.keepshell.data.AuthMethod
import com.keepshell.data.HostEntity
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SshSessionService : Service() {
    inner class LocalBinder : Binder() {
        val service: SshSessionService get() = this@SshSessionService
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val application: KeepShellApplication get() = getApplication() as KeepShellApplication
    private val repository get() = application.sessionRepository
    private val hostRepository get() = application.hostRepository
    private val credentialStore get() = application.credentialStore
    private val knownHostStore get() = application.knownHostStore

    @Volatile private var sshSession: Session? = null
    @Volatile private var shellChannel: ChannelShell? = null
    @Volatile private var shellInput: InputStream? = null
    @Volatile private var shellOutput: OutputStream? = null
    private var connectionJob: Job? = null
    private var lastHostId: Long? = null
    private var awaitingFingerprintForHostId: Long? = null
    private var enhancedKeepAliveLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val hostId = intent.getLongExtra(EXTRA_HOST_ID, -1L)
                if (hostId > 0) connect(hostId)
            }
            ACTION_DISCONNECT -> disconnect(userInitiated = true)
            ACTION_RECONNECT -> manualReconnect()
            ACTION_TRUST_FINGERPRINT -> trustPendingFingerprint(
                intent.getBooleanExtra(EXTRA_REPLACE_CHANGED_KEY, false)
            )
            ACTION_REJECT_FINGERPRINT -> rejectPendingFingerprint()
        }
        return START_NOT_STICKY
    }

    fun connect(hostId: Long) {
        val current = repository.state.value
        if (current.hasActiveTransport || current.isBusy) return
        val resumesPreviousHost = current.host?.id == hostId && (
            current.phase == SessionPhase.DISCONNECTED ||
                current.phase == SessionPhase.FAILED
            )
        if (resumesPreviousHost) {
            repository.terminal.appendSessionDivider("${clock()} 开始新会话")
        } else {
            repository.terminal.clear()
        }
        connectionJob?.cancel()
        connectionJob = scope.launch {
            connectInternal(hostId, isManualReconnect = resumesPreviousHost)
        }
    }

    fun trustPendingFingerprint(replaceChangedKey: Boolean) {
        val hostId = awaitingFingerprintForHostId ?: return
        if (!knownHostStore.trustPending(replaceChangedKey)) return
        awaitingFingerprintForHostId = null
        connectionJob?.cancel()
        connectionJob = scope.launch { connectInternal(hostId, isManualReconnect = false) }
    }

    fun rejectPendingFingerprint() {
        knownHostStore.clearPending()
        awaitingFingerprintForHostId = null
        repository.update {
            it.copy(
                phase = SessionPhase.FAILED,
                endedAt = System.currentTimeMillis(),
                reason = "已取消主机指纹确认",
                fingerprintPrompt = null
            )
        }
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun manualReconnect() {
        val hostId = lastHostId ?: return
        val state = repository.state.value
        if (state.hasActiveTransport || state.isBusy) return
        repository.terminal.appendSessionDivider("${clock()} 开始新会话")
        connectionJob?.cancel()
        connectionJob = scope.launch { connectInternal(hostId, isManualReconnect = true) }
    }

    fun send(bytes: ByteArray) {
        val output = shellOutput ?: return
        scope.launch {
            runCatching {
                output.write(bytes)
                output.flush()
            }.onFailure { handleTransportEnd("发送失败") }
        }
    }

    fun sendText(text: String) = send(text.toByteArray(Charsets.UTF_8))

    fun resize(columns: Int, rows: Int, widthPx: Int = 0, heightPx: Int = 0) {
        runCatching {
            shellChannel?.setPtySize(
                columns.coerceAtLeast(20),
                rows.coerceAtLeast(8),
                widthPx.coerceAtLeast(0),
                heightPx.coerceAtLeast(0)
            )
        }
    }

    fun setEnhancedKeepAlive(enabled: Boolean) {
        if (enabled && repository.state.value.phase == SessionPhase.CONNECTED) {
            acquireEnhancedKeepAliveLock()
        } else {
            releaseEnhancedKeepAliveLock()
        }
    }

    fun disconnect(userInitiated: Boolean) {
        connectionJob?.cancel()
        connectionJob = null
        closeTransport()
        val now = System.currentTimeMillis()
        repository.terminal.appendSessionDivider(
            if (userInitiated) "会话于 ${clock(now)} 由用户断开"
            else "会话于 ${clock(now)} 断开"
        )
        repository.update {
            it.copy(
                phase = SessionPhase.DISCONNECTED,
                endedAt = now,
                reason = if (userInitiated) "用户主动断开" else it.reason ?: "连接已结束",
                fingerprintPrompt = null
            )
        }
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun connectInternal(hostId: Long, isManualReconnect: Boolean) {
        val host = hostRepository.get(hostId) ?: run {
            publishFailure(null, "主机配置不存在")
            return
        }
        lastHostId = hostId
        closeTransport()
        val sessionId = newSessionId()
        val startedAt = System.currentTimeMillis()
        repository.replace(
            SessionState(
                phase = SessionPhase.CONNECTING,
                host = host,
                sessionId = sessionId,
                startedAt = startedAt
            )
        )
        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            val credentials = credentialStore.load(host.id)
            val jsch = JSch().apply {
                hostKeyRepository = knownHostStore
                if (host.auth == AuthMethod.PRIVATE_KEY) {
                    val privateKey = credentials.privateKey
                        ?: throw IllegalStateException("未找到私钥")
                    addIdentity(
                        host.keyDisplayName ?: "keepshell-key",
                        privateKey.toByteArray(Charsets.UTF_8),
                        null,
                        credentials.passphrase
                            ?.takeIf { it.isNotEmpty() }
                            ?.toByteArray(Charsets.UTF_8)
                    )
                }
            }

            repository.update { it.copy(phase = SessionPhase.AUTHENTICATING) }
            updateNotification()

            val session = jsch.getSession(host.username, host.address, host.port).apply {
                setConfig("StrictHostKeyChecking", "yes")
                setConfig("PreferredAuthentications", preferredAuthentications(host.auth))
                if (host.auth == AuthMethod.PASSWORD) {
                    setPassword(credentials.password ?: throw IllegalStateException("未找到密码"))
                }
                timeout = host.connectTimeoutSeconds * 1_000
                serverAliveInterval = host.keepAliveSeconds * 1_000
                serverAliveCountMax = 3
            }
            sshSession = session
            session.connect(host.connectTimeoutSeconds * 1_000)

            val channel = session.openChannel("shell") as ChannelShell
            channel.setPty(true)
            channel.setPtyType("xterm-256color", 80, 24, 0, 0)
            val input = channel.inputStream
            val output = channel.outputStream
            shellChannel = channel
            shellInput = input
            shellOutput = output
            channel.connect(host.connectTimeoutSeconds * 1_000)

            hostRepository.markConnected(host.id)
            val connectedAt = System.currentTimeMillis()
            repository.replace(
                SessionState(
                    phase = SessionPhase.CONNECTED,
                    host = host,
                    sessionId = sessionId,
                    startedAt = connectedAt
                )
            )
            setEnhancedKeepAlive(application.settingsStore.settings.value.enhancedKeepAlive)
            if (isManualReconnect) {
                repository.terminal.append("\r\n")
            }
            updateNotification()
            readLoop(input, channel, session)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            closeTransport()
            val pending = knownHostStore.pending
            if (pending != null && isHostKeyRejection(error)) {
                awaitingFingerprintForHostId = host.id
                repository.replace(
                    SessionState(
                        phase = SessionPhase.FINGERPRINT_REQUIRED,
                        host = host,
                        sessionId = sessionId,
                        startedAt = startedAt,
                        fingerprintPrompt = FingerprintPrompt(
                            algorithm = pending.type,
                            fingerprint = pending.fingerprint,
                            previousAlgorithm = pending.previousType,
                            previousFingerprint = pending.previousFingerprint
                        )
                    )
                )
                updateNotification()
            } else {
                publishFailure(host, classifyError(error), sessionId, startedAt)
            }
        }
    }

    private suspend fun readLoop(input: InputStream, channel: ChannelShell, session: Session) {
        val buffer = ByteArray(8 * 1024)
        try {
            while (scope.isActive && session.isConnected && channel.isConnected) {
                val count = withContext(Dispatchers.IO) { input.read(buffer) }
                if (count < 0) break
                if (count > 0) repository.terminal.append(buffer, count)
                if (count == 0) delay(10)
            }
            handleTransportEnd(channel.exitStatus.takeIf { it >= 0 }?.let { "远端会话结束（$it）" }
                ?: "远端已关闭连接")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            handleTransportEnd("网络或服务器已中断连接")
        }
    }

    private fun handleTransportEnd(reason: String) {
        if (repository.state.value.phase != SessionPhase.CONNECTED) return
        closeTransport()
        val now = System.currentTimeMillis()
        repository.terminal.appendSessionDivider("会话于 ${clock(now)} 断开")
        repository.update {
            it.copy(
                phase = SessionPhase.DISCONNECTED,
                endedAt = now,
                reason = reason,
                fingerprintPrompt = null
            )
        }
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun publishFailure(
        host: HostEntity?,
        reason: String,
        sessionId: String? = repository.state.value.sessionId,
        startedAt: Long? = repository.state.value.startedAt
    ) {
        repository.replace(
            SessionState(
                phase = SessionPhase.FAILED,
                host = host,
                sessionId = sessionId,
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                reason = reason
            )
        )
        updateNotification()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Synchronized
    private fun closeTransport() {
        releaseEnhancedKeepAliveLock()
        runCatching { shellOutput?.close() }
        runCatching { shellInput?.close() }
        runCatching { shellChannel?.disconnect() }
        runCatching { sshSession?.disconnect() }
        shellOutput = null
        shellInput = null
        shellChannel = null
        sshSession = null
    }

    @Synchronized
    private fun acquireEnhancedKeepAliveLock() {
        val existing = enhancedKeepAliveLock
        if (existing?.isHeld == true) return
        val powerManager = getSystemService(PowerManager::class.java)
        enhancedKeepAliveLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:ssh-enhanced-keep-alive"
        ).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    @Synchronized
    private fun releaseEnhancedKeepAliveLock() {
        enhancedKeepAliveLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        enhancedKeepAliveLock = null
    }

    private fun preferredAuthentications(method: AuthMethod): String = when (method) {
        AuthMethod.PASSWORD -> "password,keyboard-interactive"
        AuthMethod.PRIVATE_KEY -> "publickey"
    }

    private fun isHostKeyRejection(error: Throwable): Boolean =
        error is JSchException && (
            error.message?.contains("HostKey", ignoreCase = true) == true ||
                error.message?.contains("reject", ignoreCase = true) == true
            )

    private fun classifyError(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("Auth fail", ignoreCase = true) -> "认证失败，请检查凭据"
            message.contains("timeout", ignoreCase = true) -> "连接超时"
            message.contains("UnknownHost", ignoreCase = true) -> "无法解析主机地址"
            message.contains("Connection refused", ignoreCase = true) -> "服务器拒绝连接"
            message.contains("private key", ignoreCase = true) -> "私钥不可用或格式不受支持"
            error is IllegalStateException -> message.ifBlank { "缺少连接凭据" }
            else -> "无法建立 SSH 连接"
        }
    }

    private fun newSessionId(): String {
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return "KS-" + buildString {
            repeat(4) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private fun clock(timestamp: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val state = repository.state.value
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TERMINAL, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val disconnectIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TERMINAL, true)
                .putExtra(MainActivity.EXTRA_CONFIRM_DISCONNECT, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = state.host?.name ?: "KeepShell"
        val text = when (state.phase) {
            SessionPhase.CONNECTING -> "正在连接"
            SessionPhase.AUTHENTICATING -> "正在认证"
            SessionPhase.FINGERPRINT_REQUIRED -> "等待确认主机指纹"
            SessionPhase.CONNECTED -> "SSH 会话已连接"
            SessionPhase.DISCONNECTED -> "连接已断开"
            SessionPhase.FAILED -> state.reason ?: "连接失败"
            SessionPhase.IDLE -> "未连接"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(
                state.phase == SessionPhase.CONNECTED ||
                    state.phase == SessionPhase.CONNECTING ||
                    state.phase == SessionPhase.AUTHENTICATING ||
                    state.phase == SessionPhase.FINGERPRINT_REQUIRED
            )
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .apply {
                if (state.phase == SessionPhase.CONNECTED) {
                    setWhen(state.startedAt ?: System.currentTimeMillis())
                    setUsesChronometer(true)
                    addAction(0, "返回终端", openIntent)
                    addAction(0, "断开", disconnectIntent)
                }
            }
            .build()
    }

    private fun updateNotification() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification())
        }
    }

    override fun onDestroy() {
        val state = repository.state.value
        if (
            state.phase == SessionPhase.CONNECTED ||
            state.phase == SessionPhase.CONNECTING ||
            state.phase == SessionPhase.AUTHENTICATING ||
            state.phase == SessionPhase.FINGERPRINT_REQUIRED
        ) {
            val now = System.currentTimeMillis()
            if (state.phase == SessionPhase.CONNECTED) {
                repository.terminal.appendSessionDivider("会话于 ${clock(now)} 断开")
            }
            repository.update {
                it.copy(
                    phase = SessionPhase.DISCONNECTED,
                    endedAt = now,
                    reason = "会话服务已停止",
                    fingerprintPrompt = null
                )
            }
        }
        closeTransport()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "ssh_session"
        private const val NOTIFICATION_ID = 4102
        private const val EXTRA_HOST_ID = "host_id"
        const val EXTRA_REPLACE_CHANGED_KEY = "replace_changed_key"
        const val ACTION_CONNECT = "com.keepshell.action.CONNECT"
        const val ACTION_DISCONNECT = "com.keepshell.action.DISCONNECT"
        const val ACTION_RECONNECT = "com.keepshell.action.RECONNECT"
        const val ACTION_TRUST_FINGERPRINT = "com.keepshell.action.TRUST_FINGERPRINT"
        const val ACTION_REJECT_FINGERPRINT = "com.keepshell.action.REJECT_FINGERPRINT"

        fun connectIntent(context: Context, hostId: Long) =
            Intent(context, SshSessionService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_HOST_ID, hostId)

        fun startConnection(context: Context, hostId: Long) {
            ContextCompat.startForegroundService(context, connectIntent(context, hostId))
        }
    }
}
