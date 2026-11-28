package co.asssie.qrlibre.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import co.asssie.qrlibre.R
import co.asssie.qrlibre.data.SelectedApps
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SEARCH_THRESHOLD = 12

private data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
)

@Composable
internal fun AppsScreen(imeVisible: Boolean) {
    val context = LocalContext.current
    val preferences = remember { SelectedApps(context) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(preferences.selected()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val chrome = resolveAppsChrome(
            imeVisible = imeVisible,
            viewportHeightDp = maxHeight.value,
            fontScale = density.fontScale,
        )
        AppsContent(
            chrome = chrome,
            apps = apps,
            selected = selected,
            query = query,
            onQueryChange = { query = it },
            onCheckedChange = { packageName, checked ->
                selected = preferences.setSelected(packageName, checked)
            },
        )
    }
}

@Composable
private fun AppsContent(
    chrome: AppsChrome,
    apps: List<AppEntry>?,
    selected: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onCheckedChange: (String, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (chrome != AppsChrome.SEARCH_ONLY) {
            AppsHeader(compact = chrome == AppsChrome.COMPACT)
        }

        val loaded = apps
        when {
            loaded == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            loaded.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(20.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                EmptyCard(stringResource(R.string.apps_empty))
            }

            else -> {
                val filtered = remember(loaded, query) {
                    if (query.isBlank()) {
                        loaded
                    } else {
                        loaded.filter { it.label.contains(query, ignoreCase = true) }
                    }
                }
                if (loaded.size > SEARCH_THRESHOLD) {
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                        tight = chrome == AppsChrome.SEARCH_ONLY,
                    )
                }
                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(20.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        EmptyCard(stringResource(R.string.apps_no_matches))
                    }
                    if (chrome == AppsChrome.COMPACT) {
                        SelectionSummary(count = selected.size)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            top = if (chrome == AppsChrome.SEARCH_ONLY) 4.dp else 8.dp,
                            bottom = 4.dp,
                        ),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, app -> app.packageName },
                        ) { index, app ->
                            AppRow(
                                app = app,
                                checked = selected.contains(app.packageName),
                                showDivider = index < filtered.lastIndex,
                                onCheckedChange = { checked ->
                                    onCheckedChange(app.packageName, checked)
                                },
                            )
                        }
                        if (chrome == AppsChrome.COMPACT) {
                            item(key = "apps-selection-summary") {
                                SelectionSummary(count = selected.size)
                            }
                        }
                    }
                    if (chrome == AppsChrome.FULL) {
                        SelectionSummary(count = selected.size)
                    }
                }
            }
        }
    }
}

/** Full chrome in portrait; compact mode drops the long description. */
@Composable
private fun AppsHeader(compact: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (compact) 8.dp else 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (compact) 0.dp else 6.dp),
    ) {
        Text(
            text = stringResource(R.string.apps_headline),
            style = if (compact) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineLarge
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!compact) {
            Text(
                text = stringResource(R.string.apps_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    tight: Boolean,
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = if (tight) 4.dp else 14.dp,
            ),
        placeholder = {
            Text(
                text = stringResource(R.string.apps_search),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        singleLine = true,
        shape = CircleShape,
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )
}

@Composable
private fun AppRow(
    app: AppEntry,
    checked: Boolean,
    showDivider: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (app.icon != null) {
                Image(
                    bitmap = app.icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = app.label.take(1).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    uncheckedTrackColor = QrLibreColors.SwitchOff,
                    uncheckedBorderColor = QrLibreColors.SwitchOff,
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SelectionSummary(count: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Text(
                text = pluralStringResource(R.plurals.apps_selected_summary, count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val byPackage = LinkedHashMap<String, android.content.pm.ApplicationInfo>()

    for (resolved in pm.queryIntentActivities(launcherIntent, 0)) {
        val activity = resolved.activityInfo ?: continue
        val applicationInfo = activity.applicationInfo ?: continue
        if (applicationInfo.packageName == context.packageName) continue
        byPackage.putIfAbsent(applicationInfo.packageName, applicationInfo)
    }

    return byPackage.values.map { applicationInfo ->
        val label = runCatching {
            pm.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(applicationInfo.packageName)
        val icon = runCatching {
            pm.getApplicationIcon(applicationInfo)
                .toBitmap(width = 96, height = 96)
                .asImageBitmap()
        }.getOrNull()
        AppEntry(
            packageName = applicationInfo.packageName,
            label = label,
            icon = icon,
        )
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { entry -> entry.label })
}
