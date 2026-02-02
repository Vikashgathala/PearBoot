package xyz.pearos.pearboot.download

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.*
import okhttp3.*
import xyz.pearos.pearboot.R
import xyz.pearos.pearboot.data.DownloadKeys
import xyz.pearos.pearboot.data.downloadStore
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val url = intent?.getStringExtra("url") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val fileName = intent.getStringExtra("name") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val total = intent.getLongExtra("total", 0)

        startForeground(1, notification("Downloading…"))

        scope.launch {
            try {
                download(url, fileName, total)
            } catch (e: Exception) {
                e.printStackTrace()
                updateNotification("Download failed")
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private suspend fun download(url: String, name: String, total: Long) {
        val file = File(filesDir, "isos/$name")
        file.parentFile?.mkdirs()

        val downloaded = if (file.exists()) file.length() else 0L

        val request = Request.Builder()
            .url(url)
            .apply {
                if (downloaded > 0) {
                    addHeader("Range", "bytes=$downloaded-")
                }
            }
            .build()

        client.newCall(request).execute().use { res ->
            if (!res.isSuccessful && res.code != 206) {
                throw Exception("Download failed with code: ${res.code}")
            }

            val append = downloaded > 0 && res.code == 206
            val sink = file.outputStream().buffered()
            val source = res.body!!.byteStream()
            val buf = ByteArray(64 * 1024)  // 64KB buffer

            var read: Int
            var current = if (append) downloaded else 0L
            var lastPersistTime = System.currentTimeMillis()
            var lastPersistBytes = current
            var lastNotificationTime = System.currentTimeMillis()

            // Persist settings
            val persistIntervalMs = 5000L       // Every 5 seconds
            val persistIntervalBytes = 20L * 1024 * 1024  // Or every 20MB
            val notificationIntervalMs = 1000L  // Update notification every 1 second

            try {
                while (source.read(buf).also { read = it } != -1) {
                    sink.write(buf, 0, read)
                    current += read

                    val now = System.currentTimeMillis()

                    // Update notification periodically
                    if (now - lastNotificationTime >= notificationIntervalMs) {
                        val progress = if (total > 0) ((current * 100) / total).toInt() else 0
                        updateNotification("Downloading… $progress%")
                        lastNotificationTime = now
                    }

                    // Persist progress less frequently
                    val timeDelta = now - lastPersistTime
                    val bytesDelta = current - lastPersistBytes

                    if (timeDelta >= persistIntervalMs || bytesDelta >= persistIntervalBytes) {
                        applicationContext.downloadStore.edit {
                            it[DownloadKeys.BYTES] = current
                            it[DownloadKeys.TOTAL] = total
                            it[DownloadKeys.PATH] = file.absolutePath
                        }
                        lastPersistTime = now
                        lastPersistBytes = current
                    }
                }
            } finally {
                sink.flush()
                sink.close()
                source.close()
            }
        }

        // Final persist
        applicationContext.downloadStore.edit {
            it[DownloadKeys.BYTES] = file.length()
            it[DownloadKeys.TOTAL] = total
            it[DownloadKeys.PATH] = file.absolutePath
            it[DownloadKeys.COMPLETED] = true
        }
    }

    private fun notification(text: String): Notification {
        val channelId = "download"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW)
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("PearBoot")
            .setContentText(text)
            .setOngoing(true)
            .setProgress(100, 0, true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, notification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}