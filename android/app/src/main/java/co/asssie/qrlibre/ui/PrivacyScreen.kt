package co.asssie.qrlibre.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asssie.qrlibre.R

internal data class PrivacySection(
    @DrawableRes val icon: Int,
    @StringRes val title: Int,
    @StringRes val body: Int,
)

/** Authored in display order; the privacy screen renders exactly these sections. */
internal val privacySections = listOf(
    PrivacySection(R.drawable.ic_device, R.string.privacy_stays_title, R.string.privacy_stays_body),
    PrivacySection(R.drawable.ic_eye, R.string.privacy_reads_title, R.string.privacy_reads_body),
    PrivacySection(R.drawable.ic_warning, R.string.privacy_limit_title, R.string.privacy_limit_body),
    PrivacySection(R.drawable.ic_trash, R.string.privacy_delete_title, R.string.privacy_delete_body),
    PrivacySection(R.drawable.ic_privacy, R.string.privacy_identity_title, R.string.privacy_identity_body),
)

@Composable
internal fun PrivacyScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        BackHeader(
            onBack = onBack,
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.privacy_headline),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.privacy_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )
            privacySections.forEachIndexed { index, section ->
                if (index > 0) {
                    SectionDivider()
                }
                PrivacySectionRow(section)
            }
            SectionDivider()
            ClosingLine()
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.privacy_source),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun PrivacySectionRow(section: PrivacySection) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Icon(
            painter = painterResource(section.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(section.title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(section.body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClosingLine() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_device),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.privacy_closing),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
