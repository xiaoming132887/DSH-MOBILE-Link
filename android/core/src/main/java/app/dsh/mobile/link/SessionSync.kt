package app.dsh.mobile.link

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * DSH-MOBILE Link · sync protocol v1 client.
 *
 * Standalone: the caller supplies the local sessions directory and a backup
 * directory. No dependency on the DSH Android app or its EngineConfig.
 *
 * Protocol:
 *   GET  /__dshm/sync/ping
 *   GET  /__dshm/sync/manifest
 *   GET  /__dshm/sync/file?path=<relative>
 *   PUT  /__dshm/sync/file?path=<relative>
 * Auth: x-dshm-sync: <password>
 */
object SessionSync {
    private const val MAX_BYTES = 64 * 1024 * 1024

    data class Result(
        val downloaded: Int,
        val uploaded: Int,
        val conflicts: Int,
        val skipped: Int,
        val remoteFiles: Int,
        val localFiles: Int,
    )

    private data class Info(
        val path: String,
        val size: Long,
        val mtimeMs: Long,
        val sha256: String,
    )

    /**
     * @param sessionsDir local `sessions` directory (created if missing)
     * @param backupDir conflict backups (created if missing)
     * @param direction both | pull | push
     * @param log optional diagnostic sink (Android Log, println, etc.)
     */
    fun sync(
        sessionsDir: File,
        backupDir: File,
        baseUrl: String,
        code: String,
        direction: String = "both",
        log: (String) -> Unit = {},
    ): Result {
        val base = baseUrl.trim().trimEnd('/')
        require(base.startsWith("http://") || base.startsWith("https://")) { "电脑地址必须以 http:// 或 https:// 开头" }
        require(code.isNotBlank()) { "密码不能为空" }
        require(direction in setOf("both", "pull", "push")) { "direction must be both, pull or push" }

        sessionsDir.mkdirs()
        backupDir.mkdirs()

        val ping = JSONObject(getText(base, "/__dshm/sync/ping", code))
        require(ping.optInt("version", 0) == 1) { "电脑端同步协议版本不匹配" }

        val remote = parseManifest(getText(base, "/__dshm/sync/manifest", code))
        val local = localManifest(sessionsDir)
        val localByPath = local.associateBy { it.path }
        val remoteByPath = remote.associateBy { it.path }
        val all = (localByPath.keys + remoteByPath.keys).sorted()

        var downloaded = 0
        var uploaded = 0
        var conflicts = 0
        var skipped = 0

        for (rel in all) {
            val l = localByPath[rel]
            val r = remoteByPath[rel]
            if (l != null && r != null && l.sha256 == r.sha256) {
                skipped++
                continue
            }

            if (direction != "push" && r != null && l == null) {
                writeLocal(sessionsDir, rel, getBytes(base, "/__dshm/sync/file?path=" + urlEncode(rel), code))
                downloaded++
                continue
            }
            if (direction != "pull" && l != null && r == null) {
                putBytes(base, rel, readLocal(sessionsDir, rel), code)
                uploaded++
                continue
            }

            if (l != null && r != null) {
                val localBytes = readLocal(sessionsDir, rel)
                val remoteBytes = getBytes(base, "/__dshm/sync/file?path=" + urlEncode(rel), code)

                // JSONL sessions are append-only: a strict prefix means the
                // longer side continued the same session.
                if (direction != "push" && isPrefix(localBytes, remoteBytes)) {
                    writeLocal(sessionsDir, rel, remoteBytes)
                    downloaded++
                    continue
                }
                if (direction != "pull" && isPrefix(remoteBytes, localBytes)) {
                    putBytes(base, rel, localBytes, code)
                    uploaded++
                    continue
                }

                conflicts++
                val backup = File(backupDir, rel.replace('/', File.separatorChar) + ".${System.currentTimeMillis()}.local.jsonl")
                backup.parentFile?.mkdirs()
                backup.writeBytes(localBytes)
                log("conflict $rel; backup=${backup.absolutePath}")
                if (direction != "pull" && r.mtimeMs > l.mtimeMs) {
                    writeLocal(sessionsDir, rel, remoteBytes)
                    downloaded++
                } else if (direction != "pull") {
                    putBytes(base, rel, localBytes, code)
                    uploaded++
                }
            }
        }

        val result = Result(downloaded, uploaded, conflicts, skipped, remote.size, local.size)
        log("sync done: $result")
        return result
    }

