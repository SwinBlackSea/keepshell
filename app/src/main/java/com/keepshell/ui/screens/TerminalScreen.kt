package com.keepshell.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.keepshell.data.AppSettings
import com.keepshell.ssh.SessionPhase
import com.keepshell.ssh.SessionState
import com.keepshell.ssh.TerminalEngine
import com.keepshell.ui.MainViewModel
import com.keepshell.ui.components.PrimaryActionButton
import com.keepshell.ui.components.RemoteTerminalView
import com.keepshell.ui.components.StatusDot
import com.keepshell.ui.theme.Danger
import com.keepshell.ui.theme.Online
import com.keepshell.ui.theme.Terminal
import com.keepshell.ui.theme.TerminalGreen
import com.keepshell.ui.theme.TerminalLine
import com.keepshell.ui.theme.TerminalMuted
import com.keepshell.ui.theme.TerminalRaised
import com.keepshell.ui.theme.TerminalText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TerminalScreen(
    state: SessionState,
    terminalEngine: TerminalEngine,
    terminalRevision: Long,
    settings: AppSettings,
    ctrlArmed: Boolean,
    disconnectConfirmationRequest: Long,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(disconnectConfirmationRequest) {
        if (disconnectConfirmationRequest > 0) confirmDisconnect = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Terminal)
    ) {
        TerminalHeader(
            state = state,
            onBack = onBack,
            onMenu = { menuOpen = true },
            menu = {
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    state.host?.name ?: "当前会话",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Session ${state.sessionId ?: "—"}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp
                                )
                            }
                        },
                        leadingIcon = { Icon(Icons.Rounded.Dns, null) },
                        onClick = {},
                        enabled = false
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("复制主机地址") },
                        leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                        onClick = {
                            val address = state.host?.displayAddress.orEmpty()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("SSH 地址", address))
                            menuOpen = false
                        }
                    )
                    if (state.phase == SessionPhase.CONNECTED) {
                        DropdownMenuItem(
                            text = { Text("断开连接", color = Danger) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, tint = Danger)
                            },
                            onClick = {
                                menuOpen = false
                                confirmDisconnect = true
                            }
                        )
                    }
                }
            }
        )

        if (state.phase == SessionPhase.DISCONNECTED || state.phase == SessionPhase.FAILED) {
            DisconnectedBanner(state)
        }

        when (state.phase) {
            SessionPhase.CONNECTING, SessionPhase.AUTHENTICATING -> {
                ConnectingPanel(state.phase)
            }
            SessionPhase.CONNECTED -> {
                ConnectedTerminal(
                    terminalEngine = terminalEngine,
                    terminalRevision = terminalRevision,
                    fontSize = settings.terminalFontSize,
                    ctrlArmed = ctrlArmed,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f)
                )
            }
            SessionPhase.FINGERPRINT_REQUIRED -> {
                Box(Modifier.weight(1f)) {
                    TerminalOutput(
                        terminalEngine = terminalEngine,
                        terminalRevision = terminalRevision,
                        fontSize = settings.terminalFontSize,
                        readOnly = true,
                        viewModel = viewModel
                    )
                }
            }
            else -> {
                Box(Modifier.weight(1f)) {
                    TerminalOutput(
                        terminalEngine = terminalEngine,
                        terminalRevision = terminalRevision,
                        fontSize = settings.terminalFontSize,
                        readOnly = state.phase != SessionPhase.CONNECTED,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        if (
            state.phase == SessionPhase.DISCONNECTED ||
            state.phase == SessionPhase.FAILED
        ) {
            ReconnectPanel(onReconnect = viewModel::reconnect)
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("断开当前会话？") },
            text = { Text("远端正在运行的前台程序可能会停止。终端内容会保留为只读。") },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) {
                    Text("继续保持")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDisconnect = false
                    viewModel.disconnect()
                }) {
                    Text("断开", color = Danger)
                }
            }
        )
    }
}

@Composable
private fun ConnectedTerminal(
    terminalEngine: TerminalEngine,
    terminalRevision: Long,
    fontSize: Int,
    ctrlArmed: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            TerminalOutput(
                terminalEngine = terminalEngine,
                terminalRevision = terminalRevision,
                fontSize = fontSize,
                readOnly = false,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
            ExtraKeys(
                ctrlArmed = ctrlArmed,
                onKey = viewModel::sendExtraKey
            )
        },
        modifier = modifier.fillMaxWidth()
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val keyHeight = 56.dp.roundToPx().coerceAtMost(height)
        val terminalHeight = (height - keyHeight).coerceAtLeast(0)
        val terminal = measurables[0].measure(
            Constraints.fixed(width, terminalHeight)
        )
        val keys = measurables[1].measure(
            Constraints.fixed(width, keyHeight)
        )
        layout(width, height) {
            terminal.placeRelative(0, 0)
            keys.placeRelative(0, terminalHeight)
        }
    }
}

