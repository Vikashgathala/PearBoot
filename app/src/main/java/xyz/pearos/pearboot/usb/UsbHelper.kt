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
import me.jahnen.libaums.core.UsbMassStorageDevice
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.usb.UsbCommunication
import java.nio.ByteBuffer
import java.nio.ByteOrder

object UsbHelper {

    private const val TAG = "UsbHelper"
    const val ACTION_USB_PERMISSION = "xyz.pearos.USB_PERMISSION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    // Store references
    private var currentMassStorageDevice: UsbMassStorageDevice? = null
    private var currentBlockDevice: BlockDeviceDriver? = null
    private var cachedBlockSize: Int = 512
    private var cachedTotalBlocks: Long = 0L
    private var appContext: Context? = null

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
                // Close any existing device first
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

                // Get device name
                val deviceName = device.productName?.trim()?.ifEmpty { null }
                    ?: device.manufacturerName?.trim()?.ifEmpty { null }
                    ?: device.deviceName
                    ?: "USB Drive"

                _name.value = deviceName
                _connected.value = true

                Log.d(TAG, "Device connected: $deviceName")

                // Create block device directly using usbCommunication
                val blockDevice = createBlockDevice(massStorageDevice)

                if (blockDevice != null) {
                    currentBlockDevice = blockDevice

                    cachedBlockSize = blockDevice.blockSize
                    cachedTotalBlocks = blockDevice.blocks

                    Log.d(TAG, "BlockDevice created successfully!")
                    Log.d(TAG, "  - Block size: $cachedBlockSize")
                    Log.d(TAG, "  - Total blocks: $cachedTotalBlocks")
                    Log.d(TAG, "  - Last block address: ${cachedTotalBlocks - 1}")

                    val capacityBytes = cachedTotalBlocks * cachedBlockSize.toLong()

                    Log.d(TAG, "  - Total capacity: $capacityBytes bytes (${formatSize(capacityBytes)})")

                    _size.value = formatSize(capacityBytes)

                    // Check if filesystem is supported
                    val partitions = try {
                        massStorageDevice.partitions
                    } catch (e: Exception) {
                        Log.w(TAG, "Error getting partitions: ${e.message}")
                        emptyList()
                    }

                    val hasValidFs = partitions.isNotEmpty() && partitions.any {
                        try {
                            it.fileSystem != null
                        } catch (e: Exception) {
                            false
                        }
                    }

                    if (hasValidFs) {
                        _needsFormat.value = false
                        Log.i(TAG, "USB Connected with valid filesystem: $deviceName, Size: ${_size.value}")
                    } else {
                        _needsFormat.value = true
                        Log.w(TAG, "USB Connected but filesystem unsupported: $deviceName, Size: ${_size.value}")
                    }
                } else {
                    Log.e(TAG, "Could not create block device")
                    _size.value = "Unknown"
                    _needsFormat.value = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing USB device", e)
                e.printStackTrace()
                notifyDetached()
            }
        }
    }

    /**
     * Create a BlockDeviceDriver by extracting usbCommunication from UsbMassStorageDevice
     * and using BlockDeviceDriverFactory
     */
    private fun createBlockDevice(massStorageDevice: UsbMassStorageDevice): BlockDeviceDriver? {
        try {
            // Get usbCommunication field via reflection
            val usbCommField = massStorageDevice.javaClass.getDeclaredField("usbCommunication")
            usbCommField.isAccessible = true
            val usbCommunication = usbCommField.get(massStorageDevice)

            if (usbCommunication == null) {
                Log.e(TAG, "usbCommunication is null")
                return null
            }

            Log.d(TAG, "Got usbCommunication: ${usbCommunication.javaClass.name}")

            // Cast to UsbCommunication interface
            if (usbCommunication !is UsbCommunication) {
                Log.e(TAG, "usbCommunication is not UsbCommunication type")
                return null
            }

            // Create block device using the factory with LUN 0
            val blockDevice = BlockDeviceDriverFactory.createBlockDevice(usbCommunication, 0.toByte())
            Log.d(TAG, "Created BlockDevice: ${blockDevice.javaClass.name}")

            // Initialize the block device
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
     * Ensure we have a valid block device, reinitializing if necessary
     */
    private suspend fun ensureBlockDevice(context: Context): Boolean {
        if (currentBlockDevice != null && cachedTotalBlocks > 0) {
            Log.d(TAG, "Block device already available")
            return true
        }

        Log.d(TAG, "Block device not available, re-initializing...")

        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val devices = manager.deviceList.values

        if (devices.isEmpty()) {
            Log.e(TAG, "No USB devices found")
            return false
        }

        val device = devices.first()

        if (!manager.hasPermission(device)) {
            Log.e(TAG, "No permission for USB device")
            return false
        }

        try {
            closeCurrentDevice()

            val massDevices = UsbMassStorageDevice.getMassStorageDevices(context)
            if (massDevices.isEmpty()) {
                Log.e(TAG, "No mass storage devices found")
                return false
            }

            val massStorageDevice = massDevices[0]
            massStorageDevice.init()
            currentMassStorageDevice = massStorageDevice

            // Create block device
            val blockDevice = createBlockDevice(massStorageDevice)
            if (blockDevice != null) {
                currentBlockDevice = blockDevice
                cachedBlockSize = blockDevice.blockSize
                cachedTotalBlocks = blockDevice.blocks
                Log.d(TAG, "Block device re-initialized successfully")
                Log.d(TAG, "  - Block size: $cachedBlockSize")
                Log.d(TAG, "  - Total blocks: $cachedTotalBlocks")
                return true
            }

            Log.e(TAG, "Could not create block device after re-init")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Error re-initializing device: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Format the USB drive to FAT32.
     */
    fun formatDevice(onComplete: (Boolean, String) -> Unit) {
        val context = appContext

        if (context == null) {
            onComplete(false, "Context not available")
            return
        }

        scope.launch {
            _isFormatting.value = true
            _formatProgress.value = 0f

            try {
                // Ensure we have a valid block device
                if (!ensureBlockDevice(context)) {
                    withContext(Dispatchers.Main) {
                        _isFormatting.value = false
                        onComplete(false, "Could not access USB device. Please reconnect and try again.")
                    }
                    return@launch
                }

                val blockDevice = currentBlockDevice
                if (blockDevice == null) {
                    withContext(Dispatchers.Main) {
                        _isFormatting.value = false
                        onComplete(false, "Block device not available")
                    }
                    return@launch
                }

                Log.i(TAG, "Starting FAT32 format...")
                Log.d(TAG, "Device: totalBlocks=$cachedTotalBlocks, blockSize=$cachedBlockSize")

                val totalBlocks = cachedTotalBlocks
                val totalBytes = totalBlocks * cachedBlockSize

                // Validate size (FAT32 supports 32MB to 2TB)
                if (totalBytes < 32 * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        _isFormatting.value = false
                        onComplete(false, "Device too small for FAT32 (min 32MB)")
                    }
                    return@launch
                }

                _formatProgress.value = 0.05f

                // Step 1: Write MBR
                Log.d(TAG, "Step 1: Writing MBR...")
                writeMBR(blockDevice, totalBlocks, cachedBlockSize)
                _formatProgress.value = 0.15f

                // Step 2: Calculate FAT32 parameters
                val fat32Params = calculateFat32Params(totalBlocks, cachedBlockSize)
                _formatProgress.value = 0.20f

                // Step 3: Write FAT32 Boot Sector
                Log.d(TAG, "Step 2: Writing FAT32 boot sector...")
                writeFat32BootSector(blockDevice, fat32Params, cachedBlockSize)
                _formatProgress.value = 0.30f

                // Step 4: Write FSInfo sector
                Log.d(TAG, "Step 3: Writing FSInfo...")
                writeFSInfo(blockDevice, fat32Params, cachedBlockSize)
                _formatProgress.value = 0.40f

                // Step 5: Write backup boot sector
                Log.d(TAG, "Step 4: Writing backup boot sector...")
                writeBackupBootSector(blockDevice, fat32Params, cachedBlockSize)
                _formatProgress.value = 0.50f

                // Step 6: Initialize FAT tables
                Log.d(TAG, "Step 5: Initializing FAT tables...")
                initializeFatTables(blockDevice, fat32Params, cachedBlockSize)
                _formatProgress.value = 0.80f

                // Step 7: Initialize root directory
                Log.d(TAG, "Step 6: Initializing root directory...")
                initializeRootDirectory(blockDevice, fat32Params, cachedBlockSize)
                _formatProgress.value = 0.95f

                Log.i(TAG, "FAT32 format completed successfully!")

                // Close and reinitialize device
                closeCurrentDevice()

                delay(1500) // Give device time to settle

                _formatProgress.value = 1f

                // Rescan device
                withContext(Dispatchers.Main) {
                    _isFormatting.value = false
                    _needsFormat.value = false
                    onComplete(true, "Format completed successfully")

                    // Trigger rescan
                    scan(context)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error formatting device", e)
                e.printStackTrace()

                withContext(Dispatchers.Main) {
                    _isFormatting.value = false
                    onComplete(false, "Format failed: ${e.message}")
                }
            }
        }
    }

    // ==================== FAT32 Formatting Implementation ====================

    private data class Fat32Params(
        val totalSectors: Long,
        val sectorsPerCluster: Int,
        val reservedSectors: Int,
        val fatSize: Long,
        val rootCluster: Int,
        val partitionStartSector: Long,
        val partitionSectors: Long
    )

    private fun calculateFat32Params(totalBlocks: Long, blockSize: Int): Fat32Params {
        val partitionStartSector = 2048L
        val partitionSectors = totalBlocks - partitionStartSector

        val sectorsPerCluster = when {
            partitionSectors <= 66600L -> 1
            partitionSectors <= 532480L -> 8
            partitionSectors <= 16777216L -> 8
            partitionSectors <= 33554432L -> 16
            partitionSectors <= 67108864L -> 32
            else -> 64
        }

        val reservedSectors = 32

        val dataSectors = partitionSectors - reservedSectors
        val totalClusters = dataSectors / sectorsPerCluster
        val fatEntries = totalClusters + 2
        val fatBytes = fatEntries * 4
        val fatSize = ((fatBytes + blockSize - 1) / blockSize).toLong()

        Log.d(TAG, "FAT32 Params:")
        Log.d(TAG, "  - Partition start: $partitionStartSector")
        Log.d(TAG, "  - Partition sectors: $partitionSectors")
        Log.d(TAG, "  - Sectors per cluster: $sectorsPerCluster")
        Log.d(TAG, "  - Reserved sectors: $reservedSectors")
        Log.d(TAG, "  - FAT size (sectors): $fatSize")

        return Fat32Params(
            totalSectors = totalBlocks,
            sectorsPerCluster = sectorsPerCluster,
            reservedSectors = reservedSectors,
            fatSize = fatSize,
            rootCluster = 2,
            partitionStartSector = partitionStartSector,
            partitionSectors = partitionSectors
        )
    }

    private fun writeMBR(blockDevice: BlockDeviceDriver, totalBlocks: Long, blockSize: Int) {
        val mbr = ByteBuffer.allocate(blockSize)
        mbr.order(ByteOrder.LITTLE_ENDIAN)

        // Clear MBR
        for (i in 0 until blockSize) {
            mbr.put(0.toByte())
        }
        mbr.rewind()

        val partitionStart = 2048L
        val partitionSectors = (totalBlocks - partitionStart).coerceAtMost(0xFFFFFFFF)

        // First partition entry at offset 446
        mbr.position(446)
        mbr.put(0x00.toByte())  // Boot indicator
        mbr.put(0x00.toByte())  // CHS start head
        mbr.put(0x01.toByte())  // CHS start sector
        mbr.put(0x00.toByte())  // CHS start cylinder
        mbr.put(0x0C.toByte())  // Partition type: FAT32 LBA
        mbr.put(0xFE.toByte())  // CHS end head
        mbr.put(0xFF.toByte())  // CHS end sector
        mbr.put(0xFF.toByte())  // CHS end cylinder
        mbr.putInt(partitionStart.toInt())  // LBA start
        mbr.putInt(partitionSectors.toInt())  // Number of sectors

        // MBR signature
        mbr.position(510)
        mbr.put(0x55.toByte())
        mbr.put(0xAA.toByte())

        mbr.rewind()
        blockDevice.write(0, mbr)
        Log.d(TAG, "MBR written successfully")
    }

    private fun writeFat32BootSector(blockDevice: BlockDeviceDriver, params: Fat32Params, blockSize: Int) {
        val bootSector = ByteBuffer.allocate(blockSize)
        bootSector.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until blockSize) {
            bootSector.put(0.toByte())
        }
        bootSector.rewind()

        // Jump instruction
        bootSector.put(0xEB.toByte())
        bootSector.put(0x58.toByte())
        bootSector.put(0x90.toByte())

        // OEM Name
        val oemName = "PEARBOOT"
        for (i in 0 until 8) {
            bootSector.put(if (i < oemName.length) oemName[i].code.toByte() else 0x20.toByte())
        }

        // BPB
        bootSector.putShort(blockSize.toShort())
        bootSector.put(params.sectorsPerCluster.toByte())
        bootSector.putShort(params.reservedSectors.toShort())
        bootSector.put(2.toByte())  // Number of FATs
        bootSector.putShort(0.toShort())  // Root entry count
        bootSector.putShort(0.toShort())  // Total sectors 16
        bootSector.put(0xF8.toByte())  // Media type
        bootSector.putShort(0.toShort())  // FAT size 16
        bootSector.putShort(63.toShort())  // Sectors per track
        bootSector.putShort(255.toShort())  // Number of heads
        bootSector.putInt(params.partitionStartSector.toInt())  // Hidden sectors
        bootSector.putInt(params.partitionSectors.toInt())  // Total sectors 32

        // FAT32 Extended BPB
        bootSector.putInt(params.fatSize.toInt())
        bootSector.putShort(0.toShort())  // Ext flags
        bootSector.putShort(0.toShort())  // FS version
        bootSector.putInt(params.rootCluster)
        bootSector.putShort(1.toShort())  // FSInfo sector
        bootSector.putShort(6.toShort())  // Backup boot sector

        // Reserved
        for (i in 0 until 12) {
            bootSector.put(0.toByte())
        }

        bootSector.put(0x80.toByte())  // Drive number
        bootSector.put(0.toByte())  // Reserved
        bootSector.put(0x29.toByte())  // Boot signature
        bootSector.putInt(System.currentTimeMillis().toInt())  // Volume serial

        // Volume label
        val label = "PEARBOOT   "
        for (i in 0 until 11) {
            bootSector.put(label[i].code.toByte())
        }

        // File system type
        val fsType = "FAT32   "
        for (i in 0 until 8) {
            bootSector.put(fsType[i].code.toByte())
        }

        // Signature
        bootSector.position(510)
        bootSector.put(0x55.toByte())
        bootSector.put(0xAA.toByte())

        bootSector.rewind()
        blockDevice.write(params.partitionStartSector, bootSector)
        Log.d(TAG, "FAT32 boot sector written successfully")
    }

    private fun writeFSInfo(blockDevice: BlockDeviceDriver, params: Fat32Params, blockSize: Int) {
        val fsInfo = ByteBuffer.allocate(blockSize)
        fsInfo.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until blockSize) {
            fsInfo.put(0.toByte())
        }
        fsInfo.rewind()

        // Lead signature
        fsInfo.putInt(0x41615252)

        // Reserved
        fsInfo.position(484)

        // Structure signature
        fsInfo.putInt(0x61417272)

        // Free cluster count (unknown)
        fsInfo.putInt(-1)

        // Next free cluster hint
        fsInfo.putInt(3)

        // Reserved
        fsInfo.position(508)
        fsInfo.putShort(0.toShort())

        // Trail signature
        fsInfo.put(0x55.toByte())
        fsInfo.put(0xAA.toByte())

        fsInfo.rewind()
        blockDevice.write(params.partitionStartSector + 1, fsInfo)
        Log.d(TAG, "FSInfo written successfully")
    }

    private fun writeBackupBootSector(blockDevice: BlockDeviceDriver, params: Fat32Params, blockSize: Int) {
        // Read the original boot sector
        val bootSector = ByteBuffer.allocate(blockSize)
        bootSector.order(ByteOrder.LITTLE_ENDIAN)
        blockDevice.read(params.partitionStartSector, bootSector)

        // Write as backup at sector 6
        bootSector.rewind()
        blockDevice.write(params.partitionStartSector + 6, bootSector)

        // Write backup FSInfo at sector 7
        val fsInfo = ByteBuffer.allocate(blockSize)
        fsInfo.order(ByteOrder.LITTLE_ENDIAN)
        blockDevice.read(params.partitionStartSector + 1, fsInfo)
        fsInfo.rewind()
        blockDevice.write(params.partitionStartSector + 7, fsInfo)
        Log.d(TAG, "Backup boot sector written successfully")
    }

    private fun initializeFatTables(blockDevice: BlockDeviceDriver, params: Fat32Params, blockSize: Int) {
        val fatStartSector = params.partitionStartSector + params.reservedSectors

        // First FAT sector
        val firstFatSector = ByteBuffer.allocate(blockSize)
        firstFatSector.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until blockSize) {
            firstFatSector.put(0.toByte())
        }
        firstFatSector.rewind()

        // Entry 0: Media type
        firstFatSector.putInt(0x0FFFFFF8.toInt())
        // Entry 1: End of chain
        firstFatSector.putInt(0x0FFFFFFF.toInt())
        // Entry 2: End of chain (root directory)
        firstFatSector.putInt(0x0FFFFFFF.toInt())

        // Write first sector of FAT1
        firstFatSector.rewind()
        blockDevice.write(fatStartSector, firstFatSector)

        // Write first sector of FAT2
        firstFatSector.rewind()
        blockDevice.write(fatStartSector + params.fatSize, firstFatSector)

        // Clear remaining FAT sectors (limit for performance)
        val zeroSector = ByteBuffer.allocate(blockSize)
        for (i in 0 until blockSize) {
            zeroSector.put(0.toByte())
        }

        val maxFatSectors = params.fatSize.coerceAtMost(500)

        for (i in 1 until maxFatSectors) {
            zeroSector.rewind()
            blockDevice.write(fatStartSector + i, zeroSector)
        }

        for (i in 1 until maxFatSectors) {
            zeroSector.rewind()
            blockDevice.write(fatStartSector + params.fatSize + i, zeroSector)
        }

        Log.d(TAG, "FAT tables initialized successfully")
    }

    private fun initializeRootDirectory(blockDevice: BlockDeviceDriver, params: Fat32Params, blockSize: Int) {
        val dataStartSector = params.partitionStartSector +
                params.reservedSectors +
                (params.fatSize * 2)

        val rootDirSector = dataStartSector + ((params.rootCluster - 2) * params.sectorsPerCluster)

        // Create root directory with volume label
        val rootDir = ByteBuffer.allocate(blockSize)
        rootDir.order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until blockSize) {
            rootDir.put(0.toByte())
        }
        rootDir.rewind()

        // Volume label entry
        val label = "PEARBOOT   "
        for (i in 0 until 11) {
            rootDir.put(label[i].code.toByte())
        }
        rootDir.put(0x08.toByte())  // Attribute: Volume Label

        // Write root directory
        rootDir.rewind()
        blockDevice.write(rootDirSector, rootDir)

        // Clear remaining sectors in root directory cluster
        val zeroSector = ByteBuffer.allocate(blockSize)
        for (i in 0 until blockSize) {
            zeroSector.put(0.toByte())
        }

        for (i in 1 until params.sectorsPerCluster) {
            zeroSector.rewind()
            blockDevice.write(rootDirSector + i, zeroSector)
        }

        Log.d(TAG, "Root directory initialized successfully")
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

        closeCurrentDevice()
        cachedBlockSize = 512
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