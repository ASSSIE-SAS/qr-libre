package co.asssie.qrlibre.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asssie.qrlibre.R

@Composable
fun QrLibreApp(
    notificationAccess: Boolean,
    ttsReady: Boolean,
    ttsMessage: String?,
    onSpeak: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val tabLabels = listOf(R.string.tab_history, R.string.tab_apps, R.string.tab_privacy)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            TabRow(selectedTabIndex = tab) {
                tabLabels.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = tab == index,
                        onClick = { tab = index },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }

            when (tab) {
                0 -> HistoryScreen(
                    notificationAccess = notificationAccess,
                    ttsReady = ttsReady,
                    ttsMessage = ttsMessage,
                    onSpeak = onSpeak,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                )
                1 -> AppsScreen()
                else -> PrivacyScreen()
            }
        }
    }
}
