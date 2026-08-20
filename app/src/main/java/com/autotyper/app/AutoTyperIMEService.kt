package com.autotyper.app

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView

/**
 * The invisible typing engine. This is a real Input Method (keyboard),
 * which is the only root-free way to inject genuine keystrokes into other
 * apps (Google Docs, Gmail, etc.) with real per-key timing.
 */
class AutoTyperIMEService : InputMethodService() {

    private val handler = Handler(Looper.getMainLooper())
    private var statusView: TextView? = null
    private val stateListener: () -> Unit = { handler.post { updateStatus() } }

    override fun onCreate() {
        super.onCreate()
        TypingSession.connectionProvider = { currentInputConnection }
        TypingSession.addListener(stateListener)
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
        TypingSession.removeListener(stateListener)
        super.onDestroy()
    }
}
