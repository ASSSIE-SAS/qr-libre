package co.asssie.qrlibre.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.pairing.CaptureMessage
import co.asssie.qrlibre.pairing.encodeBase64Url
import co.asssie.qrlibre.pairing.peerOnFirstConnection
import co.asssie.qrlibre.pairing.shouldAdvertise
import co.asssie.qrlibre.pairing.shouldSend
import co.asssie.qrlibre.pairing.signedFrame
import java.util.UUID

/**
 * The emitter side of the Bluetooth link: a GATT server that announces the
 * service named in the pairing QR and pushes signed capture frames to a
 * subscribed speaker.
 *
 * The class fails closed. Missing runtime permission, Bluetooth off, no
 * adapter, no local payload or a failed GATT call all end in a debug log line
 * and a quiet return; no exception escapes and no capture text is logged.
 *
 * The instance is process-wide because the notification listener and the
 * pairing screen both need to reach it. [start] may be called repeatedly: it
 * reconciles the server and the advertisement with the persisted role.
 *
 * Frames longer than the negotiated MTU need the receiver to request a larger
 * ATT MTU before subscribing; the server only notifies.
 */
class CaptureBroadcaster private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = PairingStore(appContext)
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val lock = Any()
    private var server: BluetoothGattServer? = null
    private var characteristic: BluetoothGattCharacteristic? = null
    private var advertisedUuid: String? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var advertising = false
    private val connectedDevices = mutableSetOf<BluetoothDevice>()
    private val subscribers = mutableSetOf<BluetoothDevice>()

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, ERROR_STATE)) {
                BluetoothAdapter.STATE_ON ->
                    synchronized(lock) { startLocked() }

                BluetoothAdapter.STATE_OFF,
                BluetoothAdapter.STATE_TURNING_OFF,
                -> synchronized(lock) { teardownLocked() }
            }
        }
    }

    init {
        registerAdapterReceiver()
    }

    /** Starts or reconciles the emitter side with the persisted role and peer. */
    fun start() {
        synchronized(lock) { startLocked() }
    }

    /**
     * Sends [message] to every subscribed speaker when this phone is a paired,
     * connected capture device. Returns without sending otherwise.
     */
    fun onCaptured(message: CaptureMessage) {
        synchronized(lock) {
            try {
                val role = store.role()
                val payload = store.localPayload()
                if (role != DeviceRole.CAPTURE || payload == null) {
                    debug("send skipped: not a configured capture phone")
                    teardownLocked()
                    return
                }
                if (!hasBluetoothSendPermissions(appContext)) {
                    debug("send skipped: Bluetooth permission not granted")
                    return
                }

                // A fresh install, a role change or a rotated payload leaves
                // the running server stale until the next capture.
                ensureServerLocked(payload.serviceUuid)
                if (!advertising && connectedDevices.isEmpty()) {
                    startAdvertisingLocked(payload.serviceUuid)
                }

                val peer = store.peer()
                if (!shouldSend(role, peer, connectedDevices.isNotEmpty())) {
                    debug("send skipped: unpaired or no connection")
                    return
                }

                val server = server ?: return
                val characteristic = characteristic ?: return
                val frame = signedFrame(message, payload.secret).toByteArray(Charsets.UTF_8)

                // Only subscribed centrals get a notification. A stale device
                // that never wrote the CCCD would just drop it.
                var sent = 0
                for (device in subscribers.toList()) {
                    if (notify(server, characteristic, device, frame)) sent++
                }
                debug("frame sent to $sent subscriber(s)")
            } catch (error: Exception) {
                // A Bluetooth problem must never propagate into the capture path.
                debug("send failed: ${error.javaClass.simpleName}")
            }
        }
    }

    /** Stops advertising and releases the GATT server. */
    fun close() {
        synchronized(lock) { teardownLocked() }
        try {
            appContext.unregisterReceiver(adapterReceiver)
        } catch (_: IllegalArgumentException) {
            // Never registered, or the process is already shutting down.
        }
    }

    private fun startLocked() {
        try {
            val role = store.role()
            val peer = store.peer()
            val payload = store.localPayload()
            if (!shouldAdvertise(role, peer) || payload == null) {
                debug("advertise skipped: not a configured capture phone")
                teardownLocked()
                return
            }
            if (!hasBluetoothSendPermissions(appContext)) {
                debug("advertise skipped: Bluetooth permission not granted")
                return
            }

            val adapter = manager?.adapter
            if (adapter == null) {
                debug("advertise skipped: no Bluetooth adapter")
                return
            }
            if (!adapter.isEnabled) {
                debug("advertise skipped: Bluetooth is off")
                return
            }

            ensureServerLocked(payload.serviceUuid)
            if (connectedDevices.isEmpty()) startAdvertisingLocked(payload.serviceUuid)
        } catch (error: Exception) {
            debug("start failed: ${error.javaClass.simpleName}")
        }
    }

    private fun ensureServerLocked(serviceUuid: String) {
        if (server != null && advertisedUuid == serviceUuid) return

        closeServerLocked()
        val manager = manager ?: return
        val uuid = UUID.fromString(serviceUuid)

        val callback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice?,
                status: Int,
                newState: Int,
            ) {
                synchronized(lock) {
                    when (newState) {
                        BluetoothGatt.STATE_CONNECTED -> {
                            if (device != null) connectedDevices += device
                            // One peer per emitter: stop looking once someone
                            // is attached, and reopen the door on disconnect.
                            stopAdvertisingLocked()
                            recordFirstPeerLocked(device)
                            debug("peer connected")
                        }

                        BluetoothGatt.STATE_DISCONNECTED -> {
                            if (device != null) {
                                connectedDevices -= device
                                subscribers -= device
                            }
                            debug("peer disconnected")
                            // Re-read the role and permissions: the phone may
                            // have been unpaired, switched to speaker or lost
                            // permission while this link was up.
                            startLocked()
                        }
                    }
                }
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    debug("connection state status $status")
                }
            }

            override fun onDescriptorWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                descriptor: BluetoothGattDescriptor?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                val enabled = value != null &&
                    (
                        value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                            value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                        )
                if (descriptor?.uuid == CLIENT_CONFIG_UUID && device != null) {
                    synchronized(lock) {
                        if (enabled) subscribers += device else subscribers -= device
                    }
                    @Suppress("DEPRECATION")
                    descriptor.value = if (enabled) {
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    } else {
                        BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                    }
                    debug("subscription ${if (enabled) "on" else "off"}")
                }
                if (responseNeeded) {
                    server?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        null,
                    )
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                // The characteristic only carries what the emitter pushes up;
                // acks keep the peer's stack happy without reading its writes.
                if (characteristic?.uuid != CAPTURE_CHARACTERISTIC_UUID) {
                    if (responseNeeded) {
                        server?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                            offset,
                            null,
                        )
                    }
                    return
                }
                if (responseNeeded) {
                    server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
                }
            }
        }

        val opened = manager.openGattServer(appContext, callback) ?: run {
            debug("GATT server not available")
            return
        }

        val service = BluetoothGattService(uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val captureCharacteristic = BluetoothGattCharacteristic(
            CAPTURE_CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )
        captureCharacteristic.addDescriptor(
            BluetoothGattDescriptor(
                CLIENT_CONFIG_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or
                    BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(captureCharacteristic)

        if (!opened.addService(service)) {
            debug("GATT service rejected")
            opened.close()
            return
        }

        server = opened
        characteristic = captureCharacteristic
        advertisedUuid = serviceUuid
        debug("GATT service ready")
    }

    /**
     * Records the first speaker that connects, so the pairing screen can name
     * it and "Desemparejar" has something to forget.
     *
     * The scanner already holds the pairing secret and every frame is signed
     * with it, so reaching the service is the authorization; this only keeps
     * the persisted peer in step with the live link. An existing peer is never
     * replaced, and neither the remote name nor the secret reaches the log.
     */
    private fun recordFirstPeerLocked(device: BluetoothDevice?) {
        try {
            if (device == null) return
            if (store.role() != DeviceRole.CAPTURE) return
            val payload = store.localPayload() ?: return
            val peer = peerOnFirstConnection(
                existing = store.peer(),
                remoteName = device.name,
                localSecretBase64Url = encodeBase64Url(payload.secret),
                serviceUuid = payload.serviceUuid,
            ) ?: return
            store.savePeer(peer)
            debug("first peer recorded")
        } catch (error: Exception) {
            // A missing permission or an unreadable name must not break the link.
            debug("peer record failed: ${error.javaClass.simpleName}")
        }
    }

    private fun startAdvertisingLocked(serviceUuid: String) {
        if (advertising) return

        val adapter = manager?.adapter ?: return
        if (!adapter.isEnabled) return
        val advertiser = adapter.bluetoothLeAdvertiser ?: run {
            debug("advertise skipped: peripheral mode not supported")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid(UUID.fromString(serviceUuid)))
            .setIncludeTxPowerLevel(false)
            .build()
        // The name rides in the scan response so the 31-byte advertising
        // packet keeps room for the 128-bit service UUID.
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                synchronized(lock) { advertising = true }
                debug("advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                synchronized(lock) { advertising = false }
                debug("advertising failed: $errorCode")
            }
        }
        try {
            advertiser.startAdvertising(settings, data, scanResponse, callback)
            // Treat the request as live until the callback says otherwise, so a
            // second start in the same capture does not double-advertise.
            advertising = true
            this.advertiser = advertiser
            this.advertiseCallback = callback
        } catch (error: Exception) {
            advertising = false
            debug("advertising failed: ${error.javaClass.simpleName}")
        }
    }

    private fun stopAdvertisingLocked() {
        val advertiser = advertiser
        val callback = advertiseCallback
        if (advertiser != null && callback != null && advertising) {
            try {
                advertiser.stopAdvertising(callback)
            } catch (_: Exception) {
                // The adapter may already be off; state is what matters.
            }
        }
        advertising = false
        advertiseCallback = null
    }

    private fun teardownLocked() {
        stopAdvertisingLocked()
        closeServerLocked()
    }

    private fun closeServerLocked() {
        connectedDevices.clear()
        subscribers.clear()
        try {
            server?.close()
        } catch (_: Exception) {
            // Closing an already-dead server is harmless.
        }
        server = null
        characteristic = null
        advertisedUuid = null
    }

    private fun notify(
        server: BluetoothGattServer,
        characteristic: BluetoothGattCharacteristic,
        device: BluetoothDevice,
        value: ByteArray,
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(
                device,
                characteristic,
                false,
                value,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    } catch (_: Exception) {
        false
    }

    private fun registerAdapterReceiver() {
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(adapterReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                appContext.registerReceiver(adapterReceiver, filter)
            }
        } catch (_: Exception) {
            debug("adapter receiver not registered")
        }
    }

    private fun debug(message: String) {
        // Status only: capture text and secrets never reach the log.
        Log.d(TAG, message)
    }

    companion object {
        /** The characteristic the speaker discovers and subscribes to. */
        val CAPTURE_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("8f0a2c1e-4b3d-4a6f-9e21-5c7d8b9a0f13")

        /** Standard Client Characteristic Configuration descriptor. */
        val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val TAG = "QrLibreBluetooth"
        private const val ERROR_STATE = Int.MIN_VALUE

        @Volatile
        private var instance: CaptureBroadcaster? = null

        fun get(context: Context): CaptureBroadcaster =
            instance ?: synchronized(this) {
                instance ?: CaptureBroadcaster(context.applicationContext).also { instance = it }
            }
    }
}
