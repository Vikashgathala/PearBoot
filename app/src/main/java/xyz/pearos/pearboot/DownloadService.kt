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

class DownloadService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val url = intent!!.getStringExtra("url")!!
        val fileName = intent.getStringExtra("name")!!
        val total = intent.getLongExtra("total", 0)

        startForeground(1, notification("Downloading…"))

        scope.launch {
            download(url, fileName, total)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_STICKY
    }

    private suspend fun download(url: String, name: String, total: Long) {
        val file = File(filesDir, "isos/$name")
        file.parentFile!!.mkdirs()

        val downloaded = if (file.exists()) file.length() else 0L

        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=$downloaded-")
            .build()

        client.newCall(request).execute().use { res ->
            val sink = file.outputStream().buffered()
            val source = res.body!!.byteStream()
            val buf = ByteArray(8 * 1024)

            var read: Int
            var current = downloaded

            while (source.read(buf).also { read = it } != -1) {
                sink.write(buf, 0, read)
                current += read

                applicationContext.downloadStore.edit {
                    it[DownloadKeys.BYTES] = current
                    it[DownloadKeys.TOTAL] = total
                    it[DownloadKeys.PATH] = file.absolutePath
                }
            }

            sink.close()
        }

        applicationContext.downloadStore.edit {
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
            .build()
    }

    override fun onBind(p0: Intent?): IBinder? = null
}
