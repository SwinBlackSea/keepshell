# KeepShell

KeepShell 是一个 Android 8.0+ 原生 SSH 客户端实现，依据仓库中的
`KeepShell-PRD.md` 和 `keepshell-design/` 原型开发。

## 已实现

- Jetpack Compose 主机列表、主机编辑、设置、首次指纹确认、终端和断线只读界面。
- Room 主机配置；密码、私钥和口令使用 Android Keystore 主密钥 + AES-GCM 加密。
- 密码与 OpenSSH 私钥认证、首次连接 TOFU、已知主机严格校验、密钥变化阻断。
- 启动型 + 绑定型前台服务持有唯一 JSch Session 和 Shell Channel。
- Activity 重建或离开后不关闭会话；通知提供返回终端和主动断开入口。
- 通知中的断开入口会先返回 App 二次确认，避免误触终止远端程序。
- SSH keepalive、默认开启且可关闭的增强保活、PTY resize、10,000 行内存滚动缓冲、扩展控制键。
- 终端输入直接发送给远端 PTY，不使用本地命令输入框；Shell、Codex、vim 等程序自行处理回显、光标和编辑。
- 基于 Termux Terminal Libraries 的 xterm/VT 仿真，支持备用屏幕、颜色、中文宽字符、滚动、粘贴和 TUI 动态重绘。
- 键盘显示/隐藏会同步调整远端 PTY 行列数，保持 SSH 会话不变并避免终端页面跳动。
- 前台会话被系统回收后恢复连接意图；断线页在服务重建后仍可直接重新连接。
- 断线后保留只读内容，不自动重连；只有用户点击后才创建新 Session ID。

## 构建

需要 JDK 17、Android SDK 35 与 Gradle 8.11.1。可直接在 Android Studio 中打开
`code` 目录，或使用本机 Gradle：

```bash
gradle --no-daemon --max-workers=1 :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。当前开发版本为
`0.1.2`。

## 安全边界

- 凭据与导入私钥不进入 Room、日志或通知。
- 主机密钥变化不会被自动接受。
- 不保存命令历史或终端缓冲到磁盘。
- `FLAG_SECURE` 可在设置中开启。

## 当前范围

该版本专注单活动会话。终端已使用完整的 xterm/VT 屏幕模型，可运行普通
Shell 与 Codex 等全屏 TUI；当前不提供多标签会话和端口转发。
