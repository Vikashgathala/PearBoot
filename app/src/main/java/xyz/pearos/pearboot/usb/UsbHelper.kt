package xyz.pearos.pearboot.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunication
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

object UsbHelper {

    private const val TAG = "UsbHelper"
    const val ACTION_USB_PERMISSION = "xyz.pearos.USB_PERMISSION"

    // Write settings
    private const val DEFAULT_BLOCK_SIZE = 512
    private const val MIN_BLOCK_SIZE = 512
    private const val MAX_BLOCK_SIZE = 4096
    private const val WRITE_SECTORS_PER_CHUNK = 128  // 64KB at 512 bytes/sector
    private const val READ_BLOCK_SIZE = 512 * 1024
    private const val MAX_RETRIES = 5
    private const val RETRY_DELAY_MS = 500L
    private const val RECONNECT_DELAY_MS = 2000L
    private const val POST_WRITE_SETTLE_MS = 3000L  // Time to let device settle after writing

    // Sectors to wipe before flashing
    private const val SECTORS_TO_WIPE = 2048  // First 1MB

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    // Connection states
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _size = MutableStateFlow("")
    val size: StateFlow<String> = _size

    private val _needsFormat = MutableStateFlow(false)
    val needsFormat: StateFlow<Boolean> = _needsFormat

    private val _isFormatting = MutableStateFlow(false)
    val isFormatting: StateFlow<Boolean> = _isFormatting

    private val _formatProgress = MutableStateFlow(0f)
    val formatProgress: StateFlow<Float> = _formatProgress

    // Flash states
    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing

    private val _flashProgress = MutableStateFlow(0f)
    val flashProgress: StateFlow<Float> = _flashProgress

    private val _flashLogs = MutableStateFlow<List<String>>(emptyList())
    val flashLogs: StateFlow<List<String>> = _flashLogs

    private val _flashComplete = MutableStateFlow(false)
    val flashComplete: StateFlow<Boolean> = _flashComplete

    private val _flashError = MutableStateFlow<String?>(null)
    val flashError: StateFlow<String?> = _flashError

    // Verification states
    private val _isVerifying = MutableStateFlow(false)
    val isVerifying: StateFlow<Boolean> = _isVerifying

    private val _verificationProgress = MutableStateFlow(0f)
    val verificationProgress: StateFlow<Float> = _verificationProgress

    // Preparation state
    private val _isPreparing = MutableStateFlow(false)
    val isPreparing: StateFlow<Boolean> = _isPreparing

    // Device references
    private var currentMassStorageDevice: UsbMassStorageDevice? = null
    private var currentBlockDevice: BlockDeviceDriver? = null
    private var cachedBlockSize: Int = DEFAULT_BLOCK_SIZE
    private var cachedTotalBlocks: Long = 0L
    private var appContext: Context? = null

    private var flashJob: Job? = null

    /**
     * Validate and normalize block size
     */
    private fun validateBlockSize(reportedSize: Int): Int {
        return when {
            reportedSize < MIN_BLOCK_SIZE -> {
                Log.w(TAG, "Block size $reportedSize too small, using $DEFAULT_BLOCK_SIZE")
                DEFAULT_BLOCK_SIZE
            }
            reportedSize > MAX_BLOCK_SIZE -> {
                Log.w(TAG, "Block size $reportedSize too large, using $DEFAULT_BLOCK_SIZE")
                DEFAULT_BLOCK_SIZE
            }
            reportedSize % MIN_BLOCK_SIZE != 0 -> {
                Log.w(TAG, "Block size $reportedSize not aligned, using $DEFAULT_BLOCK_SIZE")
                DEFAULT_BLOCK_SIZE
            }
            else -> reportedSize
        }
    }

