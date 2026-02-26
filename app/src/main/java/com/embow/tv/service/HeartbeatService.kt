package com.embow.tv.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.embow.tv.BuildConfig
import com.embow.tv.R
import com.embow.tv.data.network.ApiClient
import com.embow.tv.data.repository.EmbowRepository
import com.embow.tv.data.storage.SecureStorage
import com.embow.tv.ui.player.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class HeartbeatService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: EmbowRepository
    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val secureStorage = SecureStorage(this)
        val apiClient = ApiClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
        )
        repository = EmbowRepository(apiClient, secureStorage)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        acquireWakeLock()
        handler.post(heartbeatRunnable)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendHeartbeat() {
        serviceScope.launch {
            try {
                val mainActivity = getMainActivity()
                val status = if (mainActivity?.isPlaying() == true) "playing" else "idle"
                val videoId = mainActivity?.getCurrentVideoId()
                val position = mainActivity?.getCurrentPosition()

                val result = repository.sendHeartbeat(status, videoId, position)
                result.fold(
                    onSuccess = { response ->
                        handleCommands(response.commands)
                    },
                    onFailure = {
                    }
                )
            } catch (e: Exception) {
            }
        }
    }

    private fun handleCommands(commands: List<com.embow.tv.data.models.Command>) {
        val mainActivity = getMainActivity() ?: return

        for (command in commands) {
            when (command.type) {
                "REFRESH" -> {
                    handler.post {
                        mainActivity.refreshPlaylist()
                    }
                }
                "PAUSE" -> {
                    handler.post {
                        mainActivity.pausePlayback()
                    }
                }
                "RESUME" -> {
                    handler.post {
                        mainActivity.resumePlayback()
                    }
                }
                "REBOOT" -> {
                    handler.post {
                        restartApp()
                    }
                }
                "NOW_PLAYING" -> {
                }
            }
        }
    }

    private fun getMainActivity(): MainActivity? {
        return try {
            val activityManager = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
            val runningTasks = activityManager.appTasks
            if (runningTasks.isNotEmpty()) {
                val topActivity = runningTasks[0].taskInfo.topActivity
                if (topActivity?.className == MainActivity::class.java.name) {
                    null
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "EmbowTV::HeartbeatWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Embow TV Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Embow TV connected"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Embow TV")
            .setContentText("Connected")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(heartbeatRunnable)
        wakeLock?.release()
        serviceScope.cancel()
    }

    companion object {
        private const val CHANNEL_ID = "embow_tv_service_channel"
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 20000L
    }
}
