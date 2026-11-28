package co.asssie.qrlibre.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.SelectedApps
import co.asssie.qrlibre.data.SelectionEvents
import co.asssie.qrlibre.notifications.resolveListenerUiState

private data class NavItem(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
)

private val NavItems = listOf(
    NavItem(R.string.tab_history, R.drawable.ic_history),
    NavItem(R.string.tab_apps, R.drawable.ic_apps),
    NavItem(R.string.tab_privacy, R.drawable.ic_privacy),
)

@Composable
fun QrLibreApp(
    notificationAccess: Boolean,
    listenerConnected: Boolean,
    ttsReady: Boolean,
    ttsMessage: String?,
    onSpeak: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val context = LocalContext.current
    val density = LocalDensity.current
    // The keyboard is measured in pixels; any positive IME inset means the
    // software keyboard is showing and the bottom bar should yield its space
    // to the app list without touching the selected tab.
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
                // insets here: the top is already handled above, the bottom bar
                // owns navigationBars below, and imePadding owns the keyboard.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
                )
                .imePadding(),
        ) {
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (
                        fadeIn(tween(180)) +
                            slideInHorizontally(tween(180)) { width -> direction * width / 14 }
                        ).togetherWith(fadeOut(tween(140)))
                },
                label = "tab-content",
            ) { index ->
                when (index) {
                    0 -> HistoryScreen(
                        status = listenerStatus,
                        selectedApps = selectedApps,
                        ttsReady = ttsReady,
                        ttsMessage = ttsMessage,
                        onSpeak = onSpeak,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onOpenApps = { tab = 1 },
                    )
                    1 -> AppsScreen()
                    else -> PrivacyScreen()
                }
            }

            if (!imeVisible) {
                BottomBar(tab = tab, onSelect = { tab = it })
            }
        }
    }
}

@Composable
private fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(QrLibreColors.Page),
    ) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems.forEachIndexed { index, item ->
                NavBarItem(
                    item = item,
                    selected = tab == index,
                    onClick = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun NavBarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = stringResource(item.label),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.sp),
            color = color,
        )
    }
}
