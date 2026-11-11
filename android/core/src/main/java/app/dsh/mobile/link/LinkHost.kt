package app.dsh.mobile.link

import java.io.File

/**
 * Host integration point. The Android host app (DSH Mobile or any other)
 * supplies its own sessions directory and optional local-service summary.
 */
interface LinkHost {
    fun sessionsDir(): File
    fun backupDir(): File
    fun localServiceSummary(): String = ""
}

object LinkHostRegistry {
    @Volatile
    var host: LinkHost? = null
}
