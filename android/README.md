# Android integration

This directory is a **library + sample**, not the full DSH Mobile app.

## Module layout

```
core/
  src/main/java/app/dsh/mobile/link/
    LinkHost.kt
    LinkPrefs.kt
    SessionSync.kt
  build.gradle.kts
  src/main/AndroidManifest.xml
sample/
  src/main/java/app/dsh/mobile/link/sample/PeerLinkActivity.kt
assets/
  dsh-web-proxy.mjs
```

## 1. Add the core

Copy `core/src/main/java/app/dsh/mobile/link/` into your app module (or make it a
library module using the included `core/build.gradle.kts` as a starting point).

No external Kotlin dependencies are required: `SessionSync` uses `java.io`,
`java.net` and Android's built-in `org.json`.

## 2. Supply host paths

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LinkHostRegistry.host = object : LinkHost {
            override fun sessionsDir() = File(filesDir, "dsh-home/sessions")
            override fun backupDir() = File(filesDir, "dsh-home/sync-conflicts")
            override fun localServiceSummary() = "本机 WebUI: http://192.168.1.23:3090"
        }
    }
}
```

## 3. Sync

```kotlin
Thread {
    val result = SessionSync.sync(
        sessionsDir = File(filesDir, "dsh-home/sessions"),
        backupDir = File(filesDir, "dsh-home/sync-conflicts"),
        baseUrl = "http://192.168.1.10:3091", // or https://*.trycloudflare.com
        code = "password from the PC side",
        direction = "both", // both | pull | push
    )
}.start()
```

## 4. UI

`sample/PeerLinkActivity.kt` is a standalone `Activity` with:

- PC URL + password;
- open PC DSH in the system browser;
- bidirectional / pull / push sync;
- optional local-service summary from `LinkHost`.

Copy it into your app and adapt colors/strings. There is no `ThemedActivity`,
`DshApp`, `LanWebProxy` or `RemoteAccess` dependency.

## 5. Cleartext HTTP (LAN)

Android 9+ blocks `http://` by default. If your PC side uses plain LAN HTTP,
add a network security config that permits cleartext for your chosen host, or
use the Cloudflare HTTPS URL. Do **not** enable cleartext globally unless you
understand the risk.

## 6. Phone as server (optional)

`assets/dsh-web-proxy.mjs` is the Node reverse proxy used by DSH Mobile. It
proxies the DSH WebUI and exposes the same `/__dshm/sync/*` endpoints. It needs
a Node runtime in the host app; most integrations only need the PC side server.

## Build

Add a manifest entry if you use the sample:

```xml
<activity
    android:name="app.dsh.mobile.link.sample.PeerLinkActivity"
    android:exported="false"
    android:windowSoftInputMode="adjustResize" />
```
