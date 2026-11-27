package co.asssie.qrlibre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.Capture
import co.asssie.qrlibre.data.CaptureEvents
import co.asssie.qrlibre.data.CapturesDb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HistoryScreen(
    notificationAccess: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { CapturesDb.get(context) }
    val version by CaptureEvents.version.collectAsStateWithLifecycle()
    var captures by remember { mutableStateOf<List<Capture>?>(null) }
    LaunchedEffect(version) {
        captures = withContext(Dispatchers.IO) { db.recentCaptures() }
    }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PermissionCard(notificationAccess, onOpenNotificationSettings) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.history_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (!captures.isNullOrEmpty()) {
                    TextButton(onClick = { confirmClear = true }) {
                        Text(stringResource(R.string.history_clear))
                    }
                }
            }
        }

        val list = captures
        when {
            list == null -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            list.isEmpty() -> item { EmptyCard(stringResource(R.string.history_empty)) }
            else -> items(list, key = { capture -> capture.id }) { capture ->
                CaptureRow(capture)
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_title)) },
            text = { Text(stringResource(R.string.history_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        scope.launch {
                            withContext(Dispatchers.IO) { db.clear() }
                            CaptureEvents.notifyChanged()
                        }
                    },
                ) {
                    Text(stringResource(R.string.history_clear_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.history_cancel))
                }
            },
        )
    }
}

@Composable
private fun PermissionCard(granted: Boolean, onOpenSettings: () -> Unit) {
    val colors = if (granted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    }

    Card(colors = colors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(
                    if (granted) R.string.permission_granted else R.string.permission_missing,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (granted) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_review_settings))
                }
            } else {
                Text(
                    text = stringResource(R.string.permission_explanation),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.permission_steps),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_open_settings))
                }
            }
        }
    }
}

@Composable
private fun CaptureRow(capture: Capture) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = capture.app,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatTimestamp(capture.epochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (capture.title.isNotEmpty()) {
                Text(
                    text = capture.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (capture.body.isNotEmpty()) {
                Text(
                    text = capture.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(epochMs))
