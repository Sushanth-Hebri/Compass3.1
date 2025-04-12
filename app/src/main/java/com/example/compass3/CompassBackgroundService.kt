package com.example.compass3

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.*

class CompassBackgroundService : Service(), TextToSpeech.OnInitListener {

    private var job: Job? = null
    private lateinit var urlToListen: String
    private lateinit var tts: TextToSpeech
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        urlToListen = intent?.getStringExtra("url") ?: return START_NOT_STICKY
        startForegroundNotification()

        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                try {
                    val request = Request.Builder().url(urlToListen).build()
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()?.trim()

                    if (!body.isNullOrEmpty()) {
                        Log.d("CompassService", "Received: $body")
                        speakText(body)
                    }

                    delay(5000) // poll every 5 seconds
                } catch (e: Exception) {
                    Log.e("CompassService", "Error polling URL", e)
                    delay(10000) // wait longer if error
                }
            }
        }

        return START_STICKY
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        tts.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    private fun startForegroundNotification() {
        val channelId = "compass_service_channel"

        // Only create NotificationChannel for API level 26 and higher
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Compass Service", NotificationManager.IMPORTANCE_LOW)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            // Use the Notification.Builder for API 26 and higher
            val notification = Notification.Builder(this, channelId)
                .setContentTitle("Compass Background Listener")
                .setContentText("Listening for commands...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)
        } else {
            // For devices below API 26, use the legacy Notification.Builder
            val notification = Notification.Builder(this)
                .setContentTitle("Compass Background Listener")
                .setContentText("Listening for commands...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .build()

            startForeground(1, notification)
        }
    }
}
