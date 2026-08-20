package com.autotyper.app

import android.app.Application
import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Global crash catcher: writes the stack trace to a file and shows it on
 * the next launch, so a crash is never a silent "it just closed".
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                CrashLog.write(this, "$stamp\n${sw}")
                Log.e("AutoTyper", "FATAL", throwable)
            } catch (ignored: Throwable) {
            }
            default?.uncaughtException(Thread.currentThread(), throwable)
            Process.killProcess(Process.myPid())
            System.exit(10)
        }
    }
}

object CrashLog {
    private const val FILE = "crash.log"

    fun write(ctx: Context, text: String) {
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            dir.mkdirs()
            File(dir, FILE).writeText(text)
        }
    }

    fun read(ctx: Context): String? = runCatching {
        val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
        File(dir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear(ctx: Context) {
        runCatching {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            File(dir, FILE).delete()
        }
    }
}
