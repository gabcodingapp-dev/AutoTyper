package com.autotyper.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Movable always-on-top control panel (collapses to a small pill).
 * Runs as a foreground service so Samsung/One UI doesn't kill it.
 */
class FloatingPanelService : Service() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val stateListener = { handler.post { updateUI() } }

    private var panel: View? = null
    private var pill: View? = null
    private var panelLp: WindowManager.LayoutParams? = null
    private var pillLp: WindowManager.LayoutParams? = null

    private var collapsed = false

    private var dragStartX = 0
    private var dragStartY = 0
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var moved = false

    companion object {
        const val ACTION_TOGGLE = "com.autotyper.app.TOGGLE_PANEL"
        const val CHANNEL_ID = "autotyper_panel"
        const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createChannel()
        startForegroundCompat()
        TypingSession.addListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TOGGLE) {
            if (collapsed) expand() else collapse()
        }
        ensureVisible()
        return START_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID, buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
    }

    private fun ensureVisible() {
        if (collapsed) {
            if (pill == null) pill = inflatePill()
            if (pill?.parent == null) {
                pillLp = overlayParams()
                try { wm.addView(pill, pillLp) } catch (_: Exception) { }
            }
            panel?.let { if (it.parent != null) try { wm.removeView(it) } catch (_: Exception) { } }
        } else {
            if (panel == null) panel = inflatePanel()
            if (panel?.parent == null) {
                panelLp = overlayParams()
                try { wm.addView(panel, panelLp) } catch (_: Exception) { }
            }
            pill?.let { if (it.parent != null) try { wm.removeView(it) } catch (_: Exception) { } }
        }
        updateUI()
    }

    private fun overlayParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        val dm = resources.displayMetrics
        lp.x = Prefs.getPanelX(this, dm.widthPixels - dp(230))
        lp.y = Prefs.getPanelY(this, dp(140))
        return lp
    }

    private fun inflatePanel(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)
        makeDraggable(v.findViewById(R.id.drag_handle), { panelLp }, { snapAndSave(v, panelLp) })
        makeDraggable(v.findViewById(R.id.panel_title), { panelLp }, { snapAndSave(v, panelLp) })

        v.findViewById<View>(R.id.btn_collapse).setOnClickListener { collapse() }
        v.findViewById<View>(R.id.btn_play).setOnClickListener { onPlay() }
        v.findViewById<View>(R.id.btn_pause).setOnClickListener { TypingSession.pause() }
        v.findViewById<View>(R.id.btn_stop).setOnClickListener { TypingSession.stop() }
        return v
    }

    private fun inflatePill(): View {
        val v = LayoutInflater.from(this).inflate(R.layout.floating_pill, null)
        makeDraggable(v, { pillLp }, { snapAndSave(v, pillLp) })
        v.setOnClickListener {
            if (!moved) expand()
            moved = false
        }
        return v
    }

    private fun makeDraggable(
        view: View,
        getLp: () -> WindowManager.LayoutParams?,
        onDragEnd: () -> Unit
    ) {
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val lp = getLp() ?: return@setOnTouchListener false
                    dragStartX = lp.x
                    dragStartY = lp.y
                    touchStartX = e.rawX
                    touchStartY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val lp = getLp() ?: return@setOnTouchListener false
                    val dx = (e.rawX - touchStartX).toInt()
                    val dy = (e.rawY - touchStartY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) moved = true
                    lp.x = dragStartX + dx
                    lp.y = dragStartY + dy
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) { }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    onDragEnd()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapAndSave(view: View, lp: WindowManager.LayoutParams?) {
        val p = lp ?: return
        val dm = resources.displayMetrics
        val w = view.width
        val h = view.height
        val cx = p.x + w / 2
        p.x = if (cx < dm.widthPixels / 2) 0 else dm.widthPixels - w
        p.y = p.y.coerceIn(0, dm.heightPixels - h)
        try { wm.updateViewLayout(view, p) } catch (_: Exception) { }
        Prefs.setPanelPos(this, p.x, p.y)
    }

    private fun onPlay() {
        if (TypingSession.running) {
            if (TypingSession.paused) TypingSession.resume()
            return
        }
        val text = Prefs.getLastText(this)
        if (text.isBlank()) {
            Toast.makeText(this, "Set your text in the AutoTyper app first", Toast.LENGTH_SHORT).show()
            return
        }
        TypingSession.start(text, TypeConfig(Prefs.getWpm(this), Prefs.getHumanity(this)))
        updateUI()
    }

    private fun updateUI() {
        if (collapsed) {
            val pillBtn = pill?.findViewById<ImageView>(R.id.pill_btn)
            if (TypingSession.running && !TypingSession.paused) pillBtn?.setImageResource(R.drawable.ic_pause)
            else pillBtn?.setImageResource(R.drawable.ic_play)
            return
        }
        val status = panel?.findViewById<TextView>(R.id.panel_status)
        val progress = panel?.findViewById<ProgressBar>(R.id.panel_progress)
        status?.text = when {
            TypingSession.done -> "Done ✓"
            TypingSession.running && TypingSession.paused -> "Paused — resume to continue"
            TypingSession.running -> {
                val total = TypingSession.totalChars
                val d = TypingSession.index
                val pct = if (total > 0) d * 100 / total else 0
                "Typing… $pct%"
            }
            else -> "Ready"
        }
        progress?.let {
            it.max = TypingSession.totalChars.coerceAtLeast(1)
            it.progress = TypingSession.index
        }
    }

    private fun collapse() {
        collapsed = true
        ensureVisible()
    }

    private fun expand() {
        collapsed = false
        ensureVisible()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "Floating panel", NotificationManager.IMPORTANCE_LOW)
            ch.description = "Keeps the AutoTyper floating panel running"
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val toggle = PendingIntent.getService(
            this, 1,
            Intent(this, FloatingPanelService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoTyper")
            .setContentText("Tap to collapse / expand the panel")
            .setSmallIcon(R.drawable.ic_play)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        TypingSession.removeListener(stateListener)
        super.onDestroy()
    }
}
