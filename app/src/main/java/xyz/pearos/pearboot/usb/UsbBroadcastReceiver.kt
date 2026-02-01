package xyz.pearos.pearboot.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                UsbHelper.scan(context)
            }

            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                UsbHelper.notifyDetached()
            }

            UsbHelper.ACTION_USB_PERMISSION -> {
                UsbHelper.scan(context)
            }
        }
    }
}
