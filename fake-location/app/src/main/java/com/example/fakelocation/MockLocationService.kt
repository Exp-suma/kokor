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
        @Volatile var isRunning = false
            private set
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
                    return
                }
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(runnable)
        isRunning = true
        return START_STICKY
    }

    private fun pushMockLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            try {
                try {
                    lm.removeTestProvider(provider)
                } catch (_: Exception) {}
                lm.addTestProvider(
                    provider,
                    false, false, false, false, true, true, true, 3, 5
                )
                val loc = Location(provider).apply {
                    latitude = lat
                    longitude = lng
                    accuracy = 1.0f
                    altitude = 0.0
                    speed = 0.0f
                    bearing = 0.0f
                    time = System.currentTimeMillis()
                    elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                }
                lm.setTestProviderLocation(provider, loc)
            } catch (_: Exception) {}
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
            .setContentText("(%.5f, %.5f) に固定されています".format(lat, lng))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
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
