package com.trollsound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SoundService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ws: WebSocket? = null
    private var serverUrl: String = ""
    private var currentPlayer: MediaPlayer? = null
    private var reconnectDelay = 1000L

    companion object {
        private const val TAG = "TrollSound"
        private const val CHANNEL_ID = "troll_channel"
        private const val NOTIF_ID = 1
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverUrl = intent?.getStringExtra("server") ?: return START_NOT_STICKY

        startForeground(NOTIF_ID, buildNotification("Подключение к $serverUrl..."))
        connect()
        return START_STICKY
    }

    private fun connect() {
        val client = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val wsUrl = serverUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")
            .trimEnd('/')

        val request = Request.Builder().url(wsUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket подключён")
                reconnectDelay = 1000L
                updateNotification("Подключено. Ожидание команд.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Сообщение: $text")
                try {
                    val json = JSONObject(text)
                    if (json.optString("type") == "play") {
                        val url = json.optString("url")
                        playSound(serverUrl.trimEnd('/') + url)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Парсинг ошибка", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket разорван: ${t.message}")
                updateNotification("Переподключение...")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket закрыт: $reason")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(reconnectDelay)
            reconnectDelay = (reconnectDelay * 2).coerceAtMost(30000L)
            if (scope.isActive) {
                updateNotification("Переподключение к $serverUrl...")
                connect()
            }
        }
    }

    private fun playSound(url: String) {
        scope.launch(Dispatchers.Main) {
            try {
                currentPlayer?.release()
                currentPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setDataSource(url)
                    setOnPreparedListener { it.start() }
                    setOnCompletionListener { it.release() }
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка воспроизведения", e)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        ws?.close(1000, "service stopped")
        currentPlayer?.release()
    }
}
