package com.autotyper.app

import android.content.Context
import android.content.Intent

/**
 * Shared command actions + helpers for broadcasting commands between
 * the app, the floating panel and the IME typing engine.
 */
object Commands {
    const val ACTION_START = "com.autotyper.app.START"
    const val ACTION_PAUSE = "com.autotyper.app.PAUSE"
    const val ACTION_RESUME = "com.autotyper.app.RESUME"
    const val ACTION_STOP = "com.autotyper.app.STOP"

    const val EXTRA_TEXT = "text"
    const val EXTRA_WPM = "wpm"
    const val EXTRA_HUMANITY = "humanity"

    fun sendStart(ctx: Context, text: String, wpm: Int, humanity: Float) {
        val i = Intent(ACTION_START).setPackage(ctx.packageName)
        i.putExtra(EXTRA_TEXT, text)
        i.putExtra(EXTRA_WPM, wpm)
        i.putExtra(EXTRA_HUMANITY, humanity)
        ctx.sendBroadcast(i)
    }

    fun sendPause(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_PAUSE).setPackage(ctx.packageName))
    }

    fun sendResume(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_RESUME).setPackage(ctx.packageName))
    }

    fun sendStop(ctx: Context) {
        ctx.sendBroadcast(Intent(ACTION_STOP).setPackage(ctx.packageName))
    }
}