    // ---------------------------------------------------------------- manifest

    private fun parseManifest(text: String): List<Info> {
        val files = JSONObject(text).getJSONArray("files")
        val out = ArrayList<Info>(files.length())
        for (i in 0 until files.length()) {
            val o = files.getJSONObject(i)
            out.add(Info(o.getString("path"), o.getLong("size"), o.getLong("mtimeMs"), o.getString("sha256")))
        }
        return out
    }

    private fun localManifest(root: File): List<Info> {
        val out = ArrayList<Info>()
        fun walk(dir: File) {
            val entries = dir.listFiles() ?: return
            for (f in entries) {
                if (f.name.startsWith(".") || f.name.endsWith(".tmp")) continue
                if (f.isDirectory) {
                    walk(f)
                } else if (f.isFile && f.name.endsWith(".jsonl")) {
                    val bytes = f.readBytes()
                    out.add(
                        Info(
                            f.relativeTo(root).path.replace(File.separatorChar, '/'),
                            bytes.size.toLong(),
                            f.lastModified(),
                            sha256(bytes),
                        )
                    )
                }
            }
        }
        walk(root)
        return out.sortedBy { it.path }
    }

    // ---------------------------------------------------------------- files

    private fun readLocal(root: File, rel: String): ByteArray =
        File(root, rel.replace('/', File.separatorChar)).readBytes()

    private fun writeLocal(root: File, rel: String, bytes: ByteArray) {
        val f = File(root, rel.replace('/', File.separatorChar))
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp-" + System.nanoTime())
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) {
            f.writeBytes(bytes)
            tmp.delete()
        }
    }

    private fun isPrefix(short: ByteArray, long: ByteArray): Boolean {
        if (short.size > long.size) return false
        for (i in short.indices) if (short[i] != long[i]) return false
        return true
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    // ---------------------------------------------------------------- HTTP

    private fun open(base: String, path: String, code: String, method: String): HttpURLConnection {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 10_000
        c.readTimeout = 45_000
        c.setRequestProperty("x-dshm-sync", code)
        c.setRequestProperty("Accept", "application/json, application/x-ndjson")
        return c
    }

    private fun getText(base: String, path: String, code: String): String {
        val c = open(base, path, code, "GET")
        try {
            val status = c.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP $status: ${c.errorStream?.bufferedReader()?.readText().orEmpty().take(200)}")
            return c.inputStream.bufferedReader().readText()
        } finally {
            c.disconnect()
        }
    }

    private fun getBytes(base: String, path: String, code: String): ByteArray {
        val c = open(base, path, code, "GET")
        try {
            val status = c.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
            if (c.contentLengthLong > MAX_BYTES) throw IllegalStateException("会话文件超过 64 MiB")
            val bytes = c.inputStream.readBytes()
            if (bytes.size > MAX_BYTES) throw IllegalStateException("会话文件超过 64 MiB")
            return bytes
        } finally {
            c.disconnect()
        }
    }

    private fun putBytes(base: String, rel: String, bytes: ByteArray, code: String) {
        val c = open(base, "/__dshm/sync/file?path=" + urlEncode(rel), code, "PUT")
        try {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/x-ndjson")
            c.outputStream.use { it.write(bytes) }
            val status = c.responseCode
            if (status !in 200..299) throw IllegalStateException("HTTP $status")
        } finally {
            c.disconnect()
        }
    }
}
