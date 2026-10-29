package com.kob.frameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

class OverlayService : Service() {

    companion object {
        @Volatile
        var running = false

        const val ACTION_STOP = "com.kob.frameboost.STOP_OVERLAY"
        private const val CHANNEL = "monitor"
        private const val NOTIF_ID = 1001

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, OverlayService::class.java))
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var cfg: AppSettings
    private lateinit var lp: WindowManager.LayoutParams
    private var tv: TextView? = null

    private val ui = Handler(Looper.getMainLooper())
    private var worker: HandlerThread? = null
    private var bg: Handler? = null

    private var frames = 0
    private var lastNs = 0L
    private var fps = 0
    private var cpu: Reading? = null
    private var gpu: Reading? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val frameCb = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            if (lastNs == 0L) lastNs = frameTimeNanos
            frames++
            val dt = frameTimeNanos - lastNs
            if (dt >= 1_000_000_000L) {
                fps = (frames * 1_000_000_000.0 / dt).roundToInt()
                frames = 0
                lastNs = frameTimeNanos
                render()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private val sampler = object : Runnable {
        override fun run() {
            if (!running) return
            val c = SystemStats.cpu()
            val g = SystemStats.gpu()
            ui.post {
                cpu = c
                gpu = g
                render()
            }
            bg?.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startAsForeground()

        if (tv == null) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            cfg = AppSettings(this)
            if (!createOverlay()) {
                Toast.makeText(this, "สร้างหน้าต่างลอยไม่ได้ ตรวจสิทธิ์ \"แสดงทับแอปอื่น\"", Toast.LENGTH_LONG).show()
                stopSelf()
                return START_NOT_STICKY
            }
            running = true
            worker = HandlerThread("monitor-sampler").also { it.start() }
            bg = Handler(worker!!.looper).also { it.post(sampler) }
            Choreographer.getInstance().postFrameCallback(frameCb)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frameCb)
        bg?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        bg = null
        tv?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        tv = null
        super.onDestroy()
    }

    // ---------- overlay ----------

    private fun createOverlay(): Boolean {
        val view = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.argb(170, 0, 0, 0))
                cornerRadius = dp(8).toFloat()
            }
            text = "FPS --\nCPU --\nGPU --"
        }

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (cfg.overlayX >= 0) cfg.overlayX else dp(8)
            y = if (cfg.overlayY >= 0) cfg.overlayY else dp(80)
        }

        // ลากเพื่อย้ายตำแหน่ง
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = lp.x
                        startY = lp.y
                        touchX = e.rawX
                        touchY = e.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = startX + (e.rawX - touchX).toInt()
                        lp.y = startY + (e.rawY - touchY).toInt()
                        try {
                            wm.updateViewLayout(v, lp)
                        } catch (ex: Exception) {
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        cfg.overlayX = lp.x
                        cfg.overlayY = lp.y
                    }
                }
                return true
            }
        })

        return try {
            wm.addView(view, lp)
            tv = view
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun fmt(label: String, r: Reading?): String =
        if (r == null) "$label N/A" else "$label ${if (r.estimated) "~" else ""}${r.pct}%"

    private fun render() {
        tv?.text = "FPS $fps\n${fmt("CPU", cpu)}\n${fmt("GPU", gpu)}"
    }

    // ---------- notification ----------

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "หน้าต่างมอนิเตอร์", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val action = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "ปิด",
            stopIntent
        ).build()
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("FrameBoost มอนิเตอร์")
            .setContentText("แสดง FPS / CPU / GPU อยู่")
            .setContentIntent(openIntent)
            .addAction(action)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
