package co.asssie.qrlibre.ui

import android.graphics.Bitmap
import android.os.Build
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
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.DeviceRole
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.pairing.PairingPayload
import co.asssie.qrlibre.pairing.PairingStatus
import co.asssie.qrlibre.pairing.PairingSummary
import co.asssie.qrlibre.pairing.encode
import co.asssie.qrlibre.pairing.pairingQrMatrix
import co.asssie.qrlibre.pairing.pairingSummary

/** The QR is rasterised once per payload; 1024px keeps the modules sharp. */
private const val QR_PIXELS = 1024

@Composable
internal fun PairingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PairingStore(context) }
    var role by remember { mutableStateOf(store.role()) }
    var peer by remember { mutableStateOf(store.peer()) }
    val summary = pairingSummary(role, peer)

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
            }
            when (role) {
                DeviceRole.CAPTURE -> CaptureSection(payload, summary, onUnpair)
                DeviceRole.SPEAKER -> SpeakerSection(summary, onUnpair)
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
) {
    PairingStatusLine(summary)
    if (summary.canUnpair) {
        Spacer(Modifier.height(10.dp))
        UnpairButton(onUnpair)
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
private fun SpeakerSection(summary: PairingSummary, onUnpair: () -> Unit) {
    PairingStatusLine(summary)
    if (summary.canUnpair) {
        Spacer(Modifier.height(10.dp))
        UnpairButton(onUnpair)
    }
    Spacer(Modifier.height(12.dp))
    // The scanner is not wired in this version. Keeping the button visible but
    // disabled shows the shape of the flow without claiming it works.
    Button(
        onClick = {},
        enabled = summary.scanEnabled,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.pairing_scan),
            style = MaterialTheme.typography.labelLarge,
        )
    }
    Text(
        text = stringResource(R.string.pairing_scan_next_version),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

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
