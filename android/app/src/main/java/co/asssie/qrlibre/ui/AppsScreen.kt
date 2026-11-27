package co.asssie.qrlibre.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
internal fun AppsScreen() {
    val context = LocalContext.current
    val preferences = remember { SelectedApps(context) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var selected by remember { mutableStateOf(preferences.selected()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.apps_explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.apps_selected_count, selected.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val loaded = apps
        when {
            loaded == null -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
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
                if (loaded.size > SEARCH_THRESHOLD) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text(stringResource(R.string.apps_search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp),
                    )
                }
                val filtered = remember(loaded, query) {
                    if (query.isBlank()) {
                        loaded
                    } else {
                        loaded.filter { it.label.contains(query, ignoreCase = true) }
                    }
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
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 8.dp,
                            bottom = 24.dp,
                        ),
                    ) {
                        items(filtered, key = { app -> app.packageName }) { app ->
                            AppRow(
                                app = app,
                                checked = selected.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    selected = preferences.setSelected(app.packageName, checked)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = app.label.take(1).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Checkbox(checked = checked, onCheckedChange = null)
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
