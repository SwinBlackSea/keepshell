package com.keepshell.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keepshell.ssh.FingerprintPrompt
import com.keepshell.ssh.SessionPhase
import com.keepshell.ssh.SessionState
import com.keepshell.ui.screens.HostEditorScreen
import com.keepshell.ui.screens.HostsScreen
import com.keepshell.ui.screens.SettingsScreen
import com.keepshell.ui.screens.TerminalScreen
import com.keepshell.ui.theme.Danger
import com.keepshell.ui.theme.DangerSoft
import com.keepshell.ui.theme.KeepShellTheme
import com.keepshell.ui.theme.Signal
import com.keepshell.ui.theme.SignalSoft

@Composable
fun KeepShellApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val hosts by viewModel.hosts.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val terminalRevision by viewModel.terminalRevision.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val ctrlArmed by viewModel.ctrlArmed.collectAsStateWithLifecycle()
    val disconnectConfirmationRequest by viewModel.disconnectConfirmationRequest
        .collectAsStateWithLifecycle()
    val vendorBackgroundAccessRequest by viewModel.vendorBackgroundAccessRequest
        .collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // Fingerprint decisions belong next to the host the user selected, matching
    // the prototype and making it impossible to mistake the dialog for terminal output.
    LaunchedEffect(session.phase) {
        if (session.phase == SessionPhase.FINGERPRINT_REQUIRED && screen != AppScreen.Hosts) {
            viewModel.showHosts()
        }
    }

    KeepShellTheme(darkTheme = screen == AppScreen.Terminal) {
        Box(Modifier.fillMaxSize()) {
            when (val current = screen) {
                AppScreen.Hosts -> HostsScreen(
                    hosts = hosts,
                    session = session,
                    onSettings = viewModel::showSettings,
                    onAdd = { viewModel.openEditor() },
                    onConnect = { host -> viewModel.connect(host, context) },
                    onEdit = viewModel::openEditor,
                    onOpenSession = viewModel::showTerminal
                )

                is AppScreen.Editor -> {
                    BackHandler(onBack = viewModel::showHosts)
                    HostEditorScreen(
                        state = editor,
                        viewModel = viewModel,
                        onBack = viewModel::showHosts
                    )
                }

                AppScreen.Settings -> {
                    BackHandler(onBack = viewModel::showHosts)
                    SettingsScreen(
                        settings = settings,
                        onBack = viewModel::showHosts,
                        onUpdate = viewModel::updateSettings
                    )
                }

                AppScreen.Terminal -> {
                    BackHandler(onBack = viewModel::showHosts)
                    TerminalScreen(
                        state = session,
                        terminalEngine = viewModel.terminalEngine,
                        terminalRevision = terminalRevision,
                        settings = settings,
                        ctrlArmed = ctrlArmed,
                        disconnectConfirmationRequest = disconnectConfirmationRequest,
                        viewModel = viewModel,
                        onBack = viewModel::showHosts
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            )
        }

        session.fingerprintPrompt?.let { prompt ->
            FingerprintDialog(
                state = session,
                prompt = prompt,
                onReject = viewModel::rejectFingerprint,
                onTrust = { viewModel.trustFingerprint(prompt.isChanged) }
            )
        }

        if (vendorBackgroundAccessRequest) {
            AlertDialog(
                onDismissRequest = viewModel::dismissVendorBackgroundAccessRequest,
                title = {
                    Text("允许后台 SSH", fontWeight = FontWeight.Bold)
                },
                text = {
                    Text(
                        "ColorOS 默认会在应用退到后台约 30 秒后冻结网络。请在下一页进入“耗电管理”，打开“允许完全后台行为”，完成后返回 KeepShell。",
                        lineHeight = 21.sp
                    )
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissVendorBackgroundAccessRequest) {
                        Text("暂不")
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.openVendorBackgroundSettings(context) }
                    ) {
                        Text("去设置", color = Signal, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    }
}

@Composable
private fun FingerprintDialog(
    state: SessionState,
    prompt: FingerprintPrompt,
    onReject: () -> Unit,
    onTrust: () -> Unit
) {
    val changed = prompt.isChanged
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (changed) DangerSoft else SignalSoft,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (changed) Icons.Rounded.WarningAmber else Icons.Rounded.Security,
                    contentDescription = null,
                    tint = if (changed) Danger else Signal
                )
            }
        },
        title = {
            Text(
                if (changed) "主机密钥已变更" else "确认主机指纹",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "${state.host?.name.orEmpty()} · ${state.host?.address.orEmpty()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                if (changed) {
                    Text(
                        "服务器提供的密钥与本机记录不一致。请先通过可信渠道核对新指纹；这也可能是中间人攻击。",
                        color = Danger,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    FingerprintBlock(
                        label = "原指纹 · ${prompt.previousAlgorithm.orEmpty()}",
                        value = prompt.previousFingerprint.orEmpty(),
                        background = DangerSoft.copy(alpha = 0.6f)
                    )
                    FingerprintBlock(
                        label = "新指纹 · ${prompt.algorithm}",
                        value = prompt.fingerprint,
                        background = SignalSoft
                    )
                } else {
                    Text(
                        "首次连接此主机。确认后会将指纹安全地保存在本机，后续连接将严格校验。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    FingerprintBlock(
                        label = prompt.algorithm,
                        value = prompt.fingerprint,
                        background = SignalSoft
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onReject) {
                Text("取消连接", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onTrust) {
                Text(
                    if (changed) "更新密钥并连接" else "确认并连接",
                    color = if (changed) Danger else Signal,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

@Composable
private fun FingerprintBlock(
    label: String,
    value: String,
    background: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label.uppercase(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp
            )
            Spacer(Modifier.width(6.dp))
        }
        SelectionContainer {
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}
