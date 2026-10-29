package com.kob.frameboost

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * ขั้นที่ 1 ของตัวสร้างเฟรม: จับภาพหน้าจอแล้ววัดว่าเครื่องจับได้กี่ FPS
 * (ยังไม่สร้างเฟรมกลาง ใช้ทดสอบว่าเครื่องไหวไหมก่อน)
 */
class CaptureService : Service() {

    companion object {
        @Volatile var running = false
        @Volatile var captureFps = 0
        @Volatile var captureSize = ""

        const val ACTION_STOP = "com.kob.frameboost.STOP_CAPTURE"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 1002

        fun start(ctx: Context, resultCode: Int, data: Intent) {
            val i = Intent(ctx, CaptureService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, CaptureService::class.java))
        }
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var count = 0
    private var lastNs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // ต้อง startForeground (ชนิด mediaProjection) ก่อนเรียก getMediaProjection
        startAsForeground()

        @Suppress("DEPRECATION")
        val data = intent?.getParcelableExtra<Intent>("data")
        val code = intent?.getIntExtra("code", 0) ?: 0
        if (data == null || code == 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!begin(code, data)) {
            Toast.makeText(this, "เริ่มจับภาพไม่สำเร็จ", Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun begin(code: Int, data: Intent): Boolean {
        return try {
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val proj = mpm.getMediaProjection(code, data)
            projection = proj

            worker = HandlerThread("capture").also { it.start() }
            val h = Handler(worker!!.looper)

            // Android 14 บังคับให้ลงทะเบียน callback ก่อนสร้าง VirtualDisplay
            proj.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSelf()
                }
            }, h)

            val dm = DisplayMetrics()
            (getSystemService(WINDOW_SERVICE) as android.view.WindowManager)
                .defaultDisplay.getRealMetrics(dm)
            val f = when (AppSettings(this).scale) {
                0 -> 0.5f
                1 -> 0.75f
                else -> 1.0f
            }
            val w = ((dm.widthPixels * f).toInt() / 2) * 2
            val hh = ((dm.heightPixels * f).toInt() / 2) * 2
            captureSize = "${w}x$hh"

            val r = ImageReader.newInstance(w, hh, PixelFormat.RGBA_8888, 3)
            reader = r
            r.setOnImageAvailableListener({ ir ->
                val img = ir.acquireLatestImage()
                if (img != null) {
                    img.close()
                    onFrame()
                }
            }, h)

            display = proj.createVirtualDisplay(
                "frameboost-capture", w, hh, dm.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                r.surface, null, h
            )
            captureFps = 0
            running = true
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun onFrame() {
        val now = System.nanoTime()
        if (lastNs == 0L) lastNs = now
        count++
        val dt = now - lastNs
        if (dt >= 1_000_000_000L) {
            captureFps = Math.round(count * 1_000_000_000.0 / dt).toInt()
            count = 0
            lastNs = now
        }
    }

    override fun onDestroy() {
        running = false
        captureFps = 0
        try { display?.release() } catch (e: Exception) {}
        try { reader?.close() } catch (e: Exception) {}
        try { projection?.stop() } catch (e: Exception) {}
        worker?.quitSafely()
        display = null
        reader = null
        projection = null
        worker = null
        super.onDestroy()
    }

    private fun startAsForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "จับภาพหน้าจอ", NotificationManager.IMPORTANCE_LOW)
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("FrameBoost กำลังจับภาพหน้าจอ")
            .setContentText("แตะเพื่อหยุด")
            .setContentIntent(stopIntent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }
}
