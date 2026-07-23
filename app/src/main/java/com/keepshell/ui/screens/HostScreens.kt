package com.keepshell.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepshell.data.AuthMethod
import com.keepshell.data.HostDraft
import com.keepshell.data.HostEntity
import com.keepshell.ssh.SessionPhase
import com.keepshell.ssh.SessionState
import com.keepshell.ui.EditorUiState
import com.keepshell.ui.MainViewModel
import com.keepshell.ui.components.FormSectionTitle
import com.keepshell.ui.components.KeepShellLogo
import com.keepshell.ui.components.PrimaryActionButton
import com.keepshell.ui.components.SecondaryActionButton
import com.keepshell.ui.components.SectionHeader
import com.keepshell.ui.components.SoftIcon
import com.keepshell.ui.components.StatusDot
import com.keepshell.ui.theme.Canvas
import com.keepshell.ui.theme.Danger
import com.keepshell.ui.theme.DangerSoft
import com.keepshell.ui.theme.Ink
import com.keepshell.ui.theme.Line
import com.keepshell.ui.theme.Muted
import com.keepshell.ui.theme.Online
import com.keepshell.ui.theme.Signal
import com.keepshell.ui.theme.SignalSoft
import com.keepshell.ui.theme.Soft
import com.keepshell.ui.theme.SurfaceSoft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HostsScreen(
    hosts: List<HostEntity>,
    session: SessionState,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
    onConnect: (HostEntity) -> Unit,
    onEdit: (Long) -> Unit,
    onOpenSession: () -> Unit
) {
    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KeepShellLogo(compact = true)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "KeepShell",
                            color = Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Rounded.Settings, "设置", tint = Muted)
                    }
                    IconButton(
                        onClick = onAdd,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .background(Signal, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Add, "新增主机", tint = Color.White)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (session.host != null && session.phase != SessionPhase.IDLE) {
                item(key = "active-session") {
                    ActiveSessionBanner(session = session, onClick = onOpenSession)
                }
            }

            item {
                SectionHeader(
                    title = "我的主机",
                    trailing = "${hosts.size} 台"
                )
            }

            if (hosts.isEmpty()) {
                item {
                    EmptyHosts(onAdd)
                }
            } else {
                items(hosts, key = { it.id }) { host ->
                    HostRow(
                        host = host,
                        onClick = { onConnect(host) },
                        onLongClick = { onEdit(host.id) }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 36.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        null,
                        tint = Soft,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("凭据仅保存在此设备", color = Soft, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ActiveSessionBanner(session: SessionState, onClick: () -> Unit) {
    val disconnected = session.phase == SessionPhase.DISCONNECTED || session.phase == SessionPhase.FAILED
    val background = if (disconnected) DangerSoft else SignalSoft
    val accent = if (disconnected) Danger else Signal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .height(118.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxSize()
                .background(accent)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    session.isBusy -> CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Signal
                    )
                    disconnected -> StatusDot(Danger)
                    else -> StatusDot(Online)
                }
                Spacer(Modifier.width(7.dp))
                Text(
                    when (session.phase) {
                        SessionPhase.CONNECTING -> "正在连接"
                        SessionPhase.AUTHENTICATING -> "正在认证"
                        SessionPhase.FINGERPRINT_REQUIRED -> "等待指纹确认"
                        SessionPhase.CONNECTED -> "正在连接"
                        SessionPhase.DISCONNECTED -> "连接已断开"
                        SessionPhase.FAILED -> "连接失败"
                        SessionPhase.IDLE -> "未连接"
                    },
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp
                )
            }
            Text(session.host?.name.orEmpty(), color = Ink, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                if (session.phase == SessionPhase.CONNECTED) {
                    "${session.host?.displayAddress.orEmpty()} · 已连接 ${sessionElapsed(session.startedAt)}"
                } else {
                    session.host?.displayAddress.orEmpty()
                },
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Row(
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (disconnected) "查看会话" else "返回会话",
                color = accent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HostRow(host: HostEntity, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIcon(Icons.Rounded.Dns)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(host.name, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                host.displayAddress,
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        if (host.lastConnectedAt != null) {
            Text(relativeTime(host.lastConnectedAt), color = Soft, fontSize = 11.sp)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            "连接",
            tint = Soft,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(color = Line)
}

@Composable
private fun EmptyHosts(onAdd: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 28.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SoftIcon(Icons.Rounded.Terminal, Modifier.size(52.dp), tint = Signal)
        Spacer(Modifier.height(18.dp))
        Text("还没有主机", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.height(6.dp))
        Text("添加服务器后即可建立 SSH 会话", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(22.dp))
        PrimaryActionButton(
            text = "添加第一台主机",
            onClick = onAdd,
            icon = Icons.Rounded.Add
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostEditorScreen(
    state: EditorUiState,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }
    var passphraseVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取文件")
                    require(bytes.size <= 512 * 1024) { "私钥文件过大" }
                    String(bytes, Charsets.UTF_8)
                }
            }
            result.onSuccess { key ->
                viewModel.updateDraft {
                    it.copy(
                        privateKey = key,
                        privateKeyName = uri.lastPathSegment?.substringAfterLast('/') ?: "OpenSSH 私钥"
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = Canvas,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                    }
                },
                title = {
                    Text(
                        if (state.draft.id == 0L) "新增主机" else "主机配置",
                        modifier = Modifier.fillMaxWidth(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                },
                actions = {
                    TextButton(onClick = { viewModel.saveHost() }) {
                        Text("保存", color = Signal, fontWeight = FontWeight.SemiBold)
                    }
                }
            )
        }
    ) { padding ->
        if (state.loading) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Signal)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            FormSectionTitle("连接信息")
            Column(Modifier.background(Color.White)) {
                EditorField(
                    label = "名称",
                    value = state.draft.name,
                    error = state.errors["name"],
                    onValueChange = { value -> viewModel.updateDraft { it.copy(name = value) } }
                )
                EditorField(
                    label = "地址",
                    value = state.draft.address,
                    error = state.errors["address"],
                    onValueChange = { value -> viewModel.updateDraft { it.copy(address = value) } }
                )
                Row(Modifier.fillMaxWidth()) {
                    EditorField(
                        label = "端口",
                        value = state.draft.port,
                        error = state.errors["port"],
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        onValueChange = { value ->
                            viewModel.updateDraft { it.copy(port = value.filter(Char::isDigit)) }
                        }
                    )
                    Box(
                        Modifier
                            .width(1.dp)
                            .height(72.dp)
                            .background(Line)
                    )
                    EditorField(
                        label = "用户名",
                        value = state.draft.username,
                        error = state.errors["username"],
                        modifier = Modifier.weight(1f),
                        onValueChange = { value -> viewModel.updateDraft { it.copy(username = value) } }
                    )
                }
            }

            FormSectionTitle("认证")
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(top = 14.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                ) {
                    AuthMethod.entries.forEachIndexed { index, method ->
                        SegmentedButton(
                            selected = state.draft.authMethod == method,
                            onClick = { viewModel.updateDraft { it.copy(authMethod = method) } },
                            shape = SegmentedButtonDefaults.itemShape(index, AuthMethod.entries.size),
                            label = {
                                Text(if (method == AuthMethod.PASSWORD) "密码" else "私钥")
                            }
                        )
                    }
                }

                if (state.draft.authMethod == AuthMethod.PASSWORD) {
                    EditorField(
                        label = "密码",
                        value = state.draft.password,
                        error = state.errors["password"],
                        visualTransformation = if (passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            TextButton(onClick = { passwordVisible = !passwordVisible }) {
                                Text(if (passwordVisible) "隐藏" else "显示", fontSize = 12.sp)
                            }
                        },
                        onValueChange = { value -> viewModel.updateDraft { it.copy(password = value) } }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyPicker.launch(
                                    arrayOf(
                                        "application/x-pem-file",
                                        "application/octet-stream",
                                        "text/plain"
                                    )
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SoftIcon(Icons.Rounded.Key)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.draft.privateKeyName ?: "选择 OpenSSH 私钥",
                                color = Ink,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (state.draft.privateKey.isBlank()) "未选择文件" else "私钥已载入并将加密保存",
                                color = if (state.errors["privateKey"] != null) Danger else Muted,
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Soft)
                    }
                    HorizontalDivider(color = Line)
                    EditorField(
                        label = "密钥口令（可选）",
                        value = state.draft.passphrase,
                        visualTransformation = if (passphraseVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailing = {
                            TextButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                Text(if (passphraseVisible) "隐藏" else "显示", fontSize = 12.sp)
                            }
                        },
                        onValueChange = { value -> viewModel.updateDraft { it.copy(passphrase = value) } }
                    )
                }
            }

            FormSectionTitle("会话")
            Column(Modifier.background(Color.White)) {
                ChoiceField(
                    label = "保活间隔",
                    value = state.draft.keepAliveSeconds,
                    options = listOf(15, 30, 60, 120, 300),
                    valueLabel = { "$it 秒" },
                    onSelected = { value ->
                        viewModel.updateDraft { it.copy(keepAliveSeconds = value) }
                    }
                )
                ChoiceField(
                    label = "连接超时",
                    value = state.draft.connectTimeoutSeconds,
                    options = listOf(15, 30, 60),
                    valueLabel = { "$it 秒" },
                    onSelected = { value ->
                        viewModel.updateDraft { it.copy(connectTimeoutSeconds = value) }
                    }
                )
            }

            SecondaryActionButton(
                text = state.testResult ?: "测试连接",
                onClick = viewModel::testHost,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                icon = if (state.testResult?.contains("可达") == true) {
                    Icons.Rounded.CheckCircle
                } else {
                    Icons.Rounded.Terminal
                },
                loading = state.testing
            )

            if (state.draft.id > 0) {
                Button(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DangerSoft,
                        contentColor = Danger
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.DeleteOutline, null)
                    Spacer(Modifier.width(8.dp))
                    Text("删除此主机")
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除主机？") },
            text = { Text("主机配置和本机加密凭据将一并删除。") },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消") }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteHost()
                }) {
                    Text("删除", color = Danger)
                }
            }
        )
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(if (error == null) 72.dp else 88.dp),
        label = { Text(label, fontSize = 12.sp) },
        supportingText = error?.let { { Text(it, color = Danger, fontSize = 11.sp) } },
        isError = error != null,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        trailingIcon = trailing,
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,
            focusedIndicatorColor = Signal,
            unfocusedIndicatorColor = Line,
            cursorColor = Signal
        )
    )
}

@Composable
private fun ChoiceField(
    label: String,
    value: Int,
    options: List<Int>,
    valueLabel: (Int) -> String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Ink, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(valueLabel(value), color = Muted, fontSize = 13.sp)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                null,
                tint = Soft,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(valueLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
    HorizontalDivider(color = Line)
}

private fun relativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时"
        diff < 7 * 86_400_000 -> "${diff / 86_400_000} 天"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

@Composable
private fun sessionElapsed(startedAt: Long?): String {
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
