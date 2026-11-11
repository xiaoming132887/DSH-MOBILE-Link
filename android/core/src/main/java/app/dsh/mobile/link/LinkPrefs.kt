package app.dsh.mobile.link

import android.content.Context

/** Stores the PC URL and password locally (SharedPreferences). */
class LinkPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("dsh_mobile_link", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) = prefs.edit().putString("base_url", value).apply()

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit().putString("password", value).apply()
}
