package com.apkupdateross.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.DownloadChoice
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.repository.detectFourPdaReleaseType
import com.apkupdateross.ui.component.TvAwareIconButton as IconButton
import com.apkupdateross.ui.component.TvAwareTextButton as TextButton
import com.apkupdateross.ui.theme.ApkTheme
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadChoiceDialog(
    update: AppUpdate,
    onSelect: (AppUpdate) -> Unit,
    onDismiss: () -> Unit,
    onLoadPostNotes: (suspend (Long) -> String?)? = null,
    onOpenPost: ((String) -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    var expandedKey by remember(update.id) { mutableStateOf<String?>(null) }
    val loadedNotes = remember(update.id) { mutableStateMapOf<Long, String>() }
    val loadingPosts = remember(update.id) { mutableStateMapOf<Long, Boolean>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.fourpda_choose_file_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = ApkTheme.colors.textPrimary
                )
                Text(
                    text = update.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = ApkTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
            ) {
                items(update.downloadChoices, key = { it.link.link }) { choice ->
                    val rowKey = choice.link.link
                    val canInspect = choice.postId > 0L && (onLoadPostNotes != null || choice.postUrl.isNotBlank())
                    val isExpanded = expandedKey == rowKey
                    val isLoading = loadingPosts[choice.postId] == true
                    val notes = loadedNotes[choice.postId]
                    val resolvedReleaseType = detectFourPdaReleaseType(
                        version = choice.version,
                        fileName = choice.label,
                        postNotes = notes.orEmpty()
                    )

                    DownloadChoiceRow(
                        choice = choice,
                        releaseType = resolvedReleaseType,
                        canInspect = canInspect,
                        isExpanded = isExpanded,
                        isLoadingNotes = isLoading,
                        notes = notes,
                        onToggleInfo = {
                            if (isExpanded) {
                                expandedKey = null
                            } else {
                                expandedKey = rowKey
                                val postId = choice.postId
                                if (postId > 0L && onLoadPostNotes != null && !loadedNotes.containsKey(postId) && loadingPosts[postId] != true) {
                                    loadingPosts[postId] = true
                                    scope.launch {
                                        val fetched = runCatching { onLoadPostNotes(postId) }.getOrNull().orEmpty()
                                        loadedNotes[postId] = fetched
                                        loadingPosts[postId] = false
                                    }
                                }
                            }
                        },
                        onOpenPost = if (choice.postUrl.isNotBlank() && onOpenPost != null) {
                            { onOpenPost(choice.postUrl) }
                        } else null,
                        onClick = {
                            onSelect(
                                update.copy(
                                    link = choice.link,
                                    total = choice.sizeBytes,
                                    version = choice.version.ifBlank { update.version },
                                    downloadChoices = emptyList()
                                )
                            )
                        }
                    )
                    HorizontalDivider(color = ApkTheme.colors.divider)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_custom_repo_cancel))
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadChoiceRow(
    choice: DownloadChoice,
    releaseType: ReleaseType,
    canInspect: Boolean,
    isExpanded: Boolean,
    isLoadingNotes: Boolean,
    notes: String?,
    onToggleInfo: () -> Unit,
    onOpenPost: (() -> Unit)?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onClick)
                    .padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = ApkTheme.colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (releaseType != ReleaseType.Stable) {
                        ChoicePill(
                            text = stringResource(releaseType.labelRes),
                            accent = true
                        )
                    }
                    if (choice.abi.isNotBlank()) {
                        ChoicePill(text = choice.abi, accent = false)
                    }
                    if (choice.sizeBytes > 0L) {
                        ChoicePill(text = formatChoiceSize(choice.sizeBytes), accent = false)
                    }
                    if (choice.uploadedAt.isNotBlank()) {
                        ChoicePill(text = choice.uploadedAt, accent = false)
                    }
                }
            }

            if (canInspect) {
                IconButton(onClick = onToggleInfo) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Outlined.Info,
                        contentDescription = stringResource(R.string.fourpda_choice_info_cd),
                        tint = if (isExpanded) ApkTheme.colors.accent else ApkTheme.colors.textSecondary
                    )
                }
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = ApkTheme.shapes.sm,
                color = ApkTheme.colors.surfaceSecondary,
                border = BorderStroke(1.dp, ApkTheme.colors.divider)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isLoadingNotes -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = ApkTheme.colors.accent
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.fourpda_choice_loading_notes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ApkTheme.colors.textSecondary
                                )
                            }
                        }
                        !notes.isNullOrBlank() -> {
                            SelectionContainer {
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = ApkTheme.colors.textPrimary
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = stringResource(R.string.fourpda_choice_no_notes),
                                style = MaterialTheme.typography.bodySmall,
                                color = ApkTheme.colors.textTertiary
                            )
                        }
                    }

                    if (onOpenPost != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onOpenPost) {
                                Text(
                                    text = stringResource(R.string.fourpda_open_post),
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChoicePill(
    text: String,
    accent: Boolean
) {
    val fg = if (accent) ApkTheme.colors.accent else ApkTheme.colors.textSecondary
    val bg = if (accent) ApkTheme.colors.accent.copy(alpha = 0.14f) else ApkTheme.colors.surfaceSecondary
    val border = if (accent) ApkTheme.colors.accent.copy(alpha = 0.35f) else ApkTheme.colors.divider
    Surface(
        shape = ApkTheme.shapes.xs,
        color = bg,
        border = BorderStroke(1.dp, border)
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = fg
            )
        }
    }
}

private fun formatChoiceSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1.0) {
        String.format(Locale.US, "%.1f MB", mb)
    } else {
        val kb = bytes / 1024.0
        String.format(Locale.US, "%.0f KB", kb)
    }
}
