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
- SSH keepalive、可选增强保活、PTY resize、10,000 行内存滚动缓冲、扩展控制键。
- 断线后保留只读内容，不自动重连；只有用户点击后才创建新 Session ID。

## 构建

需要 JDK 17、Android SDK 35 与 Gradle 8.11.1。可直接在 Android Studio 中打开
`code` 目录，或使用本机 Gradle：

```bash
gradle --no-daemon --max-workers=1 :app:assembleDebug
```

调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 安全边界

- 凭据与导入私钥不进入 Room、日志或通知。
- 主机密钥变化不会被自动接受。
- 不保存命令历史或终端缓冲到磁盘。
- `FLAG_SECURE` 可在设置中开启。

## 当前范围

该版本专注单活动会话。终端引擎采用流式 ANSI/VT 状态机，覆盖日常 Shell
所需的 SGR 颜色、光标行、退格、清屏和基本 CSI 操作；复杂 `vim`/`tmux`
兼容仍需在下一阶段接入经过许可证与中文宽字符验证的完整 xterm 引擎。
