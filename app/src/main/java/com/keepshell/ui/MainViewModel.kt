package com.keepshell.ui

import android.app.Application
import android.content.Context
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.keepshell.KeepShellApplication
import com.keepshell.data.AppSettings
import com.keepshell.data.AuthMethod
import com.keepshell.data.HostCredentials
import com.keepshell.data.HostDraft
import com.keepshell.data.HostEntity
import com.keepshell.ssh.BackgroundConnectionPolicy
import com.keepshell.ssh.SessionPhase
import com.keepshell.ssh.SessionState
import com.keepshell.ssh.SshSessionService
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppScreen {
    data object Hosts : AppScreen
    data object Terminal : AppScreen
    data object Settings : AppScreen
    data class Editor(val hostId: Long?) : AppScreen
}

data class EditorUiState(
    val draft: HostDraft = HostDraft(),
    val errors: Map<String, String> = emptyMap(),
    val loading: Boolean = false,
    val testing: Boolean = false,
    val testResult: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KeepShellApplication
    private val hostRepository = app.hostRepository
    private val credentialStore = app.credentialStore

    val hosts = hostRepository.hosts.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    val session = app.sessionRepository.state
    val terminalEngine = app.sessionRepository.terminal
    val terminalRevision = terminalEngine.revision
    val settings = app.settingsStore.settings

    private val _screen = MutableStateFlow<AppScreen>(AppScreen.Hosts)
    val screen = _screen.asStateFlow()
    private val _editor = MutableStateFlow(EditorUiState())
    val editor = _editor.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _ctrlArmed = MutableStateFlow(false)
    val ctrlArmed = _ctrlArmed.asStateFlow()
    private val _disconnectConfirmationRequest = MutableStateFlow(0L)
    val disconnectConfirmationRequest = _disconnectConfirmationRequest.asStateFlow()
    private val _vendorBackgroundAccessRequest = MutableStateFlow(false)
    val vendorBackgroundAccessRequest = _vendorBackgroundAccessRequest.asStateFlow()

    @Volatile private var service: SshSessionService? = null

    fun bindService(value: SshSessionService?) {
        service = value
    }

    fun showHosts() {
        _screen.value = AppScreen.Hosts
    }

    fun showTerminal() {
        _screen.value = AppScreen.Terminal
    }

    fun showSettings() {
        _screen.value = AppScreen.Settings
    }

    fun requestDisconnectConfirmation() {
        _disconnectConfirmationRequest.value += 1
        showTerminal()
    }

    fun dismissVendorBackgroundAccessRequest() {
        markVendorBackgroundAccessPrompted()
        _vendorBackgroundAccessRequest.value = false
    }

    fun openVendorBackgroundSettings(activityContext: Context) {
        markVendorBackgroundAccessPrompted()
        _vendorBackgroundAccessRequest.value = false
        BackgroundConnectionPolicy.openAppSettings(activityContext)
    }

    fun openEditor(hostId: Long? = null) {
        _screen.value = AppScreen.Editor(hostId)
        _editor.value = EditorUiState(loading = hostId != null)
        if (hostId == null) return
        viewModelScope.launch {
            val host = hostRepository.get(hostId)
            if (host == null) {
                _messages.tryEmit("主机配置不存在")
                showHosts()
                return@launch
            }
            val credentials = withContext(Dispatchers.IO) { credentialStore.load(host.id) }
            _editor.value = EditorUiState(
                draft = HostDraft(
                    id = host.id,
                    name = host.name,
                    address = host.address,
                    port = host.port.toString(),
                    username = host.username,
                    authMethod = host.auth,
                    password = credentials.password.orEmpty(),
                    privateKey = credentials.privateKey.orEmpty(),
                    privateKeyName = host.keyDisplayName,
                    passphrase = credentials.passphrase.orEmpty(),
                    keepAliveSeconds = host.keepAliveSeconds,
                    connectTimeoutSeconds = host.connectTimeoutSeconds
                )
            )
        }
    }

    fun updateDraft(transform: (HostDraft) -> HostDraft) {
        _editor.value = _editor.value.copy(
            draft = transform(_editor.value.draft),
            errors = emptyMap(),
            testResult = null
        )
    }

    fun saveHost(onSaved: ((Long) -> Unit)? = null) {
        val draft = _editor.value.draft
        val errors = draft.validate()
        if (errors.isNotEmpty()) {
            _editor.value = _editor.value.copy(errors = errors)
            return
        }
        viewModelScope.launch {
            val previous = draft.id.takeIf { it > 0 }?.let { hostRepository.get(it) }
            val hostId = hostRepository.save(draft.toEntity(previous))
            withContext(Dispatchers.IO) {
                credentialStore.save(
                    hostId,
                    HostCredentials(
                        password = if (draft.authMethod == AuthMethod.PASSWORD) draft.password else null,
                        privateKey = if (draft.authMethod == AuthMethod.PRIVATE_KEY) draft.privateKey else null,
                        passphrase = if (draft.authMethod == AuthMethod.PRIVATE_KEY) draft.passphrase else null
                    )
                )
            }
            _messages.tryEmit("主机配置已保存")
            onSaved?.invoke(hostId)
            showHosts()
        }
    }

    fun deleteHost() {
        val hostId = _editor.value.draft.id
        if (hostId <= 0) return
        val currentSession = session.value
        if (
            currentSession.host?.id == hostId &&
            currentSession.phase !in setOf(
                SessionPhase.IDLE,
                SessionPhase.DISCONNECTED,
                SessionPhase.FAILED
            )
        ) {
            _messages.tryEmit("请先断开当前会话")
            showTerminal()
            return
        }
        viewModelScope.launch {
            hostRepository.get(hostId)?.let { hostRepository.delete(it) }
            withContext(Dispatchers.IO) { credentialStore.delete(hostId) }
            if (session.value.host?.id == hostId) {
                app.sessionRepository.replace(SessionState())
                app.sessionRepository.terminal.clear()
            }
            _messages.tryEmit("主机已删除")
            showHosts()
        }
    }

    fun testHost() {
        val draft = _editor.value.draft
        val errors = draft.validate().filterKeys { it in setOf("address", "port") }
        if (errors.isNotEmpty()) {
            _editor.value = _editor.value.copy(errors = errors)
            return
        }
        _editor.value = _editor.value.copy(testing = true, testResult = null)
        viewModelScope.launch {
            val started = System.nanoTime()
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Socket().use {
                        it.connect(
                            InetSocketAddress(draft.address.trim(), draft.port.toInt()),
                            draft.connectTimeoutSeconds * 1_000
                        )
                    }
                    val elapsedMs = (System.nanoTime() - started) / 1_000_000
                    "SSH 端口可达 · ${elapsedMs} ms"
                }.getOrElse { "无法连接此地址和端口" }
            }
            _editor.value = _editor.value.copy(testing = false, testResult = result)
        }
    }

    fun connect(host: HostEntity, activityContext: Context) {
        val state = session.value
        if (state.phase == SessionPhase.CONNECTED && state.host?.id != host.id) {
            _messages.tryEmit("请先断开当前会话")
            showTerminal()
            return
        }
        if (state.host?.id == host.id && state.phase == SessionPhase.CONNECTED) {
            showTerminal()
            return
        }
        SshSessionService.startConnection(getApplication(), host.id)
        showTerminal()
        requestBackgroundConnectionAccess(activityContext)
    }

    fun disconnect() {
        service?.disconnect(userInitiated = true)
            ?: getApplication<KeepShellApplication>().startService(
                android.content.Intent(
                    getApplication(),
                    SshSessionService::class.java
                ).setAction(SshSessionService.ACTION_DISCONNECT)
            )
    }

    fun reconnect() {
        service?.manualReconnect()
            ?: getApplication<KeepShellApplication>().startService(
                android.content.Intent(
                    getApplication(),
                    SshSessionService::class.java
                ).setAction(SshSessionService.ACTION_RECONNECT)
            )
    }

    fun trustFingerprint(replaceChangedKey: Boolean) {
        service?.trustPendingFingerprint(replaceChangedKey) ?: getApplication<KeepShellApplication>()
            .startService(
                android.content.Intent(
                    getApplication(),
                    SshSessionService::class.java
                )
                    .setAction(SshSessionService.ACTION_TRUST_FINGERPRINT)
                    .putExtra(SshSessionService.EXTRA_REPLACE_CHANGED_KEY, replaceChangedKey)
            )
        showTerminal()
    }

    fun rejectFingerprint() {
        service?.rejectPendingFingerprint() ?: getApplication<KeepShellApplication>().startService(
            android.content.Intent(
                getApplication(),
                SshSessionService::class.java
            ).setAction(SshSessionService.ACTION_REJECT_FINGERPRINT)
        )
        showHosts()
    }

    fun sendCommand(command: String) {
        if (session.value.phase != SessionPhase.CONNECTED) return
        val normalized = command.trimEnd('\n', '\r')
        if (normalized.isEmpty()) {
            service?.sendText("\r")
            return
        }
        service?.sendText(normalized + "\r")
    }

    fun sendTerminalText(text: String) {
        if (text.isEmpty() || session.value.phase != SessionPhase.CONNECTED) return
        if (!_ctrlArmed.value) {
            service?.sendText(text)
            return
        }

        val codePoint = text.codePointAt(0)
        val controlCode = when (codePoint) {
            in 'a'.code..'z'.code -> codePoint - 'a'.code + 1
            in 'A'.code..'Z'.code -> codePoint - 'A'.code + 1
            ' '.code, '2'.code -> 0
            '['.code, '3'.code -> 27
            '\\'.code, '4'.code -> 28
            ']'.code, '5'.code -> 29
            '^'.code, '6'.code -> 30
            '_'.code, '/'.code, '7'.code -> 31
            '8'.code, '?'.code -> 127
            else -> null
        }
        _ctrlArmed.value = false
        if (controlCode == null) {
            service?.sendText(text)
            return
        }

        service?.send(byteArrayOf(controlCode.toByte()))
        val firstLength = Character.charCount(codePoint)
        if (text.length > firstLength) {
            service?.sendText(text.substring(firstLength))
        }
    }

    fun sendTerminalBytes(bytes: ByteArray) {
        if (bytes.isEmpty() || session.value.phase != SessionPhase.CONNECTED) return
        service?.send(bytes)
    }

    fun sendExtraKey(key: String) {
        if (key == "CTRL") {
            _ctrlArmed.value = !_ctrlArmed.value
            return
        }
        val bytes = when (key) {
            "ESC" -> byteArrayOf(0x1B)
            "TAB" -> byteArrayOf(0x09)
            "↑" -> terminalEngine.keySequence(KeyEvent.KEYCODE_DPAD_UP)
            "↓" -> terminalEngine.keySequence(KeyEvent.KEYCODE_DPAD_DOWN)
            "←" -> terminalEngine.keySequence(KeyEvent.KEYCODE_DPAD_LEFT)
            "→" -> terminalEngine.keySequence(KeyEvent.KEYCODE_DPAD_RIGHT)
            else -> key.toByteArray()
        }
        bytes?.let { service?.send(it) }
        _ctrlArmed.value = false
    }

    fun sendCtrlCharacter(char: Char) {
        if (!_ctrlArmed.value || !char.isLetter()) return
        service?.send(byteArrayOf(char.uppercaseChar().code.and(0x1F).toByte()))
        _ctrlArmed.value = false
    }

    fun resizeTerminal(
        columns: Int,
        rows: Int,
        cellWidthPx: Int,
        cellHeightPx: Int,
        widthPx: Int,
        heightPx: Int
    ) {
        terminalEngine.resize(columns, rows, cellWidthPx, cellHeightPx)
        service?.resize(columns, rows, widthPx, heightPx)
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val next = transform(settings.value)
        app.settingsStore.update(next)
        app.sessionRepository.terminal.setScrollbackLimit(next.scrollbackLines)
        service?.setEnhancedKeepAlive(next.enhancedKeepAlive)
    }

    private fun requestBackgroundConnectionAccess(activityContext: Context) {
        if (!settings.value.enhancedKeepAlive) return
        val preferences = app.getSharedPreferences(
            "connection_permission_prompts",
            Application.MODE_PRIVATE
        )
        if (BackgroundConnectionPolicy.requiresVendorBackgroundAccess()) {
            if (!preferences.getBoolean("vendor_background_access_v1", false)) {
                _vendorBackgroundAccessRequest.value = true
            }
            return
        }
        if (BackgroundConnectionPolicy.isUnrestricted(app)) return
        if (preferences.getBoolean("battery_exemption_requested_v1", false)) return
        if (BackgroundConnectionPolicy.requestUnrestricted(activityContext)) {
            preferences.edit()
                .putBoolean("battery_exemption_requested_v1", true)
                .apply()
            _messages.tryEmit("请允许后台运行，避免 SSH 会话被系统冻结")
        }
    }

    private fun markVendorBackgroundAccessPrompted() {
        app.getSharedPreferences(
            "connection_permission_prompts",
            Application.MODE_PRIVATE
        ).edit()
            .putBoolean("vendor_background_access_v1", true)
            .apply()
    }
}
