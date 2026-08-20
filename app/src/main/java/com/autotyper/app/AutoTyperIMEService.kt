package com.autotyper.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView

/**
 * The invisible typing engine. This is a real Input Method (keyboard),
 * which is the only root-free way to inject genuine keystrokes into other
 * apps (Google Docs, Gmail, etc.) with real per-key timing.
 */
class AutoTyperIMEService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var receiver: BroadcastReceiver
    private var statusView: TextView? = null

    override fun onCreate() {
        super.onCreate()
        TypingSession.connectionProvider = { currentInputConnection }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Commands.ACTION_START -> {
                        val text = intent.getStringExtra(Commands.EXTRA_TEXT) ?: return
                        val wpm = intent.getIntExtra(Commands.EXTRA_WPM, 60)
                        val humanity = intent.getFloatExtra(Commands.EXTRA_HUMANITY, 0.5f)
                        TypingSession.clearPending()
                        TypingSession.start(text, TypeConfig(wpm, humanity))
                    }
                    Commands.ACTION_PAUSE -> TypingSession.pause()
                    Commands.ACTION_RESUME -> TypingSession.resume()
                    Commands.ACTION_STOP -> TypingSession.stop()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Commands.ACTION_START)
            addAction(Commands.ACTION_PAUSE)
            addAction(Commands.ACTION_RESUME)
            addAction(Commands.ACTION_STOP)
        }
        registerReceiver(receiver, filter)

        TypingSession.onStateChanged = { handler.post { updateStatus() } }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // A field just gained focus — consume any pending start command.
        val pendingText = TypingSession.pendingText
        if (pendingText != null) {
            val cfg = TypingSession.pendingConfig ?: TypeConfig()
            TypingSession.clearPending()
            handler.postDelayed({ TypingSession.start(pendingText, cfg) }, 350)
        }
    }

    override fun onCreateInputView(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.ime_view, null)
        statusView = v.findViewById(R.id.ime_status)
        v.findViewById<View>(R.id.ime_stop).setOnClickListener { TypingSession.stop() }
        updateStatus()
        return v
    }

    private fun updateStatus() {
        val s = statusView ?: return
        s.text = when {
            TypingSession.done -> "Done — switch back to your keyboard"
            TypingSession.running && TypingSession.paused -> "Paused"
            TypingSession.running -> {
                val total = TypingSession.totalChars
                val d = TypingSession.index
                val pct = if (total > 0) d * 100 / total else 0
                "Typing… $pct%"
            }
            else -> "AutoTyper ready"
        }
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        super.onDestroy()
    }
}
