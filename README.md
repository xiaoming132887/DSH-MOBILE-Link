# DSH-MOBILE Link

**双端互联的独立源码包**：把手机和电脑之间的 DSH 远程控制与会话同步抽成一个
可复用的小工程。它不包含完整的 DSH-MOBILE 电脑端，也不包含完整的 Android App；
两端的宿主软件可以各自不同，只接这套 link 层即可。

```
DSH-MOBILE Link/
├─ PROTOCOL.md                 协议 v1（端点、鉴权、合并规则）
├─ pc\
│  ├─ sync-server.mjs          纯同步服务端（只暴露 /__dshm/sync/*）
│  ├─ sync-client.mjs          电脑端同步客户端
│  ├─ public-tunnel.mjs        可选：cloudflared 公网 HTTPS 隧道
│  └─ dsh-web-proxy.mjs        可选：DSH WebUI 反向代理（3090/3091）
└─ android\
   ├─ core\...\SessionSync.kt  纯 Kotlin 同步客户端（无 App 依赖）
   ├─ core\...\LinkHost.kt     宿主 App 接入点（sessions 目录等）
   ├─ core\...\LinkPrefs.kt    地址/密码本地保存
   ├─ sample\...\PeerLinkActivity.kt  示例 UI（可复制改造）
   └─ assets\dsh-web-proxy.mjs 可选：手机端 WebUI 反向代理
```

## 它能做什么

- 手机和电脑在**同一局域网**：手机打开电脑的 DSH WebUI，或电脑打开手机 WebUI；
- 不在同一局域网：电脑端用 Cloudflare quick tunnel 生成 HTTPS 地址，手机浏览器直接访问；
- **双向同步** `sessions/**/*.jsonl` 会话记录（只同步会话，不传附件/工作区/凭证）；
- 可选：手机端也跑一个同步服务，电脑主动拉取手机。

## 电脑端快速开始

需要 Node.js 24+。

```bash
# 1. 同步服务（只暴露 sessions，不含 WebUI）
node pc/sync-server.mjs --root ./sessions --port 3093 --pw "long-random-password"

# 2. 客户端（把另一端的会话同步过来）
node pc/sync-client.mjs \
  --url http://192.168.1.23:3090 \
  --code "对方密码" \
  --local ./sessions

# 3. 可选：公网 HTTPS 隧道（cloudflared）
node pc/public-tunnel.mjs --target http://127.0.0.1:3093 --cloudflared ./cloudflared.exe

# 4. 可选：DSH WebUI 反向代理（原 DSH-MOBILE 的 3090/3091 代理）
node pc/dsh-web-proxy.mjs --port 3091 --pw "6-digit-or-long" --upstream 127.0.0.1:49760
```

`dsh-web-proxy.mjs` 需要环境变量 `DSH_HOME`（或 `DSH_SYNC_ROOT` / `DSH_ENGINE_LOG`）
指向 DSH 数据目录；不使用 DSH WebUI 时只需要 `sync-server.mjs`。

## Android 端接入

1. 把 `android/core/src/main/java/app/dsh/mobile/link/` 复制进你的 Android 模块
   （包名可以改）；
2. 在 `Application`/`Activity` 启动时设置宿主：

```kotlin
LinkHostRegistry.host = object : LinkHost {
    override fun sessionsDir() = File(filesDir, "dsh-home/sessions")
    override fun backupDir() = File(filesDir, "dsh-home/sync-conflicts")
    override fun localServiceSummary() = "本机 WebUI: http://...:3090"
}
```

3. 同步：

```kotlin
Thread {
    val result = SessionSync.sync(
        sessionsDir = File(filesDir, "dsh-home/sessions"),
        backupDir = File(filesDir, "dsh-home/sync-conflicts"),
        baseUrl = "https://xxxx.trycloudflare.com",
        code = "电脑端密码",
        direction = "both",
    )
}.start()
```

4. `sample/PeerLinkActivity.kt` 是一个无 DSH 依赖的示例页面，按需复制到宿主 App。

### Android 依赖

- `SessionSync` 只使用 `java.io` / `java.net` / `org.json`（Android 内置）；
- `LinkPrefs` 使用 `SharedPreferences`；
- 示例 Activity 使用标准 `android.widget`，不依赖 Material/DSH App；
- 如果要让手机作为 WebUI 服务端，再使用 `assets/dsh-web-proxy.mjs`（需要 Node 运行时）。

## 和两个完整 App 的关系

- 电脑端完整应用：<https://github.com/xiaoming132887/DSH-MOBILE-Desktop>
- Android 完整应用：<https://github.com/Soodok/Deepseek-Harness-Local-Android>
- Android 双端互联 PR：<https://github.com/Soodok/Deepseek-Harness-Local-Android/pull/105>

这个仓库只保留 **link 层**，方便别人接进自己的电脑程序或 Android App，不需要带走
整个 DSH-MOBILE。

## 安全

见 [PROTOCOL.md](PROTOCOL.md) 与 [SECURITY.md](SECURITY.md)。一句话：
同步通道和 WebUI 代理背后都是完整 DSH Agent，只给可信设备使用；公网必须用
HTTPS + 强密码，禁止直接端口映射。
