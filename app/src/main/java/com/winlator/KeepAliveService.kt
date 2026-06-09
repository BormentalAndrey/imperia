package com.winlator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class KeepAliveService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "winlator_keepalive_channel"
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NFS Underground 2")
            .setContentText("Запуск игры...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
        return START_NOT_STICKY
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Winlator Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeping Winlator alive for game emulation"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopForeground(true)
    }
}
