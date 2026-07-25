package com.keepshell.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepshell.BuildConfig
import com.keepshell.data.AppSettings
import com.keepshell.ssh.BackgroundConnectionPolicy
import com.keepshell.ui.components.FormSectionTitle
import com.keepshell.ui.theme.Canvas
import com.keepshell.ui.theme.Ink
import com.keepshell.ui.theme.Line
import com.keepshell.ui.theme.Muted
import com.keepshell.ui.theme.Signal
import com.keepshell.ui.theme.SignalSoft
import com.keepshell.ui.theme.Soft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit
) {
    val context = LocalContext.current

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
                    Text("设置", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            FormSectionTitle("会话")
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                SwitchSettingRow(
                    title = "增强保活",
                    detail = "连接期间保持 CPU 唤醒，提高锁屏稳定性并增加耗电",
                    checked = settings.enhancedKeepAlive,
                    onCheckedChange = { enabled ->
                        onUpdate { it.copy(enhancedKeepAlive = enabled) }
                    }
                )
                HorizontalDivider(color = Line)
                SwitchSettingRow(
                    title = "截图保护",
                    detail = "阻止终端出现在截图和最近任务预览",
                    checked = settings.screenshotProtection,
                    onCheckedChange = { enabled ->
                        onUpdate { it.copy(screenshotProtection = enabled) }
                    }
                )
                HorizontalDivider(color = Line)
                SettingLinkRow(
                    icon = Icons.Rounded.BatterySaver,
                    title = "系统电池设置",
                    detail = if (
                        BackgroundConnectionPolicy.requiresVendorBackgroundAccess()
                    ) {
                        "进入耗电管理，开启“允许完全后台行为”"
                    } else if (BackgroundConnectionPolicy.isUnrestricted(context)) {
                        "已允许后台运行"
                    } else {
                        "允许后台运行，防止 SSH 会话被系统冻结"
                    },
                    onClick = {
                        if (BackgroundConnectionPolicy.requiresVendorBackgroundAccess()) {
                            BackgroundConnectionPolicy.openAppSettings(context)
                        } else if (!BackgroundConnectionPolicy.requestUnrestricted(context)) {
                            BackgroundConnectionPolicy.openAppSettings(context)
                        }
                    }
                )
            }

            FormSectionTitle("终端")
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
            ) {
                ChoiceSettingRow(
                    title = "字号",
                    value = settings.terminalFontSize,
                    options = listOf(12, 14, 16, 18, 20),
                    label = { "$it sp" },
                    onSelected = { size ->
                        onUpdate { it.copy(terminalFontSize = size) }
                    }
                )
                HorizontalDivider(color = Line)
                ChoiceSettingRow(
                    title = "滚动缓冲",
                    value = settings.scrollbackLines,
                    options = listOf(5_000, 10_000, 20_000),
                    label = { "%,d 行".format(it) },
                    onSelected = { count ->
                        onUpdate { it.copy(scrollbackLines = count) }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(SignalSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = null,
                        tint = Signal,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("KeepShell", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(
                        "版本 ${BuildConfig.VERSION_NAME} · 数据仅保存在此设备",
                        color = Soft,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Signal
            )
        )
    }
}

@Composable
private fun SettingLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Muted, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Icon(
            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = Soft,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ChoiceSettingRow(
    title: String,
    value: Int,
    options: List<Int>,
    label: (Int) -> String,
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
            Text(title, color = Ink, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(label(value), color = Muted, fontSize = 13.sp)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = Soft,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
