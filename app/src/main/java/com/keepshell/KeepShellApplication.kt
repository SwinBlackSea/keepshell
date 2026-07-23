package com.keepshell

import android.app.Application
import com.keepshell.data.AppDatabase
import com.keepshell.data.HostRepository
import com.keepshell.data.SecureCredentialStore
import com.keepshell.data.SettingsStore
import com.keepshell.ssh.KnownHostStore
import com.keepshell.ssh.SessionRepository

class KeepShellApplication : Application() {
    val database by lazy { AppDatabase.create(this) }
    val hostRepository by lazy { HostRepository(database.hostDao()) }
    val credentialStore by lazy { SecureCredentialStore(this) }
    val knownHostStore by lazy { KnownHostStore(this) }
    val settingsStore by lazy { SettingsStore(this) }
    val sessionRepository by lazy {
        SessionRepository().also { repository ->
            repository.terminal.setScrollbackLimit(settingsStore.settings.value.scrollbackLines)
        }
    }
}
