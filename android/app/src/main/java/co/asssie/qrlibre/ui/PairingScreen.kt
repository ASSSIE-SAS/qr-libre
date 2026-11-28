package co.asssie.qrlibre.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.R
import co.asssie.qrlibre.bluetooth.CaptureBroadcaster
import co.asssie.qrlibre.bluetooth.CaptureReceiver
import co.asssie.qrlibre.bluetooth.bluetoothReceivePermissions
import co.asssie.qrlibre.bluetooth.bluetoothSendPermissions
import co.asssie.qrlibre.bluetooth.hasBluetoothReceivePermissions
import co.asssie.qrlibre.bluetooth.hasBluetoothSendPermissions
import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairedPeer
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.pairing.CaptureMessage
import co.asssie.qrlibre.pairing.PairingPayload
import co.asssie.qrlibre.pairing.PairingStatus
import co.asssie.qrlibre.pairing.PairingSummary
import co.asssie.qrlibre.pairing.SpeakerConnection
import co.asssie.qrlibre.pairing.SpeakerUiState
import co.asssie.qrlibre.pairing.decodePairingPayload
import co.asssie.qrlibre.pairing.encode
import co.asssie.qrlibre.pairing.encodeBase64Url
import co.asssie.qrlibre.pairing.pairingQrMatrix
import co.asssie.qrlibre.pairing.pairingSummary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The QR is rasterised once per payload; 1024px keeps the modules sharp. */
private const val QR_PIXELS = 1024

