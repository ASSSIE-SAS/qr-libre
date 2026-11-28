package co.asssie.qrlibre.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.Capture
import co.asssie.qrlibre.data.Verdict
import co.asssie.qrlibre.data.captureCopyText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full, scrollable capture detail. The body is never truncated, and the text is
 * selectable. Copy is the only clipboard action: it happens on an explicit tap
 * and never when the screen opens. The verdict toggles record the owner's
 * classification, not a bank confirmation, and every tap is persisted through
 * [onSetVerdict].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CaptureDetailScreen(
    capture: Capture,
    onBack: () -> Unit,
    onSetVerdict: (Verdict?) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val timestamp = remember(capture.epochMs) { formatDetailTimestamp(capture.epochMs) }
    val copyText = remember(capture, timestamp) {
        captureCopyText(
            app = capture.app,
            packageName = capture.packageName,
            title = capture.title,
            body = capture.body,
            timestamp = timestamp,
        )
    }
    var copied by remember(capture.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(
            onClick = onBack,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.capture_detail_back))
        }

        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = capture.app,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = capture.packageName,
                        style = MonoBody,
                        color = QrLibreColors.LimeText,
                    )
                }
                if (capture.title.isNotEmpty()) {
                    Text(
                        text = capture.title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (capture.body.isNotEmpty()) {
                    Text(
                        text = capture.body,
                        style = MonoBody,
                        color = QrLibreColors.LimeText,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.capture_verdict_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VerdictToggle(
                    label = stringResource(R.string.capture_verdict_payment),
                    isSelected = capture.verdict == Verdict.PAYMENT,
                    onClick = {
                        onSetVerdict(
                            if (capture.verdict == Verdict.PAYMENT) null else Verdict.PAYMENT,
                        )
                    },
                )
                VerdictToggle(
                    label = stringResource(R.string.capture_verdict_not_payment),
                    isSelected = capture.verdict == Verdict.NOT_PAYMENT,
                    onClick = {
                        onSetVerdict(
                            if (capture.verdict == Verdict.NOT_PAYMENT) null else Verdict.NOT_PAYMENT,
                        )
                    },
                )
            }
            Text(
                text = stringResource(R.string.capture_verdict_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(copyText))
                    copied = true
                },
            ) {
                Text(stringResource(R.string.capture_detail_copy))
            }
            Text(
                text = stringResource(R.string.capture_detail_copy_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (copied) {
                Text(
                    text = stringResource(R.string.capture_detail_copied),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
    }
}

/**
 * One of the two classification toggles. The chosen option is a lime-filled
 * button; the other stays outlined. Selection is announced to accessibility
 * services, so the colour is never the only signal.
 */
@Composable
private fun VerdictToggle(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val selection = Modifier.semantics { selected = isSelected }
    if (isSelected) {
        Button(
            onClick = onClick,
            modifier = selection,
            colors = ButtonDefaults.buttonColors(
                containerColor = QrLibreColors.Lime,
                contentColor = QrLibreColors.OnLime,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = selection,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp),
        ) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun formatDetailTimestamp(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(epochMs))
