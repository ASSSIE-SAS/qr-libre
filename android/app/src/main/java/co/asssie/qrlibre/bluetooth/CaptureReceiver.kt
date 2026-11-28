package co.asssie.qrlibre.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.pairing.CaptureMessage
import co.asssie.qrlibre.pairing.SpeakerConnection
import co.asssie.qrlibre.pairing.SpeakerEvent
import co.asssie.qrlibre.pairing.SpeakerUiState
import co.asssie.qrlibre.pairing.decodeCaptureMessage
import co.asssie.qrlibre.pairing.reduce
import co.asssie.qrlibre.pairing.verifyFrame
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The speaker side of the Bluetooth link: scans for the service named in the
 * scanned pairing QR, connects as a GATT client, and keeps the last verified
 * capture in a [StateFlow].
 *
 * Every frame goes through [verifyFrame] with the secret from the QR. A frame
 * that fails, or a payload that does not parse, is counted and dropped; it is
 * never exposed in [state] and never reaches the voice. The class fails closed
 * on a missing permission, Bluetooth off, a missing adapter or a failed GATT
 * call, without throwing into the caller.
 *
 * This is an **in-app** receiver. It is started by `MainActivity.onStart` and
 * stopped by `onStop`; nothing runs as a background service, so the phone only
 * sounds while QR Libre is open. The pairing screen says so.
 *
 * The instance is process-wide so the pairing screen and the activity share
 * one link.
 */
