package com.keepshell

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.keepshell.ssh.SshSessionService
import com.keepshell.ui.KeepShellApp
import com.keepshell.ui.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var isBound = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? SshSessionService.LocalBinder)?.service
            viewModel.bindService(service)
            isBound = service != null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viewModel.bindService(null)
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        handleNavigationIntent(intent)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                (application as KeepShellApplication).settingsStore.settings.collect { settings ->
                    if (settings.screenshotProtection) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            KeepShellApp(viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isBound = bindService(
            Intent(this, SshSessionService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        if (isBound) {
            unbindService(connection)
            isBound = false
            viewModel.bindService(null)
        }
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNavigationIntent(intent: Intent) {
        if (intent.getBooleanExtra(EXTRA_CONFIRM_DISCONNECT, false)) {
            viewModel.requestDisconnectConfirmation()
        } else if (intent.getBooleanExtra(EXTRA_OPEN_TERMINAL, false)) {
            viewModel.showTerminal()
        }
        intent.removeExtra(EXTRA_CONFIRM_DISCONNECT)
        intent.removeExtra(EXTRA_OPEN_TERMINAL)
    }

    companion object {
        const val EXTRA_OPEN_TERMINAL = "open_terminal"
        const val EXTRA_CONFIRM_DISCONNECT = "confirm_disconnect"
    }
}
