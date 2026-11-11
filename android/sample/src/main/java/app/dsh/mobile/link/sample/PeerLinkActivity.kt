package app.dsh.mobile.link.sample

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.dsh.mobile.link.LinkHostRegistry
import app.dsh.mobile.link.LinkPrefs
import app.dsh.mobile.link.SessionSync
import java.io.File

/**
 * Minimal sample UI for the DSH-MOBILE Link core.
 *
 * The host app can copy this Activity, or build its own UI and call
 * [SessionSync.sync] directly. There is no dependency on the DSH Android app.
 */
class PeerLinkActivity : Activity() {

    private lateinit var urlInput: EditText
    private lateinit var codeInput: EditText
    private lateinit var status: TextView
    private lateinit var prefs: LinkPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = LinkPrefs(this)
        setContentView(buildUi())
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(32))
            setBackgroundColor(Color.parseColor("#0f1115"))
        }

        root.addView(TextView(this).apply {
            text = "双端互联"
            textSize = 24f
            setTextColor(Color.WHITE)
        })
        root.addView(note("手机连接电脑 DSH，或同步两端的会话记录。电脑端先开启「远程连接」。"))

        val connect = section("连接电脑") {
            urlInput = EditText(this@PeerLinkActivity).apply {
                hint = "http://192.168.1.10:3091 或 https://xxx.trycloudflare.com"
                setText(prefs.baseUrl)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setSingleLine(true)
            }
            codeInput = EditText(this@PeerLinkActivity).apply {
                hint = "电脑端连接密码"
                setText(prefs.password)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSingleLine(true)
            }
            addView(urlInput, matchWrap().apply { topMargin = dp(10) })
            addView(codeInput, matchWrap().apply { topMargin = dp(6) })
            addView(button("在浏览器打开电脑 DSH") {
                val url = normalizedUrl() ?: return@button
                save()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }, matchWrap().apply { topMargin = dp(10) })
        }
        root.addView(connect)

        val sync = section("同步会话") {
            status = TextView(this@PeerLinkActivity).apply {
                text = "未同步"
                textSize = 12f
                setTextColor(Color.parseColor("#95a1b5"))
                setPadding(0, dp(8), 0, dp(4))
            }
            addView(status, matchWrap())
            val row = LinearLayout(this@PeerLinkActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(button("双向") { runSync("both") }, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(space(8))
            row.addView(button("只拉取") { runSync("pull") }, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(space(8))
            row.addView(button("只推送") { runSync("push") }, LinearLayout.LayoutParams(0, dp(44), 1f))
            addView(row, matchWrap().apply { topMargin = dp(6) })
            addView(note("只同步 sessions 目录下的 .jsonl；不传附件、工作区文件、凭证和插件。"))
        }
        root.addView(sync)

        val host = LinkHostRegistry.host
        if (host != null && host.localServiceSummary().isNotBlank()) {
            root.addView(section("本机服务") {
                addView(note(host.localServiceSummary()), matchWrap())
            })
        }

        return ScrollView(this).apply { addView(root) }
    }

    private fun runSync(direction: String) {
        val url = normalizedUrl() ?: return
        val code = codeInput.text.toString().trim()
        if (code.isBlank()) return toast("先填电脑端连接密码")
        save()
        status.text = "同步中…"
        val host = LinkHostRegistry.host
        val sessions = host?.sessionsDir() ?: File(filesDir, "sessions")
        val backup = host?.backupDir() ?: File(filesDir, "sync-conflicts")
        Thread({
            val result = runCatching {
                SessionSync.sync(sessions, backup, url, code, direction) { line -> android.util.Log.d("DSH-Link", line) }
            }
            runOnUiThread {
                result.onSuccess {
                    status.text = "完成：下载 ${it.downloaded}，上传 ${it.uploaded}，冲突 ${it.conflicts}，跳过 ${it.skipped}"
                }.onFailure {
                    status.text = "失败：${it.message}"
                }
            }
        }, "dsh-link-sync").start()
    }

    private fun normalizedUrl(): String? {
        var raw = urlInput.text.toString().trim()
        if (raw.isBlank()) {
            toast("先填电脑地址")
            return null
        }
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) raw = "http://$raw"
        return raw.trimEnd('/')
    }

    private fun save() {
        prefs.baseUrl = urlInput.text.toString().trim()
        prefs.password = codeInput.text.toString().trim()
    }

    private fun section(title: String, content: LinearLayout.() -> Unit): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            setBackgroundColor(Color.parseColor("#171c24"))
            layoutParams = matchWrap().apply { bottomMargin = dp(14) }
        }
        card.addView(TextView(this).apply {
            text = title
            textSize = 12f
            setTextColor(Color.parseColor("#95a1b5"))
        })
        card.content()
        return card
    }

    private fun note(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 12f
        setTextColor(Color.parseColor("#95a1b5"))
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun button(text: String, click: () -> Unit): Button = Button(this).apply {
        this.text = text
        setOnClickListener { click() }
    }

    private fun space(widthDp: Int): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(widthDp), 1)
    }

    private fun matchWrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(text: String): Unit = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}
