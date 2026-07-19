package nl.tstock.veren

import android.content.Context
import android.os.Process
import android.util.Log
import java.time.Instant

object CrashStore {
    private const val PREFS = "tstock_crash_report"
    private const val KEY_STACK = "stack"
    private const val KEY_TIME = "time"
    private const val KEY_THREAD = "thread"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_TIME, Instant.now().toString())
                    .putString(KEY_THREAD, thread.name)
                    .putString(KEY_STACK, Log.getStackTraceString(throwable))
                    .commit()
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun report(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stack = prefs.getString(KEY_STACK, null) ?: return null
        val time = prefs.getString(KEY_TIME, "onbekend")
        val thread = prefs.getString(KEY_THREAD, "onbekend")
        return "Tijd: $time\nThread: $thread\n\n$stack"
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }
}
