package com.example.fakelocation

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat

class MockLocationService : Service() {

    private lateinit var handler: Handler
    private lateinit var runnable: Runnable
    private var lat = 0.0
    private var lng = 0.0

    companion object {
        const val CHANNEL_ID = "mock_location_channel"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lat = intent?.getDoubleExtra("lat", 0.0) ?: 0.0
        lng = intent?.getDoubleExtra("lng", 0.0) ?: 0.0

        createChannel()
        startForeground(1, buildNotification())

        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                try {
                    pushMockLocation()
                } catch (e: SecurityException) {
                    stopSelf()
                }
                handler.postDelayed(this, 1000) // 1秒ごとに送信
            }
        }
        handler.post(runnable)
        return START_STICKY
    }

    private fun pushMockLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                lm.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true, 3, 5
                )
                val loc = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    accuracy = 1.0f
                    altitude = 0.0
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                lm.setTestProviderLocation(provider, loc)
            } catch (_: Exception) {
                // NETWORK_PROVIDERが無効な端末では無視
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "位置情報固定", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("位置情報固定中")
            .setContentText("($lat, $lng) に固定されています")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
            lm.removeTestProvider(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
