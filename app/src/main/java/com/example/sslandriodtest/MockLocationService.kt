package com.example.sslandriodtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

/**
 * 前台服务：持续推送模拟定位，即使 App 切到后台也能保持生效。
 */
class MockLocationService : Service() {

    private var mockProvider: MockLocationProvider? = null
    private val handler = Handler(Looper.getMainLooper())
    private var latitude = 0.0
    private var longitude = 0.0

    private val tickRunnable = object : Runnable {
        override fun run() {
            mockProvider?.tick()
            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                startMocking(latitude, longitude)
            }
            ACTION_UPDATE -> {
                latitude = intent.getDoubleExtra(EXTRA_LATITUDE, 0.0)
                longitude = intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0)
                if (mockProvider == null) {
                    startMocking(latitude, longitude)
                } else {
                    mockProvider?.setLocation(latitude, longitude)
                    updateNotification(latitude, longitude)
                }
            }
            ACTION_STOP -> {
                stopMocking()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopMocking()
        super.onDestroy()
    }

    private fun startMocking(latitude: Double, longitude: Double) {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(latitude, longitude))
        mockProvider = MockLocationProvider(this).also {
            it.startMocking(latitude, longitude)
        }
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    private fun stopMocking() {
        handler.removeCallbacks(tickRunnable)
        mockProvider?.stopMocking()
        mockProvider = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateNotification(latitude: Double, longitude: Double) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(latitude, longitude))
    }

    private fun buildNotification(latitude: Double, longitude: Double): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在模拟 GPS 位置")
            .setContentText(
                "纬度: ${"%.6f".format(latitude)}, 经度: ${"%.6f".format(longitude)}"
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "模拟定位",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.example.sslandriodtest.action.START_MOCK"
        const val ACTION_UPDATE = "com.example.sslandriodtest.action.UPDATE_MOCK"
        const val ACTION_STOP = "com.example.sslandriodtest.action.STOP_MOCK"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"

        private const val CHANNEL_ID = "mock_location"
        private const val NOTIFICATION_ID = 1001
        private const val TICK_INTERVAL_MS = 300L

        fun start(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.startForegroundService(intent)
        }

        fun update(context: Context, latitude: Double, longitude: Double) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_LATITUDE, latitude)
                putExtra(EXTRA_LONGITUDE, longitude)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MockLocationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
