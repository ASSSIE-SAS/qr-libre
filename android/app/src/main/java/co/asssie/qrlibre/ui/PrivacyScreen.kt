package co.asssie.qrlibre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.asssie.qrlibre.R

@Composable
internal fun PrivacyScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.privacy_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        PrivacySection(R.string.privacy_reads_title, R.string.privacy_reads_body)
        PrivacySection(R.string.privacy_stays_title, R.string.privacy_stays_body)
        PrivacySection(R.string.privacy_delete_title, R.string.privacy_delete_body)
        PrivacySection(R.string.privacy_identity_title, R.string.privacy_identity_body)
        PrivacySection(R.string.privacy_limit_title, R.string.privacy_limit_body)
        HorizontalDivider()
        Text(
            text = stringResource(R.string.privacy_source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PrivacySection(titleRes: Int, bodyRes: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