@Composable
internal fun PairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PairingStore(context) }
    var role by remember { mutableStateOf(store.role()) }
    var peer by remember { mutableStateOf(store.peer()) }
    val summary = pairingSummary(role, peer)
    // Android 12+ asks for Bluetooth at runtime. Capture still works without
    // it; only the forward to the speaker stays off.
    var bluetoothGranted by remember { mutableStateOf(hasBluetoothSendPermissions(context)) }
    var receiveGranted by remember { mutableStateOf(hasBluetoothReceivePermissions(context)) }
    val bluetoothPermissions = bluetoothSendPermissions
    val receivePermissions = bluetoothReceivePermissions
    val requestBluetooth = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        bluetoothGranted = hasBluetoothSendPermissions(context)
        reconcileBluetooth(context)
    }
    val requestReceiveBluetooth = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        receiveGranted = hasBluetoothReceivePermissions(context)
        CaptureReceiver.get(context).refresh()
    }

    // The scanner activity owns the camera permission prompt. The result is
    // checked against the pairing codec; an unknown code saves nothing.
    var scanError by remember { mutableStateOf(false) }
    val scanOptions = remember(context) {
        ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt(context.getString(R.string.pairing_scan))
            .setBeepEnabled(false)
            .setOrientationLocked(false)
    }
    val scan = rememberLauncherForActivityResult(ScanContract()) { result ->
        val contents = result.contents
        if (contents != null) {
            val decoded = decodePairingPayload(contents)
            if (decoded == null) {
                scanError = true
            } else {
                val receiver = CaptureReceiver.get(context)
                store.savePeer(
                    PairedPeer(
                        name = decoded.deviceName,
                        secretBase64Url = encodeBase64Url(decoded.secret),
                        serviceUuid = decoded.serviceUuid,
                    ),
                )
                peer = store.peer()
                scanError = false
                receiver.forgetLastMessage()
                receiver.refresh()
            }
        }
    }

    val receiverState by CaptureReceiver.get(context).state.collectAsStateWithLifecycle()
    LaunchedEffect(role, bluetoothGranted, receiveGranted) {
        reconcileBluetooth(context)
        CaptureReceiver.get(context).refresh()
    }

    // Minted once per capture role and persisted by the store, so a rotation,
    // a recomposition or a role round-trip reuses the same secret. The speaker
    // role never mints one.
    val payload = remember(role) {
        if (role == DeviceRole.CAPTURE) {
            store.localPayloadOrCreate(Build.MODEL)
        } else {
            null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(
            onBack = onBack,
            title = stringResource(R.string.pairing_title),
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.pairing_role_question),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            RoleOption(
                title = stringResource(R.string.pairing_role_capture_title),
                body = stringResource(R.string.pairing_role_capture_body),
                selected = role == DeviceRole.CAPTURE,
                onSelect = {
                    if (role != DeviceRole.CAPTURE) {
                        role = DeviceRole.CAPTURE
                        store.setRole(DeviceRole.CAPTURE)
                        // A role change forgets a peer made for the other direction.
                        peer = store.peer()
                        reconcileBluetooth(context)
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            RoleOption(
                title = stringResource(R.string.pairing_role_speaker_title),
                body = stringResource(R.string.pairing_role_speaker_body),
                selected = role == DeviceRole.SPEAKER,
                onSelect = {
                    if (role != DeviceRole.SPEAKER) {
                        role = DeviceRole.SPEAKER
                        store.setRole(DeviceRole.SPEAKER)
                        peer = store.peer()
                        reconcileBluetooth(context)
                    }
                },
            )
            Text(
                text = stringResource(R.string.pairing_role_change_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(20.dp))
            val onUnpair = {
                store.clearPeer()
                peer = null
                CaptureReceiver.get(context).refresh()
            }
            when (role) {
                DeviceRole.CAPTURE -> CaptureSection(
                    payload = payload,
                    summary = summary,
                    onUnpair = onUnpair,
                    bluetoothNeedsPermission =
                        bluetoothPermissions.isNotEmpty() && !bluetoothGranted,
                    onRequestBluetooth = {
                        requestBluetooth.launch(bluetoothPermissions.toTypedArray())
                    },
                )

                DeviceRole.SPEAKER -> SpeakerSection(
                    summary = summary,
                    receiverState = receiverState,
                    scanError = scanError,
                    bluetoothNeedsPermission =
                        receivePermissions.isNotEmpty() && !receiveGranted,
                    onRequestBluetooth = {
                        requestReceiveBluetooth.launch(receivePermissions.toTypedArray())
                    },
                    onScan = {
                        scanError = false
                        try {
                            scan.launch(scanOptions)
                        } catch (_: Exception) {
                            // No scanner activity to handle the request.
                            scanError = true
                        }
                    },
                    onUnpair = onUnpair,
                )
            }
        }
    }
}

@Composable
private fun RoleOption(
    title: String,
    body: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect,
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The whole row is the control; the radio only paints the state so
            // TalkBack announces one selectable item, not a nested button.
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = MaterialTheme.colorScheme.primary,
                    unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CaptureSection(
    payload: PairingPayload?,
    summary: PairingSummary,
    onUnpair: () -> Unit,
    bluetoothNeedsPermission: Boolean,
    onRequestBluetooth: () -> Unit,
) {
    PairingStatusLine(summary)
    if (summary.canUnpair) {
        Spacer(Modifier.height(10.dp))
        UnpairButton(onUnpair)
    }
    if (bluetoothNeedsPermission) {
        Spacer(Modifier.height(12.dp))
        BluetoothPermissionCard(
            bodyText = stringResource(R.string.pairing_bluetooth_permission_body),
            onRequestBluetooth = onRequestBluetooth,
        )
    }
    if (payload == null) return

    val qr = remember(payload) { pairingQrImage(payload.encode()) }
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            // 512dp is the cap; the padding inside the light card is the quiet
            // zone that keeps the code scannable on a dark screen.
            .widthIn(max = 512.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.large)
            .background(QrLibreColors.Text)
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = qr,
            contentDescription = stringResource(R.string.pairing_qr_label),
            modifier = Modifier.fillMaxSize(),
        )
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = stringResource(R.string.pairing_qr_name, payload.deviceName),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(R.string.pairing_qr_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SpeakerSection(
    summary: PairingSummary,
    receiverState: SpeakerUiState,
    scanError: Boolean,
    bluetoothNeedsPermission: Boolean,
    onRequestBluetooth: () -> Unit,
    onScan: () -> Unit,
    onUnpair: () -> Unit,
) {
    PairingStatusLine(summary)
    if (summary.canUnpair) {
        Spacer(Modifier.height(10.dp))
        UnpairButton(onUnpair)
    }
    if (bluetoothNeedsPermission) {
        Spacer(Modifier.height(12.dp))
        BluetoothPermissionCard(
            bodyText = stringResource(R.string.pairing_speaker_permission_body),
            onRequestBluetooth = onRequestBluetooth,
        )
    }
    Spacer(Modifier.height(12.dp))
    SpeakerLinkLine(receiverState.connection)
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = onScan,
        enabled = summary.scanEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.pairing_scan),
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Text(
        text = stringResource(R.string.pairing_scan_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    if (scanError) {
        Text(
            text = stringResource(R.string.pairing_scan_invalid),
            style = MaterialTheme.typography.bodySmall,
            color = QrLibreColors.DangerText,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        Text(
            text = stringResource(R.string.pairing_speaker_android_11),
            style = MaterialTheme.typography.bodySmall,
            color = QrLibreColors.DangerText,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    if (summary.paired) {
        Spacer(Modifier.height(16.dp))
        SpeakerLastMessage(receiverState)
        if (receiverState.droppedFrames > 0) {
            Text(
                text = stringResource(
                    R.string.pairing_speaker_dropped,
                    receiverState.droppedFrames,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
    Text(
        text = stringResource(R.string.pairing_speaker_open_notice),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp),
    )
}

/** One line for the live link, with the colour the rest of the app uses. */
@Composable
private fun SpeakerLinkLine(connection: SpeakerConnection) {
    val text = when (connection) {
        SpeakerConnection.IDLE -> stringResource(R.string.pairing_speaker_link_idle)
        SpeakerConnection.CONNECTING -> stringResource(R.string.pairing_speaker_link_connecting)
        SpeakerConnection.CONNECTED -> stringResource(R.string.pairing_speaker_link_connected)
        SpeakerConnection.DISCONNECTED -> stringResource(R.string.pairing_speaker_link_disconnected)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = when (connection) {
            SpeakerConnection.CONNECTED -> MaterialTheme.colorScheme.primary
            SpeakerConnection.DISCONNECTED -> QrLibreColors.DangerText
            SpeakerConnection.IDLE,
            SpeakerConnection.CONNECTING,
            -> MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

/**
 * The last frame that passed signature verification. An unverified frame never
 * reaches this card; the dropped counter below it is the only trace it leaves.
 */
@Composable
private fun SpeakerLastMessage(receiverState: SpeakerUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_speaker_last_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val message = receiverState.lastMessage
            if (message == null) {
                Text(
                    text = stringResource(R.string.pairing_speaker_no_message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                SpeakerMessageBody(message)
            }
        }
    }
}

@Composable
private fun SpeakerMessageBody(message: CaptureMessage) {
    Text(
        text = stringResource(
            R.string.pairing_speaker_last_meta,
            message.app,
            speakerTime(message.epochMs),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (message.title.isNotBlank()) {
        Text(
            text = message.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (message.body.isNotBlank()) {
        Text(
            text = message.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun speakerTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))

@Composable
private fun PairingStatusLine(summary: PairingSummary) {
    Text(
        text = pairingStatusText(summary),
        style = MaterialTheme.typography.titleSmall,
        color = if (summary.paired) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}

@Composable
private fun UnpairButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = QrLibreColors.DangerText),
    ) {
        Text(
            text = stringResource(R.string.pairing_unpair),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * Plain explanation plus the system request. The permission is not needed to
 * keep capturing; it only turns on the sender, so the copy says so.
 */
@Composable
private fun BluetoothPermissionCard(
    bodyText: String,
    onRequestBluetooth: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.pairing_bluetooth_permission_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onRequestBluetooth,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.pairing_bluetooth_permission_action),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

private fun reconcileBluetooth(context: Context) {
    try {
        CaptureBroadcaster.get(context).start()
    } catch (_: Exception) {
        // Bluetooth is optional: the capture path never depends on it.
    }
}

/** The screen-level status line; the settings row has its own shorter copy. */
@Composable
internal fun pairingStatusText(summary: PairingSummary): String = when (summary.status) {
    PairingStatus.CAPTURE_UNPAIRED -> stringResource(R.string.pairing_status_capture_unpaired)
    PairingStatus.CAPTURE_PAIRED ->
        stringResource(R.string.pairing_status_capture_paired, summary.peerName.orEmpty())

    PairingStatus.SPEAKER_UNPAIRED -> stringResource(R.string.pairing_status_speaker_unpaired)
    PairingStatus.SPEAKER_PAIRED ->
        stringResource(R.string.pairing_status_speaker_paired, summary.peerName.orEmpty())
}

/** One line for the Settings entry, independent of the full screen copy. */
@Composable
internal fun pairingSettingsDetail(summary: PairingSummary): String = when (summary.status) {
    PairingStatus.CAPTURE_UNPAIRED ->
        stringResource(R.string.pairing_settings_capture_unpaired)

    PairingStatus.CAPTURE_PAIRED ->
        stringResource(R.string.pairing_settings_capture_paired, summary.peerName.orEmpty())

    PairingStatus.SPEAKER_UNPAIRED ->
        stringResource(R.string.pairing_settings_speaker_unpaired)

    PairingStatus.SPEAKER_PAIRED ->
        stringResource(R.string.pairing_settings_speaker_paired, summary.peerName.orEmpty())
}

/**
 * Turns the QR matrix into a bitmap on the light card colour. Dark modules use
 * the page colour so the code keeps maximum contrast against `#F5F4F0`.
 */
private fun pairingQrImage(encodedPayload: String) =
    pairingQrMatrix(encodedPayload, QR_PIXELS).let { matrix ->
        val dark = QrLibreColorValues.Page.toInt()
        val light = QrLibreColorValues.Text.toInt()
        val pixels = IntArray(matrix.width * matrix.height)
        var index = 0
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[index++] = if (matrix.get(x, y)) dark else light
            }
        }
        Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
