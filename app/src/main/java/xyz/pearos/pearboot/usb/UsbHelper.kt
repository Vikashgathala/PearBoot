package xyz.pearos.pearboot.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.jahnen.libaums.UsbMassStorageDevice

object UsbHelper {

    const val ACTION_USB_PERMISSION = "xyz.pearos.USB_PERMISSION"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _size = MutableStateFlow("")
    val size: StateFlow<String> = _size

    fun scan(context: Context) {
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
            val massDevices = UsbMassStorageDevice.getMassStorageDevices(context)
            if (massDevices.isEmpty()) {
                notifyDetached()
                return@launch
            }

            try {
                val storage = massDevices[0]
                storage.init()

                delay(200)

                val partition = storage.partitions.firstOrNull()
                val fs = partition?.fileSystem

                var capacity = fs?.capacity ?: 0L

                if (capacity == 0L && fs != null) {
                    capacity = fs.freeSpace + fs.occupiedSpace
                }

                _connected.value = true
                _name.value = device.productName ?: "USB Drive"
                _size.value = formatSize(capacity)

            } catch (e: Exception) {
                notifyDetached()
            }
        }
    }

    private fun requestPermission(context: Context, device: UsbDevice) {
        val manager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val pi = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE
        )
        manager.requestPermission(device, pi)
    }

    fun notifyDetached() {
        _connected.value = false
        _name.value = ""
        _size.value = ""
    }

    private fun formatSize(bytes: Long): String {
        val gb = bytes / (1024.0 * 1024 * 1024)
        return "%.2f GB".format(gb)
    }
}