class CaptureReceiver private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val store = PairingStore(appContext)
    private val manager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()

    private val _state = MutableStateFlow(SpeakerUiState())

    /** The connection state and the last message a valid frame carried. */
    val state: StateFlow<SpeakerUiState> = _state.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanning = false

    @Volatile
    private var activeSecret: ByteArray? = null

    /** The peer the current link (or scan) belongs to, so a re-pair restarts it. */
    private var linkedPeer: PairedPeer? = null
    private var reconnectAttempt = 0
    private var reconnectScheduled = false
    private var reconnectRunnable: Runnable? = null

    /**
     * True between [start] and [stop]. The adapter receiver checks it so a
     * Bluetooth toggle while the app is closed cannot silently restart the
     * scanner in the background.
     */
    private var active = false

    private val adapterReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, ERROR_STATE)) {
                BluetoothAdapter.STATE_ON -> synchronized(lock) {
                    if (!active) return@synchronized
                    reconnectAttempt = 0
                    startLocked()
                }

                BluetoothAdapter.STATE_OFF,
                BluetoothAdapter.STATE_TURNING_OFF,
                -> synchronized(lock) {
                    stopLinkLocked()
                    updateConnection(SpeakerEvent.Lost)
                }
            }
        }
    }

    init {
        registerAdapterReceiver()
    }

    /**
     * Reconciles the receiver with the persisted role and peer, scanning when
     * this phone is a paired speaker.
     */
    fun start() {
        synchronized(lock) {
            active = true
            startLocked()
        }
    }

    /**
     * Re-reads the store after a role change, a scan or an unpair. It never
     * makes the receiver active on its own: only [start] does, so a screen
     * recomposition while the app is in the background cannot start a scan.
     */
    fun refresh() {
        synchronized(lock) { startLocked() }
    }

    /**
     * Disconnects and returns to [SpeakerConnection.IDLE] without forgetting
     * the last message. Called when the app leaves the foreground.
     */
    fun stop() {
        synchronized(lock) {
            active = false
            stopLocked(clearSession = false)
        }
    }

    /** Drops the last shown message, e.g. after pairing with another phone. */
    fun forgetLastMessage() {
        synchronized(lock) {
            _state.update { it.copy(lastMessage = null, droppedFrames = 0) }
        }
    }

    private fun startLocked() {
        if (!active) return
        try {
            val role = store.role()
            val peer = store.peer()
            val secret = peer?.secretBase64Url?.let(::decodeSecret)
            if (role != DeviceRole.SPEAKER || peer == null || secret == null) {
                stopLocked(clearSession = true)
                return
            }

            // Scanning another QR replaces the peer. Drop the old link instead
            // of keeping it alive behind the newly stored code.
            if (peer != linkedPeer) {
                stopLinkLocked()
                activeSecret = null
                linkedPeer = peer
            }

            // A live link or a pending retry already reflects the store; a
            // fresh start would only restart the scan it is waiting on.
            if (gatt != null || scanning || reconnectScheduled) return

            if (!hasBluetoothReceivePermissions(appContext)) {
                stopLinkLocked()
                setConnection(SpeakerConnection.IDLE)
                return
            }

            val adapter = manager?.adapter
            if (adapter == null || !adapter.isEnabled) {
                stopLinkLocked()
                setConnection(SpeakerConnection.DISCONNECTED)
                return
            }

            activeSecret = secret
            beginScanLocked(adapter, peer.serviceUuid)
        } catch (error: Exception) {
            debug("start failed: ${error.javaClass.simpleName}")
        }
    }

    private fun beginScanLocked(adapter: BluetoothAdapter, serviceUuid: String) {
        val uuid = try {
            UUID.fromString(serviceUuid)
        } catch (_: IllegalArgumentException) {
            setConnection(SpeakerConnection.DISCONNECTED)
            return
        }

        val scanner = try {
            adapter.bluetoothLeScanner
        } catch (_: Exception) {
            null
        }
        if (scanner == null) {
            setConnection(SpeakerConnection.DISCONNECTED)
            scheduleReconnectLocked()
            return
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(uuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                val device = result?.device ?: return
                handler.post {
                    synchronized(lock) {
                        if (this@CaptureReceiver.scanCallback !== this) return@synchronized
                        stopScanLocked()
                        connectLocked(device)
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                handler.post {
                    synchronized(lock) {
                        if (this@CaptureReceiver.scanCallback !== this) return@synchronized
                        stopScanLocked()
                        setConnection(SpeakerConnection.DISCONNECTED)
                        scheduleReconnectLocked()
                    }
                }
            }
        }

        try {
            scanner.startScan(listOf(filter), settings, callback)
            this.scanner = scanner
            this.scanCallback = callback
            scanning = true
            updateConnection(SpeakerEvent.Searching)
        } catch (error: Exception) {
            debug("scan failed: ${error.javaClass.simpleName}")
            setConnection(SpeakerConnection.DISCONNECTED)
            scheduleReconnectLocked()
        }
    }

    private fun connectLocked(device: BluetoothDevice) {
        if (gatt != null) return
        try {
            gatt = device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
            if (gatt == null) {
                setConnection(SpeakerConnection.DISCONNECTED)
                scheduleReconnectLocked()
            } else {
                updateConnection(SpeakerEvent.Searching)
            }
        } catch (error: Exception) {
            gatt = null
            debug("connect failed: ${error.javaClass.simpleName}")
            setConnection(SpeakerConnection.DISCONNECTED)
            scheduleReconnectLocked()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int,
        ) {
            synchronized(lock) {
                if (gatt !== this@CaptureReceiver.gatt) return@synchronized
                if (status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
                ) {
                    // Ask for a large MTU before discovery so a full signed
                    // frame fits in a single notification.
                    val requested = try {
                        gatt.requestMtu(MTU)
                    } catch (_: Exception) {
                        false
                    }
                    if (!requested) discoverLocked(gatt)
                    return@synchronized
                }
                if (newState == BluetoothProfile.STATE_DISCONNECTED ||
                    status != BluetoothGatt.GATT_SUCCESS
                ) {
                    dropLinkLocked()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            synchronized(lock) {
                if (gatt !== this@CaptureReceiver.gatt) return@synchronized
                // Even a refused MTU request can discover: the server only
                // notifies, and a shorter frame still fits the default MTU.
                discoverLocked(gatt)
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            synchronized(lock) {
                if (gatt !== this@CaptureReceiver.gatt) return@synchronized
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    dropLinkLocked()
                    return@synchronized
                }

                val peer = store.peer()
                if (peer == null || activeSecret == null) {
                    dropLinkLocked()
                    return@synchronized
                }

                val service = try {
                    gatt.getService(UUID.fromString(peer.serviceUuid))
                } catch (_: Exception) {
                    null
                }
                val target = service?.getCharacteristic(CAPTURE_CHARACTERISTIC_UUID)
                if (target == null) {
                    dropLinkLocked()
                    return@synchronized
                }

                val local = try {
                    gatt.setCharacteristicNotification(target, true)
                } catch (_: Exception) {
                    false
                }
                val cccd = target.getDescriptor(CLIENT_CONFIG_UUID)
                if (!local || cccd == null || !writeNotification(gatt, cccd)) {
                    dropLinkLocked()
                    return@synchronized
                }

                reconnectAttempt = 0
                updateConnection(SpeakerEvent.Linked)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (descriptor.uuid != CLIENT_CONFIG_UUID || status == BluetoothGatt.GATT_SUCCESS) {
                return
            }
            synchronized(lock) {
                if (gatt === this@CaptureReceiver.gatt) dropLinkLocked()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleFrame(gatt, characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: ByteArray(0)
            handleFrame(gatt, characteristic, value)
        }
    }

    private fun discoverLocked(gatt: BluetoothGatt) {
        try {
            if (!gatt.discoverServices()) dropLinkLocked()
        } catch (error: Exception) {
            debug("discovery failed: ${error.javaClass.simpleName}")
            dropLinkLocked()
        }
    }

    private fun writeNotification(
        gatt: BluetoothGatt,
        cccd: BluetoothGattDescriptor,
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ==
                BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(cccd)
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Verifies and publishes one frame. Anything unverified is counted and
     * dropped, so neither the screen nor the voice can show an unauthenticated
     * notice.
     */
    private fun handleFrame(
        source: BluetoothGatt,
        changed: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        if (changed.uuid != CAPTURE_CHARACTERISTIC_UUID) return

        synchronized(lock) {
            if (source !== gatt) return
        }

        val secret = activeSecret ?: return
        val message: CaptureMessage? = try {
            val frame = String(value, Charsets.UTF_8)
            verifyFrame(frame, secret)?.let(::decodeCaptureMessage)
        } catch (_: Exception) {
            null
        }

        synchronized(lock) {
            if (source !== gatt) return
            if (message == null) {
                _state.update { it.copy(droppedFrames = it.droppedFrames + 1) }
                return
            }
            _state.update {
                it.copy(
                    connection = SpeakerConnection.CONNECTED,
                    lastMessage = message,
                )
            }
        }
    }

    private fun dropLinkLocked() {
        closeGattLocked()
        setConnection(SpeakerConnection.DISCONNECTED)
        scheduleReconnectLocked()
    }

    private fun scheduleReconnectLocked() {
        if (!active || reconnectScheduled) return
        if (store.role() != DeviceRole.SPEAKER || store.peer() == null) return
        if (!hasBluetoothReceivePermissions(appContext)) return

        val delay = reconnectDelayMs(reconnectAttempt)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(MAX_BACKOFF_STEP)
        reconnectScheduled = true
        val runnable = Runnable {
            synchronized(lock) {
                reconnectScheduled = false
                reconnectRunnable = null
                startLocked()
            }
        }
        reconnectRunnable = runnable
        handler.postDelayed(runnable, delay)
        debug("reconnect in ${delay}ms")
    }

    private fun cancelReconnectLocked() {
        reconnectRunnable?.let(handler::removeCallbacks)
        reconnectRunnable = null
        reconnectScheduled = false
    }

    private fun stopScanLocked() {
        val scanner = scanner
        val callback = scanCallback
        this.scanner = null
        this.scanCallback = null
        scanning = false
        if (scanner != null && callback != null) {
            try {
                scanner.stopScan(callback)
            } catch (_: Exception) {
                // The adapter may already be off; state is what matters.
            }
        }
    }

    private fun closeGattLocked() {
        val open = gatt
        gatt = null
        try {
            open?.disconnect()
            open?.close()
        } catch (_: Exception) {
            // Closing an already-dead link is harmless.
        }
    }

    private fun stopLinkLocked() {
        cancelReconnectLocked()
        stopScanLocked()
        closeGattLocked()
    }

    private fun stopLocked(clearSession: Boolean) {
        stopLinkLocked()
        activeSecret = null
        linkedPeer = null
        reconnectAttempt = 0
        _state.update { current ->
            current.copy(
                connection = current.connection.reduce(SpeakerEvent.Stopped),
                lastMessage = if (clearSession) null else current.lastMessage,
                droppedFrames = if (clearSession) 0 else current.droppedFrames,
            )
        }
    }

    private fun setConnection(connection: SpeakerConnection) {
        _state.update { it.copy(connection = connection) }
    }

    private fun updateConnection(event: SpeakerEvent) {
        _state.update { it.copy(connection = it.connection.reduce(event)) }
    }

    private fun registerAdapterReceiver() {
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    adapterReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED,
                )
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
        /** The characteristic the capture phone pushes notifications on. */
        val CAPTURE_CHARACTERISTIC_UUID: UUID =
            CaptureBroadcaster.CAPTURE_CHARACTERISTIC_UUID

        /** Standard Client Characteristic Configuration descriptor. */
        val CLIENT_CONFIG_UUID: UUID = CaptureBroadcaster.CLIENT_CONFIG_UUID

        /** A large ATT MTU keeps a full signed frame inside one notification. */
        private const val MTU = 517

        private const val MAX_BACKOFF_STEP = 4

        private const val TAG = "QrLibreSpeaker"
        private const val ERROR_STATE = Int.MIN_VALUE

        @Volatile
        private var instance: CaptureReceiver? = null

        fun get(context: Context): CaptureReceiver =
            instance ?: synchronized(this) {
                instance ?: CaptureReceiver(context.applicationContext).also { instance = it }
            }
    }
}

private fun decodeSecret(text: String): ByteArray? =
    try {
        Base64.getUrlDecoder().decode(text)
    } catch (_: IllegalArgumentException) {
        null
    }

/** 2s, 4s, 8s, 16s, then 30s between attempts. */
internal fun reconnectDelayMs(attempt: Int): Long {
    val exponent = attempt.coerceIn(0, 4)
    return minOf(30_000L, 2_000L * (1L shl exponent))
}
