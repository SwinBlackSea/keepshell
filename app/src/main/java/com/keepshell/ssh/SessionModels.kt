package com.keepshell.ssh

import com.keepshell.data.HostEntity

enum class SessionPhase {
    IDLE,
    CONNECTING,
    FINGERPRINT_REQUIRED,
    AUTHENTICATING,
    CONNECTED,
    DISCONNECTED,
    FAILED
}

data class FingerprintPrompt(
    val algorithm: String,
    val fingerprint: String,
    val previousAlgorithm: String? = null,
    val previousFingerprint: String? = null
) {
    val isChanged: Boolean get() = previousFingerprint != null
}

data class SessionState(
    val phase: SessionPhase = SessionPhase.IDLE,
    val host: HostEntity? = null,
    val sessionId: String? = null,
    val startedAt: Long? = null,
    val endedAt: Long? = null,
    val reason: String? = null,
    val fingerprintPrompt: FingerprintPrompt? = null
) {
    val hasActiveTransport: Boolean get() = phase == SessionPhase.CONNECTED
    val isBusy: Boolean
        get() = phase == SessionPhase.CONNECTING || phase == SessionPhase.AUTHENTICATING
}
