package ro.snapify.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ro.snapify.R
import ro.snapify.data.model.ScreenshotTab

/**
 * Multi-select filter bar for screenshot tags (Marked, Kept, Unmarked)
 */
@Composable
fun TagFilterBar(
    selectedTags: Set<ScreenshotTab>,
    onTagSelectionChanged: (Set<ScreenshotTab>) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            ScreenshotTab.MARKED to stringResource(R.string.tab_marked),
            ScreenshotTab.KEPT to stringResource(R.string.kept),
            ScreenshotTab.UNMARKED to stringResource(R.string.unmarked)
        ).forEach { (tag, label) ->
            FilterChip(
                selected = tag in selectedTags,
                onClick = {
                    val newSelection = if (tag in selectedTags) {
                        selectedTags - tag
                    } else {
                        selectedTags + tag
                    }
                    onTagSelectionChanged(newSelection)
                },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

/**
 * Multi-select filter bar for folders
 */
@Composable
fun FolderFilterBar(
    availableUris: List<String>,
    availablePaths: List<String>,
    selectedPaths: Set<String>,
    onFolderSelectionChanged: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        availableUris.zip(availablePaths).forEach { (uri, path) ->
            val folderName = try {
                val decoded = java.net.URLDecoder.decode(uri, "UTF-8")
                when {
                    decoded.contains("primary:") -> "Primary:" + decoded.substringAfter("primary:")
                    decoded.contains("tree/") -> "Tree:" + decoded.substringAfter("tree/")
                    else -> path.substringAfterLast('/')
                }
            } catch (e: Exception) {
                path.substringAfterLast('/')
            }
            FilterChip(
                selected = path in selectedPaths,
                onClick = {
                    val newSelection = if (path in selectedPaths) {
                        selectedPaths - path
                    } else {
                        selectedPaths + path
                    }
                    onFolderSelectionChanged(newSelection)
                },
                label = { Text(folderName) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            )
        }
    }
}
