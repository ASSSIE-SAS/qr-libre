package co.asssie.qrlibre.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.Capture
import co.asssie.qrlibre.data.CaptureEvents
import co.asssie.qrlibre.data.PairingStore
import co.asssie.qrlibre.data.exportCaptures
import co.asssie.qrlibre.data.exportFileName
import co.asssie.qrlibre.pairing.pairingSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsScreen(
    selectedApps: Int,
    ttsReady: Boolean,
    ttsMessage: String?,
    onSpeak: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onBack: () -> Unit,
    loadCaptures: suspend () -> List<Capture>,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by CaptureEvents.version.collectAsStateWithLifecycle()
    // Read on entry, so returning from Emparejamiento shows the current role
    // and peer. Leaving Settings drops this state with the composition.
    val pairing = remember {
        val store = PairingStore(context)
        pairingSummary(store.role(), store.peer())
    }
    // Read on entry and whenever the table changes, so the action never offers
    // a stale list. Leaving Settings drops this state with the composition.
    var captures by remember { mutableStateOf<List<Capture>?>(null) }
    var exporting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<ExportFeedback?>(null) }
    // The rows the user confirmed in the picker, so the written file is exactly
    // the snapshot the button described even if the table changes while the
    // system dialog is open.
    var pendingExport by remember { mutableStateOf<List<Capture>?>(null) }

    LaunchedEffect(version) {
        captures = try {
            loadCaptures()
        } catch (_: Exception) {
            // A failed read is unknown, not an empty history. The action stays
            // disabled rather than claiming there is nothing to export.
            null
        }
    }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val rows = pendingExport
        pendingExport = null
        if (uri == null || rows == null) {
            // Cancelled in the system picker: no success and no error message.
            exporting = false
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    val document = exportCaptures(rows).toByteArray(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(document)
                        out.flush()
                    } != null
                } catch (_: Exception) {
                    false
                }
            }
            exporting = false
            feedback = if (written) ExportFeedback.DONE else ExportFeedback.FAILED
        }
    }

    val rows = captures

    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(
            onBack = onBack,
            title = stringResource(R.string.settings_title),
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        ) {
            SettingsRow(
                title = stringResource(R.string.apps_headline),
                detail = pluralStringResource(
                    R.plurals.apps_selected_summary,
                    selectedApps,
                    selectedApps,
                ),
                onClick = onOpenApps,
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.pairing_title),
                detail = pairingSettingsDetail(pairing),
                onClick = onOpenPairing,
            )
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.voice_test),
                detail = stringResource(R.string.voice_no_auto),
                onClick = onSpeak,
            )
            if (ttsMessage != null) {
                Text(
                    text = ttsMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = QrLibreColors.DangerText,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
            } else if (!ttsReady) {
                Text(
                    text = stringResource(R.string.voice_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                )
            }
            SettingsDivider()
            SettingsRow(
                title = stringResource(R.string.privacy_headline),
                onClick = onOpenPrivacy,
            )
            SettingsDivider()
            // The document is built from the rows loaded on entry. The system
            // picker owns the destination; no storage permission and no share
            // sheet are involved.
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        val snapshot = rows
                        if (!snapshot.isNullOrEmpty()) {
                            feedback = null
                            pendingExport = snapshot
                            exporting = true
                            try {
                                createDocument.launch(
                                    exportFileName(System.currentTimeMillis()),
                                )
                            } catch (_: Exception) {
                                // No document provider to handle the request.
                                pendingExport = null
                                exporting = false
                                feedback = ExportFeedback.FAILED
                            }
                        }
                    },
                    enabled = !rows.isNullOrEmpty() && !exporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_export),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                when {
                    feedback == ExportFeedback.DONE -> Text(
                        text = stringResource(R.string.settings_export_done),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    feedback == ExportFeedback.FAILED -> Text(
                        text = stringResource(R.string.settings_export_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = QrLibreColors.DangerText,
                    )

                    rows != null && rows.isEmpty() -> Text(
                        text = stringResource(R.string.settings_export_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    rows != null -> Text(
                        text = stringResource(R.string.settings_export_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class ExportFeedback { DONE, FAILED }

@Composable
private fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    detail: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
