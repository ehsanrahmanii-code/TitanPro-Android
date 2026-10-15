package com.titanpro.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Foreground service that starts the embedded TITAN Flask server
 * via Chaquopy and keeps it alive while the app is running.
 */
class TitanServerService : Service() {

    companion object {
        private const val TAG = "TitanServer"
        private const val CHANNEL_ID = "titan_server_channel"
        private const val NOTIF_ID = 8080
        private val isRunning = AtomicBoolean(false)
    }

    private var serverThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("در حال راه‌اندازی…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning.compareAndSet(false, true)) {
            startPythonServer()
        }
        return START_STICKY
    }

    private fun startPythonServer() {
        serverThread = Thread {
            try {
                // Ensure Python is started
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(applicationContext))
                }
                val py = Python.getInstance()

                // Import our bridge module (src/main/python/titan_bridge.py)
                val bridge = py.getModule("titan_bridge")

                // Set Android-friendly environment before launching
                // TITAN already hard-locks to /storage/emulated/0/MMM
                bridge.callAttr(
                    "start_server",
                    "127.0.0.1",   // host
                    8080,         // port
                    applicationContext.filesDir.absolutePath  // fallback home if needed
                )

                updateNotification("TITAN در حال اجرا · پورت ۸۰۸۰")
                Log.i(TAG, "TITAN server started successfully on 127.0.0.1:8080")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start TITAN server", e)
                isRunning.set(false)
                updateNotification("خطا در راه‌اندازی سرور: ${e.message?.take(60)}")
            }
        }.apply {
            name = "Titan-Flask-Server"
            isDaemon = false
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "موتور TITAN",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "سرور تحلیل محلی TITAN Pro"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TITAN Pro V80")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning.set(false)
        // Attempt graceful shutdown if the bridge exposes it
        try {
            if (Python.isStarted()) {
                val py = Python.getInstance()
                val bridge = py.getModule("titan_bridge")
                bridge.callAttr("stop_server")
            }
        } catch (_: Exception) {}
        serverThread?.interrupt()
        super.onDestroy()
    }
}
