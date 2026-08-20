package com.autotyper.app

import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * Singleton that owns the typing state and the background typing loop.
 * Lives in the app process so it survives IME service recreation.
 * The IME service plugs in its [connectionProvider]; everything else
 * (app UI, floating panel) just reads state and sends commands.
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

    // Pending command set while the IME isn't running yet
    @Volatile var pendingText: String? = null
    @Volatile var pendingConfig: TypeConfig? = null

    @Volatile var onStateChanged: (() -> Unit)? = null

    private var thread: Thread? = null
    private val lock = Any()
    @Volatile private var actions: List<TypeAction> = emptyList()

    fun setPending(text: String, cfg: TypeConfig) {
        pendingText = text
        pendingConfig = cfg
    }

    fun clearPending() {
        pendingText = null
        pendingConfig = null
    }

    fun start(text: String, cfg: TypeConfig) {
        stopInternal()
        val engine = TypingEngine(cfg)
        val built = engine.build(text)
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
        var curIndex = 0
        while (running) {
            while (paused && running) {
                sleepChunk(50)
            }
            if (!running) break
            if (curIndex >= totalChars) {
                done = true
                break
            }
            val conn = connectionProvider?.invoke()
            if (conn == null) {
                // no focused text field yet — wait quietly
                sleepChunk(120)
                continue
            }
            val action: TypeAction = synchronized(lock) { actions[curIndex] }

            // give the field a moment to settle on the very first keystroke
            if (curIndex == 0) interruptibleSleep(450L + kotlin.random.Random.nextInt(300))

            interruptibleSleep(action.delayMs)
            if (!running) break

            when (action.type) {
                ActionType.COMMIT -> conn.commitText(action.char.toString(), 1)
                ActionType.ENTER -> sendKey(conn, KeyEvent.KEYCODE_ENTER)
                ActionType.TAB -> sendKey(conn, KeyEvent.KEYCODE_TAB)
                ActionType.BACKSPACE -> conn.deleteSurroundingText(1, 0)
            }

            curIndex++
            index = curIndex
            notifyState()
        }
        running = false
        paused = false
        index = curIndex
        notifyState()
    }

    private fun sendKey(conn: InputConnection, code: Int) {
        conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        conn.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
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

    fun notifyState() {
        onStateChanged?.invoke()
    }
}
