package co.asssie.qrlibre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.Capture
import co.asssie.qrlibre.data.captureCopyText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full, scrollable capture detail. The body is never truncated, and the text is
 * selectable. Copy is the only clipboard action: it happens on an explicit tap
 * and never when the screen opens.
 */
@Composable
internal fun CaptureDetailScreen(
    capture: Capture,
    onBack: () -> Unit,
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

private fun formatDetailTimestamp(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()).format(Date(epochMs))
