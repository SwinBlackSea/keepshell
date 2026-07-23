package com.keepshell.ssh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionRepository {
    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    val terminal = TerminalEngine()

    fun update(transform: (SessionState) -> SessionState) {
        _state.value = transform(_state.value)
    }

    fun replace(state: SessionState) {
        _state.value = state
    }
}