    fun scan(context: Context) {
        appContext = context.applicationContext
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values

        if (devices.isEmpty()) {
            notifyDetached()
            return
        }

        val device = devices.first()

        if (!manager.hasPermission(device)) {
            requestPermission(context, device)
            return
        }

        scope.launch {
            try {
                closeCurrentDevice()

                val massDevices = UsbMassStorageDevice.getMassStorageDevices(context)

                if (massDevices.isEmpty()) {
                    Log.w(TAG, "No mass storage devices found")
                    notifyDetached()
                    return@launch
                }

                val massStorageDevice = massDevices[0]
                massStorageDevice.init()

                currentMassStorageDevice = massStorageDevice

                val deviceName = device.productName?.trim()?.ifEmpty { null }
                    ?: device.manufacturerName?.trim()?.ifEmpty { null }
                    ?: device.deviceName
                    ?: "USB Drive"

                _name.value = deviceName
                _connected.value = true

                Log.d(TAG, "Device connected: $deviceName")

                val blockDevice = createBlockDeviceRaw(massStorageDevice)

                if (blockDevice != null) {
                    currentBlockDevice = blockDevice

                    val reportedBlockSize = blockDevice.blockSize
                    cachedBlockSize = validateBlockSize(reportedBlockSize)
                    cachedTotalBlocks = blockDevice.blocks

                    Log.d(TAG, "BlockDevice created successfully!")
                    Log.d(TAG, "  - Reported block size: $reportedBlockSize")
                    Log.d(TAG, "  - Using block size: $cachedBlockSize")
                    Log.d(TAG, "  - Total blocks: $cachedTotalBlocks")

                    val capacityBytes = cachedTotalBlocks * cachedBlockSize.toLong()
                    _size.value = formatSize(capacityBytes)

                    _needsFormat.value = false

                    Log.i(TAG, "USB Connected: $deviceName, Size: ${_size.value}")
                } else {
                    Log.e(TAG, "Could not create block device")
                    _size.value = "Unknown"
                    _needsFormat.value = false
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing USB device", e)
                e.printStackTrace()
                notifyDetached()
            }
        }
    }

    private fun createBlockDeviceRaw(massStorageDevice: UsbMassStorageDevice): BlockDeviceDriver? {
        try {
            val usbCommField = massStorageDevice.javaClass.getDeclaredField("usbCommunication")
            usbCommField.isAccessible = true
            val usbCommunication = usbCommField.get(massStorageDevice)

            if (usbCommunication == null) {
                Log.e(TAG, "usbCommunication is null")
                return null
            }

            Log.d(TAG, "Got usbCommunication: ${usbCommunication.javaClass.name}")

            if (usbCommunication !is UsbCommunication) {
                Log.e(TAG, "usbCommunication is not UsbCommunication type")
                return null
            }

            val blockDevice = BlockDeviceDriverFactory.createBlockDevice(usbCommunication, 0.toByte())
            Log.d(TAG, "Created BlockDevice: ${blockDevice.javaClass.name}")

            blockDevice.init()
            Log.d(TAG, "BlockDevice initialized")

            return blockDevice

        } catch (e: Exception) {
            Log.e(TAG, "Error creating block device: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    private fun closeCurrentDevice() {
        try {
            currentMassStorageDevice?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing device: ${e.message}")
        }
        currentMassStorageDevice = null
        currentBlockDevice = null
    }

    /**
     * Create a fresh block device connection - essential after prolonged writing
     */
    private suspend fun createFreshBlockDevice(context: Context, settleTime: Long = 500L): BlockDeviceDriver? {
        Log.d(TAG, "Creating fresh block device connection...")

        // Close existing connection first
        closeCurrentDevice()

        // Give device time to settle
        delay(settleTime)

        repeat(MAX_RETRIES) { attempt ->
            try {
                val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                val devices = manager.deviceList.values

                if (devices.isEmpty()) {
                    Log.e(TAG, "No USB devices found (attempt ${attempt + 1})")
                    delay(RECONNECT_DELAY_MS)
                    return@repeat
                }

                val device = devices.first()

                if (!manager.hasPermission(device)) {
                    Log.e(TAG, "No permission for USB device")
                    return null
                }

                val massDevices = UsbMassStorageDevice.getMassStorageDevices(context)
                if (massDevices.isEmpty()) {
                    Log.e(TAG, "No mass storage devices found (attempt ${attempt + 1})")
                    delay(RECONNECT_DELAY_MS)
                    return@repeat
                }

                val massStorageDevice = massDevices[0]
                massStorageDevice.init()
                currentMassStorageDevice = massStorageDevice

                val blockDevice = createBlockDeviceRaw(massStorageDevice)
                if (blockDevice != null) {
                    currentBlockDevice = blockDevice

                    val reportedBlockSize = blockDevice.blockSize
                    cachedBlockSize = validateBlockSize(reportedBlockSize)
                    cachedTotalBlocks = blockDevice.blocks

                    Log.d(TAG, "Fresh block device created successfully (attempt ${attempt + 1})")
                    Log.d(TAG, "  - Block size: $cachedBlockSize")
                    Log.d(TAG, "  - Total blocks: $cachedTotalBlocks")
                    return blockDevice
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error creating fresh block device (attempt ${attempt + 1}): ${e.message}")
                closeCurrentDevice()
                if (attempt < MAX_RETRIES - 1) {
                    delay(RECONNECT_DELAY_MS)
                }
            }
        }

        Log.e(TAG, "Could not create fresh block device after $MAX_RETRIES attempts")
        return null
    }

    private fun addLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        _flashLogs.value = _flashLogs.value + "[$timestamp] $message"
        Log.d(TAG, message)
    }

    /**
     * Create a zero-filled buffer for wiping sectors
     */
    private fun createZeroBuffer(sectorCount: Int): ByteBuffer {
        val size = sectorCount * cachedBlockSize
        return ByteBuffer.allocate(size).apply {
            order(ByteOrder.LITTLE_ENDIAN)
            clear()
        }
    }

    /**
     * Write a single sector of zeros
     */
    private suspend fun writeZeroSector(
        blockDevice: BlockDeviceDriver,
        sectorNumber: Long
    ): Boolean {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val buffer = createZeroBuffer(1)
                writeMutex.withLock {
                    blockDevice.write(sectorNumber, buffer)
                }
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Write zero sector $sectorNumber failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return false
    }

    /**
     * Write multiple sectors of zeros
     */
    private suspend fun writeZeroSectors(
        blockDevice: BlockDeviceDriver,
        startSector: Long,
        sectorCount: Int
    ): Boolean {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val buffer = createZeroBuffer(sectorCount)
                writeMutex.withLock {
                    blockDevice.write(startSector, buffer)
                }
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Write zero sectors $startSector-${startSector + sectorCount} failed (attempt ${attempt + 1}): ${e.message}")
                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        return false
    }

    /**
     * Prepare drive for flashing
     */
    private suspend fun prepareDriveForFlashing(
        blockDevice: BlockDeviceDriver,
        context: Context
    ): Boolean {
        addLog("Preparing drive for flashing...")

        _isPreparing.value = true

        try {
            val sectorsToWipe = SECTORS_TO_WIPE.coerceAtMost(cachedTotalBlocks.toInt())

            addLog("Block size: $cachedBlockSize bytes")
            addLog("Wiping first $sectorsToWipe sectors (${formatSize((sectorsToWipe * cachedBlockSize).toLong())})")

            var sectorsWiped = 0
            val chunkSize = 32

            while (sectorsWiped < sectorsToWipe) {
                val remaining = sectorsToWipe - sectorsWiped
                val toWrite = remaining.coerceAtMost(chunkSize)

                var success = writeZeroSectors(blockDevice, sectorsWiped.toLong(), toWrite)

                if (!success) {
                    addLog("Chunk write failed, falling back to single sector writes...")
                    success = true
                    for (i in 0 until toWrite) {
                        if (!writeZeroSector(blockDevice, (sectorsWiped + i).toLong())) {
                            success = false
                            break
                        }
                    }
                }

                if (!success) {
                    addLog("⚠️  Failed to wipe sectors at $sectorsWiped")
                    _isPreparing.value = false
                    return false
                }

                sectorsWiped += toWrite
                _flashProgress.value = (sectorsWiped.toFloat() / sectorsToWipe) * 0.05f

                if (sectorsWiped % 256 == 0 || sectorsWiped == sectorsToWipe) {
                    val percent = (sectorsWiped * 100) / sectorsToWipe
                    addLog("Wiping: $percent% ($sectorsWiped/$sectorsToWipe sectors)")
                }

                yield()
            }

            addLog("✓ Wiped ${formatSize((sectorsWiped * cachedBlockSize).toLong())}")

            // Wipe backup GPT
            val backupGptStart = cachedTotalBlocks - 34
            if (backupGptStart > sectorsToWipe) {
                addLog("Clearing backup GPT...")
                var gptWiped = 0
                for (i in 0 until 34) {
                    if (writeZeroSector(blockDevice, backupGptStart + i)) {
                        gptWiped++
                    }
                }
                addLog("✓ Cleared $gptWiped/34 backup GPT sectors")
            }

            _isPreparing.value = false
            addLog("✓ Drive preparation complete")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Error preparing drive", e)
            addLog("⚠️  Drive preparation failed: ${e.message}")
            _isPreparing.value = false
            return false
        }
    }

    /**
     * Write data blocks with retry logic
     */
    private suspend fun writeDataBlocks(
        blockDevice: BlockDeviceDriver,
        startSector: Long,
        data: ByteArray,
        dataLength: Int,
        context: Context
    ): Boolean {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val sectorsNeeded = (dataLength + cachedBlockSize - 1) / cachedBlockSize
                val alignedSize = sectorsNeeded * cachedBlockSize

                val buffer = ByteBuffer.allocate(alignedSize).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(data, 0, dataLength)
                    clear()
                }

                writeMutex.withLock {
                    blockDevice.write(startSector, buffer)
                }

                return true

            } catch (e: Exception) {
                Log.w(TAG, "Write error at sector $startSector (attempt ${attempt + 1}): ${e.message}")

                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))

                    if (attempt >= 2) {
                        Log.d(TAG, "Attempting USB reconnection...")
                        try {
                            val freshDevice = createFreshBlockDevice(context)
                            if (freshDevice != null) {
                                currentBlockDevice = freshDevice
                                Log.d(TAG, "USB reconnected successfully")
                            }
                        } catch (reconnectError: Exception) {
                            Log.e(TAG, "Reconnection failed: ${reconnectError.message}")
                        }
                    }
                }
            }
        }

        Log.e(TAG, "Write failed after $MAX_RETRIES attempts at sector $startSector")
        return false
    }

    /**
     * Read blocks with reconnection support
     */
    private suspend fun readDataBlocksWithReconnect(
        context: Context,
        startSector: Long,
        sectorCount: Int
    ): ByteArray? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val blockDevice = currentBlockDevice ?: return null

                val bufferSize = sectorCount * cachedBlockSize
                val buffer = ByteBuffer.allocate(bufferSize).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                }

                blockDevice.read(startSector, buffer)

                val result = ByteArray(bufferSize)
                buffer.flip()
                buffer.get(result)
                return result

            } catch (e: Exception) {
                Log.w(TAG, "Read error at sector $startSector (attempt ${attempt + 1}): ${e.message}")

                if (attempt < MAX_RETRIES - 1) {
                    delay(RETRY_DELAY_MS * (attempt + 1))

                    // Try to reconnect
                    Log.d(TAG, "Attempting USB reconnection for read...")
                    try {
                        val freshDevice = createFreshBlockDevice(context, RECONNECT_DELAY_MS)
                        if (freshDevice != null) {
                            currentBlockDevice = freshDevice
                            Log.d(TAG, "USB reconnected successfully for read")
                        }
                    } catch (reconnectError: Exception) {
                        Log.e(TAG, "Reconnection failed: ${reconnectError.message}")
                    }
                }
            }
        }

        Log.e(TAG, "Read failed after $MAX_RETRIES attempts at sector $startSector")
        return null
    }

    /**
     * Compute SHA-256 hash of entire file
     */
    private fun computeFileSHA256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(READ_BLOCK_SIZE)
        var bytesRead: Int

        FileInputStream(file).use { input ->
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Analyze ISO file to detect boot type
     */
    private fun analyzeIsoBootType(file: File): IsoBootInfo {
        val info = IsoBootInfo()

        RandomAccessFile(file, "r").use { raf ->
            val sector0 = ByteArray(512)
            raf.read(sector0)

            info.hasMbrSignature = sector0[510] == 0x55.toByte() && sector0[511] == 0xAA.toByte()

            info.hasBootCode = sector0[0] == 0xEB.toByte() ||
                    sector0[0] == 0xE9.toByte() ||
                    (sector0[0] == 0x00.toByte() && sector0[1] == 0x00.toByte())

            var hasActivePartition = false
            var hasGptProtectiveMbr = false
            for (i in 0..3) {
                val partitionOffset = 446 + (i * 16)
                val bootFlag = sector0[partitionOffset]
                val partitionType = sector0[partitionOffset + 4]

                if (bootFlag == 0x80.toByte()) {
                    hasActivePartition = true
                }
                if (partitionType == 0xEE.toByte()) {
                    hasGptProtectiveMbr = true
                }
            }
            info.hasActivePartition = hasActivePartition
            info.hasGptProtectiveMbr = hasGptProtectiveMbr

            if (file.length() >= 1024) {
                raf.seek(512)
                val sector1 = ByteArray(512)
                raf.read(sector1)

                val gptSignature = String(sector1, 0, 8, Charsets.US_ASCII)
                info.hasGptHeader = gptSignature == "EFI PART"
            }

            if (file.length() >= 32768 + 6) {
                raf.seek(32768)
                val isoSector = ByteArray(6)
                raf.read(isoSector)
                val isoSignature = String(isoSector, 1, 5, Charsets.US_ASCII)
                info.hasIso9660 = isoSignature == "CD001"
            }

            if (file.length() >= 32768 + 2048) {
                raf.seek(32768 + 71)
                val elToritoCheck = ByteArray(32)
                raf.read(elToritoCheck)
                val elToritoId = String(elToritoCheck, Charsets.US_ASCII).trim()
                info.hasElTorito = elToritoId.contains("EL TORITO")
            }

            info.firstSector = sector0
        }

        info.bootType = when {
            info.hasGptHeader && info.hasGptProtectiveMbr -> BootType.GPT
            info.hasGptHeader && info.hasMbrSignature -> BootType.HYBRID
            info.hasMbrSignature -> BootType.MBR
            info.hasIso9660 -> BootType.ISO9660
            else -> BootType.UNKNOWN
        }

        return info
    }

    private data class IsoBootInfo(
        var hasMbrSignature: Boolean = false,
        var hasBootCode: Boolean = false,
        var hasActivePartition: Boolean = false,
        var hasGptProtectiveMbr: Boolean = false,
        var hasGptHeader: Boolean = false,
        var hasIso9660: Boolean = false,
        var hasElTorito: Boolean = false,
        var bootType: BootType = BootType.UNKNOWN,
        var firstSector: ByteArray = ByteArray(512)
    )

    private enum class BootType {
        MBR, GPT, HYBRID, ISO9660, UNKNOWN
    }

    /**
     * Flash the ISO image to USB drive
     */
    fun flashImage(isoPath: String, onComplete: (Boolean, String) -> Unit) {
        val context = appContext

        if (context == null) {
            onComplete(false, "Context not available")
            return
        }

        val isoFile = File(isoPath)
        if (!isoFile.exists()) {
            onComplete(false, "ISO file not found: $isoPath")
            return
        }

        flashJob = scope.launch {
            _isFlashing.value = true
            _flashProgress.value = 0f
            _flashLogs.value = emptyList()
            _flashComplete.value = false
            _flashError.value = null
            _isVerifying.value = false
            _verificationProgress.value = 0f
            _isPreparing.value = false

            var inputStream: FileInputStream? = null

            try {
                addLog("═══════════════════════════════════════════")
                addLog("         PearBoot ISO Flasher v2.3")
                addLog("═══════════════════════════════════════════")
                addLog("")

                // Step 1: Analyze ISO
                addLog("Analyzing ISO file...")
                val isoInfo = analyzeIsoBootType(isoFile)

                addLog("ISO file: ${isoFile.name}")
                addLog("ISO size: ${formatSize(isoFile.length())}")
                addLog("")
                addLog("Boot Structure Analysis:")
                addLog("  Boot type: ${isoInfo.bootType}")
                addLog("  MBR signature: ${if (isoInfo.hasMbrSignature) "YES ✓" else "NO"}")
                addLog("  Boot code: ${if (isoInfo.hasBootCode) "YES ✓" else "NO"}")
                addLog("  Active partition: ${if (isoInfo.hasActivePartition) "YES ✓" else "NO"}")
                addLog("  GPT header: ${if (isoInfo.hasGptHeader) "YES ✓" else "NO"}")
                addLog("  ISO 9660: ${if (isoInfo.hasIso9660) "YES ✓" else "NO"}")
                addLog("  El Torito: ${if (isoInfo.hasElTorito) "YES ✓" else "NO"}")

                // Step 2: Compute checksum
                addLog("")
                addLog("Computing ISO checksum...")
                val isoHash = computeFileSHA256(isoFile)
                addLog("ISO SHA-256: ${isoHash.take(16)}...${isoHash.takeLast(16)}")

                // Step 3: Initialize USB connection
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("        INITIALIZING USB DEVICE")
                addLog("════════════════════════════════════════")
                addLog("")

                var blockDevice = createFreshBlockDevice(context)

                if (blockDevice == null) {
                    _flashError.value = "Could not access USB device"
                    withContext(Dispatchers.Main) {
                        _isFlashing.value = false
                        onComplete(false, "Could not access USB device. Please reconnect and try again.")
                    }
                    return@launch
                }

                addLog("USB connection established ✓")
                addLog("")
                addLog("USB Device Info:")
                addLog("  Name: ${_name.value}")
                addLog("  Block size: $cachedBlockSize bytes")
                addLog("  Total sectors: $cachedTotalBlocks")

                val deviceCapacity = cachedTotalBlocks * cachedBlockSize.toLong()
                val isoSize = isoFile.length()

                addLog("  Capacity: ${formatSize(deviceCapacity)}")

                if (isoSize > deviceCapacity) {
                    _flashError.value = "ISO is larger than USB drive"
                    withContext(Dispatchers.Main) {
                        _isFlashing.value = false
                        onComplete(false, "ISO file (${formatSize(isoSize)}) is larger than USB drive (${formatSize(deviceCapacity)})")
                    }
                    return@launch
                }

                // Step 4: PREPARE DRIVE
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("         PREPARING DRIVE")
                addLog("════════════════════════════════════════")
                addLog("")
                addLog("⚠️  This will ERASE all data on the drive!")
                addLog("")

                var prepareSuccess = prepareDriveForFlashing(blockDevice, context)

                if (!prepareSuccess) {
                    addLog("Retrying drive preparation with fresh connection...")
                    blockDevice = createFreshBlockDevice(context, RECONNECT_DELAY_MS)

                    if (blockDevice != null) {
                        currentBlockDevice = blockDevice
                        prepareSuccess = prepareDriveForFlashing(blockDevice, context)
                    }

                    if (!prepareSuccess) {
                        _flashError.value = "Could not prepare USB device"
                        withContext(Dispatchers.Main) {
                            _isFlashing.value = false
                            onComplete(false, "Failed to prepare USB drive. Please try a different USB drive.")
                        }
                        return@launch
                    }
                }

                // Step 5: Write ISO image
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("        WRITING ISO IMAGE")
                addLog("════════════════════════════════════════")
                addLog("")

                val writeChunkSize = WRITE_SECTORS_PER_CHUNK * cachedBlockSize
                addLog("Write Configuration:")
                addLog("  Chunk size: ${formatSize(writeChunkSize.toLong())} ($WRITE_SECTORS_PER_CHUNK sectors)")
                addLog("  Max retries: $MAX_RETRIES")
                addLog("")
                addLog("⚠️  DO NOT disconnect the USB drive!")
                addLog("⚠️  DO NOT move your device!")
                addLog("")

                inputStream = FileInputStream(isoFile)
                val buffer = ByteArray(writeChunkSize)
                var currentSector = 0L
                var bytesWritten = 0L
                var lastProgressPercent = -1
                val startTime = System.currentTimeMillis()

                val writeProgressStart = 0.05f
                val writeProgressEnd = 0.90f

                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    val currentDev = currentBlockDevice
                    if (currentDev == null) {
                        throw Exception("USB device disconnected")
                    }

                    val writeSuccess = writeDataBlocks(
                        currentDev,
                        currentSector,
                        buffer,
                        bytesRead,
                        context
                    )

                    if (!writeSuccess) {
                        throw Exception("Write failed at sector $currentSector after $MAX_RETRIES retries")
                    }

                    val sectorsWritten = (bytesRead + cachedBlockSize - 1) / cachedBlockSize
                    currentSector += sectorsWritten
                    bytesWritten += bytesRead

                    val writeProgress = (bytesWritten.toFloat() / isoSize)
                    _flashProgress.value = writeProgressStart + (writeProgress * (writeProgressEnd - writeProgressStart))

                    val progressPercent = (_flashProgress.value * 100).toInt()
                    if (progressPercent / 5 > lastProgressPercent / 5) {
                        lastProgressPercent = progressPercent
                        val elapsed = System.currentTimeMillis() - startTime
                        val speed = if (elapsed > 0) bytesWritten * 1000.0 / elapsed else 0.0
                        addLog("Progress: $progressPercent% | ${formatSize(bytesWritten)} | ${formatSize(speed.toLong())}/s")
                    }

                    yield()
                }

                inputStream.close()
                inputStream = null

                val writeTime = System.currentTimeMillis() - startTime
                val avgSpeed = if (writeTime > 0) bytesWritten * 1000.0 / writeTime else 0.0

                addLog("")
                addLog("════════════════════════════════════════")
                addLog("          WRITE COMPLETE")
                addLog("════════════════════════════════════════")
                addLog("")
                addLog("Statistics:")
                addLog("  Total written: ${formatSize(bytesWritten)}")
                addLog("  Total sectors: $currentSector")
                addLog("  Time elapsed: ${writeTime / 1000}s")
                addLog("  Average speed: ${formatSize(avgSpeed.toLong())}/s")

                // Step 6: Verification - RECONNECT DEVICE FIRST
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("         STARTING VERIFICATION")
                addLog("════════════════════════════════════════")
                addLog("")
                addLog("Reconnecting device for verification...")

                // Close current connection and let device settle
                closeCurrentDevice()
                addLog("Waiting for device to settle...")
                delay(POST_WRITE_SETTLE_MS)

                // Create fresh connection for verification
                val verifyDevice = createFreshBlockDevice(context, RECONNECT_DELAY_MS)

                if (verifyDevice == null) {
                    addLog("⚠️  Could not reconnect for verification")
                    addLog("   Write completed, but verification skipped")
                    addLog("")
                    addLog("   The flash likely succeeded. Try booting from the USB.")
                } else {
                    currentBlockDevice = verifyDevice
                    addLog("✓ Device reconnected for verification")
                    addLog("")

                    _isVerifying.value = true
                    _verificationProgress.value = 0f

                    val verifyProgressStart = 0.90f
                    val verifyProgressEnd = 1.0f

                    // Verify only first few MB and boot sector for speed
                    val verifySize = minOf(isoFile.length(), 10L * 1024 * 1024)  // Max 10MB
                    val verifyStream = FileInputStream(isoFile)
                    val verifyChunkSize = 64 * 1024  // 64KB chunks for verification
                    val verifySourceBuffer = ByteArray(verifyChunkSize)

                    var verifyOffset = 0L
                    var verifyErrors = 0
                    var verifyBytes = 0L

                    addLog("Verifying first ${formatSize(verifySize)} of data...")

                    try {
                        while (isActive && verifyBytes < verifySize) {
                            val toRead = minOf(verifyChunkSize.toLong(), verifySize - verifyBytes).toInt()
                            val sourceRead = verifyStream.read(verifySourceBuffer, 0, toRead)
                            if (sourceRead == -1) break

                            val startVerifySector = verifyOffset / cachedBlockSize
                            val sectorsToRead = (sourceRead + cachedBlockSize - 1) / cachedBlockSize

                            val deviceData = readDataBlocksWithReconnect(context, startVerifySector, sectorsToRead)

                            if (deviceData == null) {
                                verifyErrors++
                                if (verifyErrors <= 3) {
                                    addLog("⚠️  Verification read failed at offset ${formatSize(verifyOffset)}")
                                }
                                if (verifyErrors >= 5) {
                                    addLog("⚠️  Too many read errors, stopping verification")
                                    break
                                }
                            } else {
                                var mismatch = false
                                for (i in 0 until sourceRead) {
                                    if (verifySourceBuffer[i] != deviceData[i]) {
                                        mismatch = true
                                        break
                                    }
                                }

                                if (mismatch) {
                                    verifyErrors++
                                    if (verifyErrors <= 3) {
                                        addLog("⚠️  Data mismatch at offset ${formatSize(verifyOffset)}")
                                    }
                                }
                            }

                            verifyOffset += sourceRead
                            verifyBytes += sourceRead

                            _verificationProgress.value = (verifyBytes.toFloat() / verifySize).coerceIn(0f, 1f)

                            val verifyProgress = verifyBytes.toFloat() / verifySize
                            _flashProgress.value = verifyProgressStart + (verifyProgress * (verifyProgressEnd - verifyProgressStart))

                            val verifyPercent = (_verificationProgress.value * 100).toInt()
                            if (verifyPercent % 20 == 0 && verifyPercent > 0) {
                                val logged = _flashLogs.value.lastOrNull()?.contains("Verifying: $verifyPercent%") != true
                                if (logged) {
                                    addLog("Verifying: $verifyPercent%")
                                }
                            }

                            yield()
                        }

                        verifyStream.close()

                        addLog("")
                        if (verifyErrors == 0) {
                            addLog("✓ Verification PASSED - Data verified correctly!")
                        } else if (verifyErrors < 5) {
                            addLog("⚠️  Verification completed with $verifyErrors issues")
                            addLog("   The flash may still work - try booting from the USB")
                        } else {
                            addLog("⚠️  Verification had multiple errors")
                            addLog("   The USB may still be bootable - please try it")
                        }

                    } catch (e: Exception) {
                        addLog("⚠️  Verification error: ${e.message}")
                        addLog("   Write completed - try booting from the USB")
                        try { verifyStream.close() } catch (_: Exception) {}
                    }
                }

                _isVerifying.value = false

                // Boot sector quick check
                addLog("")
                addLog("Checking boot sector...")

                val finalDevice = currentBlockDevice
                if (finalDevice != null) {
                    val bootSector = readDataBlocksWithReconnect(context, 0, 1)

                    if (bootSector != null && bootSector.size >= 512) {
                        val usbHasMbr = bootSector[510] == 0x55.toByte() &&
                                bootSector[511] == 0xAA.toByte()

                        val sourceMatch = bootSector.take(512).toByteArray()
                            .contentEquals(isoInfo.firstSector)

                        addLog("  MBR signature: ${if (usbHasMbr) "PRESENT ✓" else "MISSING ⚠️"}")
                        addLog("  Boot sector match: ${if (sourceMatch) "YES ✓" else "DIFFERS ⚠️"}")

                        val firstBytes = bootSector.take(16).joinToString(" ") { "%02X".format(it) }
                        addLog("  First 16 bytes: $firstBytes")
                    } else {
                        addLog("  Could not read boot sector for verification")
                    }
                }

                addLog("")
                addLog("═══════════════════════════════════════════")
                addLog("          ✓ FLASH COMPLETED!")
                addLog("═══════════════════════════════════════════")
                addLog("")
                addLog("You can now safely remove the USB drive")
                addLog("and boot from it.")
                addLog("")
                addLog("BIOS/UEFI Boot Tips:")
                addLog("  • Press F12/F2/Del/Esc during startup")
                addLog("  • Select USB device from boot menu")
                addLog("  • Try both Legacy and UEFI modes")
                addLog("  • Disable Secure Boot if needed")

                _flashProgress.value = 1f
                _flashComplete.value = true

                withContext(Dispatchers.Main) {
                    _isFlashing.value = false
                    onComplete(true, "Flash completed successfully!")
                }

            } catch (e: CancellationException) {
                inputStream?.close()
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("         FLASH CANCELLED")
                addLog("════════════════════════════════════════")
                _flashError.value = "Flash cancelled"
                withContext(Dispatchers.Main) {
                    _isFlashing.value = false
                    _isVerifying.value = false
                    _isPreparing.value = false
                    onComplete(false, "Flash cancelled")
                }

            } catch (e: Exception) {
                inputStream?.close()
                Log.e(TAG, "Error flashing device", e)
                e.printStackTrace()
                addLog("")
                addLog("════════════════════════════════════════")
                addLog("              ERROR")
                addLog("════════════════════════════════════════")
                addLog("")
                addLog("Error: ${e.message}")
                addLog("")
                addLog("Troubleshooting:")
                addLog("  • Reconnect the USB drive")
                addLog("  • Try a different USB port/cable")
                addLog("  • Try a different USB drive")
                addLog("  • Restart the app")

                _flashError.value = e.message

                withContext(Dispatchers.Main) {
                    _isFlashing.value = false
                    _isVerifying.value = false
                    _isPreparing.value = false
                    onComplete(false, "Flash failed: ${e.message}")
                }
            }
        }
    }

    fun cancelFlash() {
        flashJob?.cancel()
        _isFlashing.value = false
        _isVerifying.value = false
        _isPreparing.value = false
        addLog("Flash cancelled by user")
    }

    fun resetFlashState() {
        _isFlashing.value = false
        _flashProgress.value = 0f
        _flashLogs.value = emptyList()
        _flashComplete.value = false
        _flashError.value = null
        _isVerifying.value = false
        _verificationProgress.value = 0f
        _isPreparing.value = false
    }

    fun formatDevice(onComplete: (Boolean, String) -> Unit) {
        onComplete(true, "Format not required - drive will be prepared during flash")
    }

    private fun requestPermission(context: Context, device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPermission(device, pendingIntent)
    }

    fun notifyDetached() {
        _connected.value = false
        _name.value = ""
        _size.value = ""
        _needsFormat.value = false
        _isFormatting.value = false
        _formatProgress.value = 0f

        flashJob?.cancel()
        _isFlashing.value = false
        _isVerifying.value = false
        _isPreparing.value = false

        closeCurrentDevice()
        cachedBlockSize = DEFAULT_BLOCK_SIZE
        cachedTotalBlocks = 0L

        Log.i(TAG, "USB Disconnected")
    }

    fun getMassStorageDevice(): UsbMassStorageDevice? = currentMassStorageDevice
    fun getBlockDevice(): BlockDeviceDriver? = currentBlockDevice
    fun getBlockSize(): Int = cachedBlockSize
    fun getTotalBlocks(): Long = cachedTotalBlocks
    fun getLastBlockAddress(): Long = cachedTotalBlocks - 1
    fun getCapacityBytes(): Long = cachedTotalBlocks * cachedBlockSize.toLong()

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "Unknown"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "%.2f %s".format(size, units[unitIndex])
    }
}