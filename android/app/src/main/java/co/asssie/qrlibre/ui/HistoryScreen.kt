package co.asssie.qrlibre.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.Capture
import co.asssie.qrlibre.data.CaptureEvents
import co.asssie.qrlibre.data.CapturesDb
import co.asssie.qrlibre.data.localDayRange
import co.asssie.qrlibre.data.millisUntilNextLocalMidnight
import co.asssie.qrlibre.notifications.ListenerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun HistoryScreen(
    status: ListenerUiState,
    selectedApps: Int,
    ttsReady: Boolean,
    ttsMessage: String?,
    onSpeak: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current
    val db = remember { CapturesDb.get(context) }
    val version by CaptureEvents.version.collectAsStateWithLifecycle()
    var captures by remember { mutableStateOf<List<Capture>?>(null) }
    // Null means the total is not known yet (or the count read failed); the
    // screen never presents an unread count as a confirmed zero.
    var todayCount by remember { mutableStateOf<Long?>(null) }
    // Bumped by a local midnight, a resume and a system time/time-zone change.
    var dayTick by remember { mutableStateOf(0) }

    LaunchedEffect(version, dayTick) {
        val loaded = withContext(Dispatchers.IO) {
            val rows = db.recentCaptures()
            val range = localDayRange(System.currentTimeMillis())
            val count = try {
                db.countCapturesBetween(range.startMs, range.endMs)
            } catch (_: Exception) {
                // A failed count is unknown, not zero.
                null
            }
            rows to count
        }
        captures = loaded.first
        todayCount = loaded.second
    }

    // In-process midnight refresh while the screen is composed. It restarts
    // after every refresh, so a resume or a clock change recomputes the delay
    // from the current time instead of keeping a mistimed sleep. The one-second
    // guard keeps the timer from firing just before the boundary, and the
    // coroutine is cancelled with the screen.
    LaunchedEffect(dayTick) {
        while (true) {
            delay(millisUntilNextLocalMidnight(System.currentTimeMillis()) + 1_000L)
            dayTick++
        }
    }

    // Coming back from another app or settings may follow a manual clock or
    // time-zone change while the app was backgrounded.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        dayTick++
    }

    // System time and time-zone changes are protected broadcasts. The receiver
    // exists only while the screen is composed: registered at runtime,
    // unregistered on dispose, with no manifest entry and no permission.
    DisposableEffect(context) {
        val timeReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                dayTick++
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            timeReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose {
            context.unregisterReceiver(timeReceiver)
        }
    }

    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    val list = captures
    val presentation = remember(status, list) {
        resolveHistoryPresentation(
            status = status,
            hasCaptures = !list.isNullOrEmpty(),
        )
    }
    val listState = rememberLazyListState()
    var openCapture by remember { mutableStateOf<Capture?>(null) }
    // System back closes the detail first; only an already-open list lets the
    // activity handle back and exit.
    BackHandler(enabled = openCapture != null) { openCapture = null }
    val opened = openCapture
    if (opened != null) {
        CaptureDetailScreen(capture = opened, onBack = { openCapture = null })
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .animateContentSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.history_label_today),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.history_headline),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.history_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            HeroCard(
                status = status,
                todayCount = todayCount,
                selectedApps = selectedApps,
                onOpenApps = onOpenApps,
                onOpenNotificationSettings = onOpenNotificationSettings,
            )
        }

        if (list == null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            if (presentation.showCaptures) {
                items(list, key = { capture -> capture.id }) { capture ->
                    CaptureCard(
                        capture = capture,
                        onOpen = { openCapture = capture },
                        modifier = Modifier.animateItem(),
                    )
                }
                item {
                    TextButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = QrLibreColors.DangerText,
                        ),
                    ) {
                        Text(stringResource(R.string.history_clear))
                    }
                }
            } else if (presentation.showEmptyHistory) {
                item { EmptyHistory(onOpenApps = onOpenApps) }
            }
        }

        if (presentation.showVoiceTest) {
            item {
                VoiceTest(ready = ttsReady, message = ttsMessage, onSpeak = onSpeak)
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
private fun HeroCard(
    status: ListenerUiState,
    todayCount: Long?,
    selectedApps: Int,
    onOpenApps: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    when (status) {
        ListenerUiState.PERMISSION_MISSING -> PermissionCard(
            onOpenNotificationSettings = onOpenNotificationSettings,
        )

        ListenerUiState.NO_APPS -> SetupCard(
            title = stringResource(R.string.apps_headline),
            body = stringResource(R.string.apps_explanation),
            actionLabel = stringResource(R.string.hero_no_apps_action),
            onAction = onOpenApps,
        )

        ListenerUiState.CONNECTING -> StatusCard(
            title = stringResource(R.string.hero_connecting),
            body = stringResource(R.string.hero_connecting_body),
            actionLabel = stringResource(R.string.permission_review_settings),
            onAction = onOpenNotificationSettings,
        )

        ListenerUiState.ACTIVE -> ActiveSummary(
            todayCount = todayCount,
            selectedApps = selectedApps,
        )
    }
}

/** Compact permission card: the single primary action is the system grant. */
@Composable
private fun PermissionCard(onOpenNotificationSettings: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.permission_missing_headline),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.permission_missing),
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = onOpenNotificationSettings,
                modifier = Modifier.padding(top = 4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(stringResource(R.string.permission_grant_action))
            }
        }
    }
}

/** Compact first-run card with one primary setup action. */
@Composable
private fun SetupCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

/** Compact active summary; the count and suffix wrap instead of clipping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveSummary(todayCount: Long?, selectedApps: Int) {
    val countText = todayCount?.toString() ?: "—"
    val suffix = stringResource(R.string.hero_captured_suffix)
    val appsText = pluralStringResource(R.plurals.hero_apps_count, selectedApps, selectedApps)
    // One merged accessibility node: TalkBack reads the status, the count and
    // the app count in a single pass instead of disconnected fragments.
    val spokenSummary = stringResource(
        R.string.hero_active_summary,
        countText,
        suffix,
        appsText,
    )
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .clearAndSetSemantics { contentDescription = spokenSummary },
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.hero_listening),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // An em dash means "not known yet": the count is never
                        // reported as a confirmed zero before the SQL read.
                        text = countText,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = suffix,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = appsText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A plain surface card with one text action, used by CONNECTING. */
@Composable
private fun StatusCard(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun VoiceTest(ready: Boolean, message: String?, onSpeak: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = onSpeak,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
        ) {
            Text(
                text = stringResource(R.string.voice_test),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = stringResource(R.string.voice_no_auto),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = QrLibreColors.DangerText,
            )
        } else if (!ready) {
            Text(
                text = stringResource(R.string.voice_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyHistory(onOpenApps: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bell),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(R.string.history_empty_title),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.history_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        TextButton(onClick = onOpenApps) {
            Text(stringResource(R.string.history_choose_apps))
        }
    }
}

@Composable
private fun CaptureCard(
    capture: Capture,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(R.string.history_open_detail),
                onClick = onOpen,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = capture.app,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatTimestamp(capture.epochMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (capture.title.isNotEmpty()) {
                Text(
                    text = capture.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (capture.body.isNotEmpty()) {
                Text(
                    text = capture.body,
                    style = MonoBody,
                    color = QrLibreColors.LimeText,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatTimestamp(epochMs: Long): String =
    SimpleDateFormat("HH:mm dd/MM", Locale.getDefault()).format(Date(epochMs))