@Composable
private fun TerminalHeader(
    state: SessionState,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    menu: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Terminal)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回主机", tint = TerminalText)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                state.host?.name ?: "终端",
                color = TerminalText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val offline = state.phase == SessionPhase.DISCONNECTED || state.phase == SessionPhase.FAILED
                StatusDot(
                    color = if (offline) Color(0xFFE27169) else Online,
                    pulse = false
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = terminalStatus(state),
                    color = if (offline) Color(0xFFE98A83) else TerminalMuted,
                    fontSize = 10.sp
                )
            }
        }
        Box {
            IconButton(onClick = onMenu) {
                Icon(Icons.Rounded.MoreVert, "会话菜单", tint = TerminalText)
            }
            menu()
        }
    }
    HorizontalDivider(color = TerminalLine)
}

@Composable
private fun DisconnectedBanner(state: SessionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF321F1E))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Color(0xFFFF8D86), modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (state.phase == SessionPhase.FAILED) "连接失败" else "会话已结束",
                color = Color(0xFFFFC3BE),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Text(
                "${state.reason ?: "连接已关闭"} · ${formatClock(state.endedAt)}",
                color = Color(0xFFB98E8A),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ConnectingPanel(phase: SessionPhase) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Terminal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = TerminalGreen,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (phase == SessionPhase.AUTHENTICATING) "正在认证…" else "正在建立 SSH 会话…",
            color = TerminalText,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(6.dp))
        Text("不会在后台创建额外会话", color = TerminalMuted, fontSize = 10.sp)
    }
}

@Composable
private fun TerminalOutput(
    terminalEngine: TerminalEngine,
    terminalRevision: Long,
    fontSize: Int,
    readOnly: Boolean,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Terminal)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        AndroidView(
            factory = { context -> RemoteTerminalView(context) },
            update = { terminalView ->
                terminalView.bind(
                    terminalEngine = terminalEngine,
                    revision = terminalRevision,
                    newFontSizeSp = fontSize,
                    enabled = !readOnly,
                    textInput = viewModel::sendTerminalText,
                    rawInput = viewModel::sendTerminalBytes,
                    terminalResize = viewModel::resizeTerminal
                )
            },
            modifier = Modifier
                .fillMaxSize()
        )
    }
}

@Composable
private fun ExtraKeys(
    ctrlArmed: Boolean,
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .requiredHeight(56.dp)
            .background(TerminalRaised)
            .padding(horizontal = 7.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        listOf("ESC", "CTRL", "TAB", "↑", "↓", "←", "→").forEach { key ->
            TextButton(
                onClick = { onKey(key) },
                modifier = Modifier
                    .width(if (key.length > 1) 52.dp else 42.dp)
                    .height(42.dp)
                    .background(
                        if (key == "CTRL" && ctrlArmed) TerminalGreen.copy(alpha = 0.2f)
                        else Color(0xFF222B2D),
                        RoundedCornerShape(5.dp)
                    )
            ) {
                Text(
                    key,
                    color = if (key == "CTRL" && ctrlArmed) TerminalGreen else TerminalText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ReconnectPanel(onReconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TerminalRaised)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PrimaryActionButton(
            text = "重新连接",
            onClick = onReconnect,
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Refresh
        )
        Spacer(Modifier.height(8.dp))
        Text("不会自动创建新会话", color = TerminalMuted, fontSize = 10.sp)
    }
}

@Composable
private fun terminalStatus(state: SessionState): String {
    return when (state.phase) {
        SessionPhase.CONNECTED -> "已连接 · ${elapsed(state.startedAt)}"
        SessionPhase.CONNECTING -> "正在连接"
        SessionPhase.AUTHENTICATING -> "正在认证"
        SessionPhase.FINGERPRINT_REQUIRED -> "等待指纹确认"
        SessionPhase.DISCONNECTED -> "连接已断开"
        SessionPhase.FAILED -> "连接失败"
        SessionPhase.IDLE -> "未连接"
    }
}

@Composable
private fun elapsed(startedAt: Long?): String {
    var seconds by remember(startedAt) { mutableLongStateOf(0L) }
    LaunchedEffect(startedAt) {
        while (true) {
            seconds = (
                (System.currentTimeMillis() - (startedAt ?: System.currentTimeMillis())) / 1_000
                )
                .coerceAtLeast(0)
            delay(1_000)
        }
    }
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatClock(timestamp: Long?): String =
    timestamp?.let { SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(it)) } ?: "--:--:--"
