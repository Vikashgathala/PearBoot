package xyz.pearos.pearboot.ui.downloads

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.Request
import xyz.pearos.pearboot.data.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
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


    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private var downloadJob: Job? = null
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


    private val _verificationProgress = MutableStateFlow(0f)
    val verificationProgress: StateFlow<Float> = _verificationProgress

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _checking.value = true
            _metadata.value = MetadataRepository.fetch()
            _checking.value = false
            tryInit()
        }
    }

    fun attachContext(context: Context) {
        ctx = context.applicationContext
        tryInit()
    }

    private fun tryInit() {
        val meta = _metadata.value ?: return
        val context = ctx ?: return
        if (isoFile != null) return

        viewModelScope.launch(Dispatchers.IO) {

            val isoDir = File(context.filesDir, "isos")
            if (!isoDir.exists()) {
                isoDir.mkdirs()
            }
            isoFile = File(isoDir, meta.file.name)

            val prefs = context.downloadStore.data.first()
            val completed = prefs[DownloadKeys.COMPLETED] == true
            val savedPath = prefs[DownloadKeys.PATH]
            val savedHash = prefs[DownloadKeys.VERIFIED_SHA256]


            if (completed && savedPath != null && File(savedPath).exists()) {

                if (savedHash != null && savedHash.equals(meta.file.sha256, ignoreCase = true)) {
                    _state.value = DownloadState.VERIFIED
                    _downloadedBytes.value = meta.file.size_bytes
                    _progress.value = 1f
                    return@launch
                }
            }

            if (!isoFile!!.exists()) {
                _state.value = DownloadState.NOT_DOWNLOADED
                return@launch
            }

            val size = isoFile!!.length()
            _downloadedBytes.value = size
            _progress.value = (size.toFloat() / meta.file.size_bytes).coerceIn(0f, 1f)

            _state.value = if (size < meta.file.size_bytes) {
                DownloadState.PAUSED
            } else {
                DownloadState.VERIFYING.also { verifyChecksum(meta) }
            }
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

        downloadJob = viewModelScope.launch(Dispatchers.IO) {
            val existing = file.length()

            val request = Request.Builder()
                .url(meta.download.url)
                .apply {
                    if (existing > 0 && meta.download.resume) {
                        addHeader("Range", "bytes=$existing-")
                    }
                }
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        _state.value = DownloadState.PAUSED
                        return@use
                    }

                    val input = response.body!!.byteStream()
                    val append = existing > 0 && meta.download.resume && response.code == 206
                    val output = FileOutputStream(file, append)


                    val buffer = ByteArray(64 * 1024)
                    var downloaded = if (append) existing else 0L
                    var lastTime = System.currentTimeMillis()
                    var lastBytes = downloaded
                    var lastPersistTime = System.currentTimeMillis()
                    var lastPersistBytes = downloaded


                    val persistIntervalMs = 3000L
                    val persistIntervalBytes = 10L * 1024 * 1024

                    try {
                        while (isActive) {
                            val read = input.read(buffer)
                            if (read == -1) {
                                completedNormally = true
                                break
                            }

                            output.write(buffer, 0, read)
                            downloaded += read

                            _downloadedBytes.value = downloaded
                            _progress.value = (downloaded.toFloat() / meta.file.size_bytes)
                                .coerceIn(0f, 1f)


                            val now = System.currentTimeMillis()
                            val dt = max(1L, now - lastTime)
                            val db = downloaded - lastBytes

                            if (dt >= 500) {
                                _speedMbps.value = (db.toFloat() / dt) * 1000f / (1024f * 1024f)
                                lastTime = now
                                lastBytes = downloaded
                            }


                            val persistTimeDelta = now - lastPersistTime
                            val persistBytesDelta = downloaded - lastPersistBytes

                            if (persistTimeDelta >= persistIntervalMs || persistBytesDelta >= persistIntervalBytes) {
                                ctx?.downloadStore?.edit {
                                    it[DownloadKeys.BYTES] = downloaded
                                    it[DownloadKeys.TOTAL] = meta.file.size_bytes
                                    it[DownloadKeys.PATH] = file.absolutePath
                                }
                                lastPersistTime = now
                                lastPersistBytes = downloaded
                            }
                        }
                    } finally {
                        output.flush()
                        output.close()
                        input.close()
                    }
                }


                if (completedNormally) {
                    ctx?.downloadStore?.edit {
                        it[DownloadKeys.BYTES] = _downloadedBytes.value
                        it[DownloadKeys.TOTAL] = meta.file.size_bytes
                        it[DownloadKeys.PATH] = file.absolutePath
                    }

                    _state.value = DownloadState.VERIFYING
                    verifyChecksum(meta)
                }

            } catch (e: CancellationException) {

                ctx?.downloadStore?.edit {
                    it[DownloadKeys.BYTES] = _downloadedBytes.value
                    it[DownloadKeys.TOTAL] = meta.file.size_bytes
                    it[DownloadKeys.PATH] = file.absolutePath
                }
                throw e

            } catch (e: Exception) {
                e.printStackTrace()

                ctx?.downloadStore?.edit {
                    it[DownloadKeys.BYTES] = _downloadedBytes.value
                    it[DownloadKeys.TOTAL] = meta.file.size_bytes
                    it[DownloadKeys.PATH] = file.absolutePath
                }
                _state.value = DownloadState.PAUSED
            }
        }
    }

    fun pause() {
        downloadJob?.cancel()
        completedNormally = false
        _state.value = DownloadState.PAUSED
    }

    fun delete() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadJob?.cancel()
            isoFile?.delete()
            ctx?.downloadStore?.edit {
                it[DownloadKeys.COMPLETED] = false
                it.remove(DownloadKeys.VERIFIED_SHA256)
                it.remove(DownloadKeys.PATH)
                it.remove(DownloadKeys.BYTES)
                it.remove(DownloadKeys.TOTAL)
            }
            _downloadedBytes.value = 0
            _progress.value = 0f
            _verificationProgress.value = 0f
            _state.value = DownloadState.NOT_DOWNLOADED
        }
    }

    private fun verifyChecksum(meta: Metadata) {
        val file = isoFile ?: return
        val context = ctx ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                _verificationProgress.value = 0f

                val digest = MessageDigest.getInstance("SHA-256")
                val input = FileInputStream(file)
                val buffer = ByteArray(64 * 1024)
                val totalBytes = file.length()
                var bytesRead = 0L

                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                    bytesRead += read


                    _verificationProgress.value = (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
                }
                input.close()

                val hash = digest.digest().joinToString("") { "%02x".format(it) }

                if (hash.equals(meta.file.sha256, ignoreCase = true)) {
                    context.downloadStore.edit {
                        it[DownloadKeys.COMPLETED] = true
                        it[DownloadKeys.VERIFIED_SHA256] = meta.file.sha256
                        it[DownloadKeys.PATH] = file.absolutePath
                    }
                    _state.value = DownloadState.VERIFIED
                } else {
                    _state.value = DownloadState.CORRUPT
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = DownloadState.CORRUPT
            }
        }
    }

    override fun onCleared() {
        downloadJob?.cancel()
        super.onCleared()
    }
}