package com.autotyper.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-registered receiver so START works even when the IME service
 * hasn't been created yet. It only stores a "pending" command; the IME
 * consumes it as soon as a text field gains focus.
 */
class CommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Commands.ACTION_START -> {
                val text = intent.getStringExtra(Commands.EXTRA_TEXT) ?: return
                val wpm = intent.getIntExtra(Commands.EXTRA_WPM, 60)
                val humanity = intent.getFloatExtra(Commands.EXTRA_HUMANITY, 0.5f)
                TypingSession.setPending(text, TypeConfig(wpm, humanity))
            }
            Commands.ACTION_STOP -> TypingSession.clearPending()
        }
    }
}
