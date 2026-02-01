package xyz.pearos.pearboot.ui.downloads

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.pearos.pearboot.data.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    VERIFIED,
    CORRUPT
}

class DownloadsViewModel : ViewModel() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient()

    private var job: Job? = null
    private var isoFile: File? = null
    private var ctx: Context? = null
    private var completedNormally = false

    private val _metadata = MutableStateFlow<Metadata?>(null)
    val metadata: StateFlow<Metadata?> = _metadata

    private val _checking = MutableStateFlow(true)
    val checking: StateFlow<Boolean> = _checking

    private val _state = MutableStateFlow(DownloadState.NOT_DOWNLOADED)
    val state: StateFlow<DownloadState> = _state

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress

    private val _speedMbps = MutableStateFlow(0f)
    val speedMbps: StateFlow<Float> = _speedMbps

    init {
        scope.launch {
            _checking.value = true
            _metadata.value = MetadataRepository.fetch()
            _checking.value = false
            tryInit()
        }
    }

    fun attachContext(context: Context) {
        ctx = context
        tryInit()
    }

    private fun tryInit() {
        val meta = _metadata.value ?: return
        val context = ctx ?: return
        if (isoFile != null) return

        scope.launch {
            isoFile = File(context.filesDir, meta.file.name)

            val prefs = context.downloadStore.data.first()
            val completed = prefs[DownloadKeys.COMPLETED] == true

            if (completed) {
                _state.value = DownloadState.VERIFIED
                _downloadedBytes.value = meta.file.size_bytes
                _progress.value = 1f
                return@launch
            }

            if (!isoFile!!.exists()) {
                _state.value = DownloadState.NOT_DOWNLOADED
                return@launch
            }

            val size = isoFile!!.length()
            _downloadedBytes.value = size
            _progress.value =
                (size.toFloat() / meta.file.size_bytes).coerceIn(0f, 1f)

            _state.value =
                if (size < meta.file.size_bytes)
                    DownloadState.PAUSED
                else
                    DownloadState.VERIFYING.also { verifyChecksum(meta) }
        }
    }

    fun startOrResume() {
        val meta = _metadata.value ?: return
        val file = isoFile ?: return

        if (file.length() >= meta.file.size_bytes) {
            _state.value = DownloadState.VERIFYING
            verifyChecksum(meta)
            return
        }

        completedNormally = false
        _state.value = DownloadState.DOWNLOADING

        job = scope.launch {
            val existing = file.length()

            val request = Request.Builder()
                .url(meta.download.url)
                .addHeader("Range", "bytes=$existing-")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _state.value = DownloadState.PAUSED
                    return@use
                }

                val input = response.body!!.byteStream()
                val output = FileOutputStream(file, true)

                val buffer = ByteArray(32 * 1024)
                var downloaded = existing
                var lastTime = System.currentTimeMillis()
                var lastBytes = downloaded

                while (isActive) {
                    val read = input.read(buffer)
                    if (read == -1) {
                        completedNormally = true
                        break
                    }

                    output.write(buffer, 0, read)
                    downloaded += read

                    _downloadedBytes.value = downloaded
                    _progress.value =
                        (downloaded.toFloat() / meta.file.size_bytes)
                            .coerceIn(0f, 1f)

                    val now = System.currentTimeMillis()
                    val dt = max(1, now - lastTime)
                    val db = downloaded - lastBytes

                    _speedMbps.value =
                        (db.toFloat() / dt) * 1000f / (1024f * 1024f)

                    lastTime = now
                    lastBytes = downloaded
                }

                input.close()
                output.close()
            }

            if (completedNormally) {
                _state.value = DownloadState.VERIFYING
                verifyChecksum(meta)
            }
        }
    }

    fun pause() {
        job?.cancel()
        completedNormally = false
        _state.value = DownloadState.PAUSED
    }

    fun delete() {
        scope.launch {
            job?.cancel()
            isoFile?.delete()
            ctx?.downloadStore?.edit {
                it[DownloadKeys.COMPLETED] = false
                it.remove(DownloadKeys.VERIFIED_SHA256)
            }
            _downloadedBytes.value = 0
            _progress.value = 0f
            _state.value = DownloadState.NOT_DOWNLOADED
        }
    }

    private fun verifyChecksum(meta: Metadata) {
        val file = isoFile ?: return
        val context = ctx ?: return

        scope.launch {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = FileInputStream(file)
            val buffer = ByteArray(64 * 1024)

            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            input.close()

            val hash = digest.digest().joinToString("") { "%02x".format(it) }

            if (hash.equals(meta.file.sha256, true)) {
                context.downloadStore.edit {
                    it[DownloadKeys.COMPLETED] = true
                    it[DownloadKeys.VERIFIED_SHA256] = meta.file.sha256
                }
                _state.value = DownloadState.VERIFIED
            } else {
                _state.value = DownloadState.CORRUPT
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}
