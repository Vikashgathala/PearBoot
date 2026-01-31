package xyz.pearos.pearboot.ui.downloads

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.pearos.pearboot.data.Metadata
import xyz.pearos.pearboot.data.MetadataRepository
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class DownloadsViewModel : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    private var job: Job? = null
    private var downloadedSoFar = 0L

    private var lastBytes = 0L
    private var lastTime = 0L

    private val _metadata = MutableStateFlow<Metadata?>(null)
    val metadata: StateFlow<Metadata?> = _metadata

    private val _checking = MutableStateFlow(true)
    val checking: StateFlow<Boolean> = _checking

    private val _downloading = MutableStateFlow(false)
    val downloading: StateFlow<Boolean> = _downloading

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _speedMbps = MutableStateFlow(0f)
    val speedMbps: StateFlow<Float> = _speedMbps

    private lateinit var isoFile: File

    init {
        loadMetadata()
    }

    fun loadMetadata() {
        scope.launch {
            _checking.value = true
            _metadata.value = MetadataRepository.fetch()
            _checking.value = false
        }
    }

    fun startOrResume(context: Context) {
        val meta = _metadata.value ?: return
        if (_downloading.value) return

        val total = meta.file.size_bytes
        isoFile = File(context.filesDir, meta.file.name)

        if (isoFile.exists()) {
            downloadedSoFar = isoFile.length()
            _downloadedBytes.value = downloadedSoFar
            _progress.value = downloadedSoFar.toFloat() / total.toFloat()
        }

        lastBytes = downloadedSoFar
        lastTime = System.currentTimeMillis()

        job = scope.launch {
            _downloading.value = true
            _paused.value = false

            try {
                val request = Request.Builder()
                    .url(meta.download.url)
                    .addHeader("Range", "bytes=$downloadedSoFar-")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@launch

                val body = response.body ?: return@launch
                val input = body.byteStream()
                val output = FileOutputStream(isoFile, true)

                val buffer = ByteArray(32 * 1024)
                var downloaded = downloadedSoFar

                while (isActive) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    output.write(buffer, 0, read)
                    downloaded += read
                    downloadedSoFar = downloaded

                    _downloadedBytes.value = downloaded
                    _progress.value =
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)

                    val now = System.currentTimeMillis()
                    val dt = max(1, now - lastTime)
                    val db = downloaded - lastBytes

                    val speed =
                        (db.toFloat() / dt.toFloat()) * 1000f / (1024f * 1024f)

                    _speedMbps.value = speed.coerceAtLeast(0f)

                    lastBytes = downloaded
                    lastTime = now
                }

                input.close()
                output.close()

            } catch (_: CancellationException) {
                // paused
            } finally {
                _downloading.value = false
                _speedMbps.value = 0f
            }
        }
    }

    fun pause() {
        job?.cancel()
        _paused.value = true
        _downloading.value = false
        _speedMbps.value = 0f
    }

    fun cancel() {
        job?.cancel()
        if (::isoFile.isInitialized && isoFile.exists()) isoFile.delete()

        downloadedSoFar = 0L
        _paused.value = false
        _downloading.value = false
        _downloadedBytes.value = 0L
        _progress.value = 0f
        _speedMbps.value = 0f
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
