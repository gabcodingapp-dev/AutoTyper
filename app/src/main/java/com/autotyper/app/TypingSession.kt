package com.autotyper.app

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Singleton that owns the typing state and the background typing loop.
 * Lives in the app process. The IME service plugs in its connection
 * provider; the app UI / floating panel call start/pause/resume/stop
 * directly (everything runs in the same process).
 */
object TypingSession {

    @Volatile var connectionProvider: (() -> InputConnection?)? = null

    @Volatile var running: Boolean = false
        private set
    @Volatile var paused: Boolean = false
        private set
    @Volatile var done: Boolean = false
        private set
    @Volatile var index: Int = 0
        private set
    @Volatile var totalChars: Int = 0
        private set
    @Volatile var config: TypeConfig = TypeConfig()
        private set

    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private var thread: Thread? = null
    private val lock = Any()
    @Volatile private var actions: List<TypeAction> = emptyList()

    fun addListener(l: () -> Unit) = listeners.add(l)
    fun removeListener(l: () -> Unit) = listeners.remove(l)

    fun start(text: String, cfg: TypeConfig) {
        stopInternal()
        val built = TypingEngine(cfg).build(text)
        synchronized(lock) { actions = built }
        config = cfg
        totalChars = built.size
        index = 0
        done = false
        running = true
        paused = false
        thread = Thread({ loop() }, "auto-typer")
        thread!!.start()
        notifyState()
    }

    private fun loop() {
        var cur = 0
        while (running) {
            while (paused && running) sleepChunk(50)
            if (!running) break
            if (cur >= totalChars) {
                done = true
                break
            }
            // No focused text field yet? Wait quietly and retry.
            val conn = connectionProvider?.invoke()
            if (conn == null) {
                sleepChunk(120)
                continue
            }
            val action: TypeAction = synchronized(lock) { actions[cur] }

            // give the field a moment to settle on the very first keystroke
            if (cur == 0) interruptibleSleep(450L + kotlin.random.Random.nextInt(300))

            interruptibleSleep(action.delayMs)
            if (!running) break

            when (action.type) {
                ActionType.COMMIT -> runCatching { conn.commitText(action.char.toString(), 1) }
                ActionType.ENTER -> sendKey(conn, KeyEvent.KEYCODE_ENTER)
                ActionType.TAB -> sendKey(conn, KeyEvent.KEYCODE_TAB)
                ActionType.BACKSPACE -> runCatching { conn.deleteSurroundingText(1, 0) }
            }

            cur++
            index = cur
            notifyState()
        }
        running = false
        paused = false
        index = cur
        notifyState()
    }

    private fun sendKey(conn: InputConnection, code: Int) {
        runCatching {
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    private fun interruptibleSleep(ms: Long) {
        if (ms <= 0) return
        val deadline = System.currentTimeMillis() + ms
        while (running && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10) } catch (_: InterruptedException) { return }
        }
    }

    private fun sleepChunk(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) { }
    }

    fun pause() {
        if (running && !paused) { paused = true; notifyState() }
    }

    fun resume() {
        if (running && paused) { paused = false; notifyState() }
    }

    fun stop() {
        stopInternal()
        notifyState()
    }

    private fun stopInternal() {
        running = false
        paused = false
        done = false
        thread?.interrupt()
        thread = null
        index = 0
    }

    private fun notifyState() {
        listeners.forEach { runCatching { it() } }
    }
}
