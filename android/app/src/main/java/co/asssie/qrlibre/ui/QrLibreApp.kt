package co.asssie.qrlibre.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.data.CapturesDb
import co.asssie.qrlibre.data.SelectedApps
import co.asssie.qrlibre.data.SelectionEvents
import co.asssie.qrlibre.notifications.resolveListenerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QrLibreApp(
    notificationAccess: Boolean,
    listenerConnected: Boolean,
    ttsReady: Boolean,
    ttsMessage: String?,
    onSpeak: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var dest by rememberSaveable { mutableStateOf(Dest.HISTORY) }
    val context = LocalContext.current
    val density = LocalDensity.current
    // The keyboard is measured in pixels. The apps list keeps collapsing its
    // chrome while it is up; the shell no longer has a bottom bar to hide.
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    // SelectedApps notifies SelectionEvents centrally, so read the count keyed
    // by that version instead of snapshotting the preferences once.
    val selectionVersion by SelectionEvents.version.collectAsStateWithLifecycle()
    val selectedApps = remember(selectionVersion) {
        SelectedApps(context).selected().size
    }
    val listenerStatus = remember(notificationAccess, listenerConnected, selectedApps) {
        resolveListenerUiState(
            accessGranted = notificationAccess,
            listenerConnected = listenerConnected,
            selectedApps = selectedApps,
        )
    }

    // Back walks one level down to Historial and only then lets the activity
    // finish. HistoryScreen still owns back for an open capture detail.
    BackHandler(enabled = dest != Dest.HISTORY) {
        backDestination(dest)?.let { dest = it }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                // Landscape three-button navigation and display cutouts live on
                // the left or right edge. Apply only the horizontal safe-drawing
                // insets here: the top is already handled above and imePadding
                // owns the keyboard.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                )
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = dest,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (
                        fadeIn(tween(180)) +
                            slideInHorizontally(tween(180)) { width -> direction * width / 14 }
                        ).togetherWith(fadeOut(tween(140)))
                },
                label = "dest-content",
            ) { target ->
                when (target) {
                    Dest.HISTORY -> HistoryScreen(
                        status = listenerStatus,
                        selectedApps = selectedApps,
                        ttsReady = ttsReady,
                        ttsMessage = ttsMessage,
                        onSpeak = onSpeak,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenApps = { dest = Dest.APPS },
                        onOpenSettings = { dest = gearDestination(target) },
                    )

                    Dest.SETTINGS -> SettingsScreen(
                        selectedApps = selectedApps,
                        ttsReady = ttsReady,
                        ttsMessage = ttsMessage,
                        onSpeak = onSpeak,
                        onOpenApps = { dest = Dest.APPS },
                        onOpenPairing = { dest = Dest.PAIRING },
                        onOpenPrivacy = { dest = Dest.PRIVACY },
                        onBack = { backDestination(Dest.SETTINGS)?.let { dest = it } },
                        loadCaptures = {
                            withContext(Dispatchers.IO) {
                                CapturesDb.get(context).recentCaptures()
                            }
                        },
                    )

                    Dest.PAIRING -> PairingScreen(
                        onBack = { backDestination(Dest.PAIRING)?.let { dest = it } },
                    )

                    Dest.APPS -> AppsScreen(
                        imeVisible = imeVisible,
                        onBack = { backDestination(Dest.APPS)?.let { dest = it } },
                    )

                    Dest.PRIVACY -> PrivacyScreen(
                        onBack = { backDestination(Dest.PRIVACY)?.let { dest = it } },
                    )
                }
            }
        }
    }
}
