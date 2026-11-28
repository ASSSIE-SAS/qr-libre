package co.asssie.qrlibre.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * The Bluetooth permissions the capture role needs at runtime on Android 12+.
 *
 * Below [Build.VERSION_CODES.S] the same capability is covered by the
 * install-time `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions, so nothing has to be
 * requested. The list is empty on those versions.
 */
val bluetoothSendPermissions: List<String>
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyList()
    }

/** True when every runtime permission needed to advertise and send is granted. */
fun hasBluetoothSendPermissions(context: Context): Boolean {
    val required = bluetoothSendPermissions
    if (required.isEmpty()) return true
    return required.all { permission ->
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
