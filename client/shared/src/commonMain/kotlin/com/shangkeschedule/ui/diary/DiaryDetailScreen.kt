package com.shangkeschedule.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppLoading
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.diary_action_delete
import shangkeschedule.shared.generated.resources.diary_action_edit
import shangkeschedule.shared.generated.resources.diary_delete_failed
import shangkeschedule.shared.generated.resources.diary_delete_text
import shangkeschedule.shared.generated.resources.diary_delete_title
import shangkeschedule.shared.generated.resources.diary_deleted
import shangkeschedule.shared.generated.resources.diary_draft_badge
import shangkeschedule.shared.generated.resources.diary_load_failed
import shangkeschedule.shared.generated.resources.diary_title
import shangkeschedule.shared.generated.resources.edit_24px

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryDetailScreen(
    entryId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: DiaryDetailViewModel = koinViewModel()
) {
    LaunchedEffect(entryId) { viewModel.bind(entryId) }
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val colors = appColors()
    val spacing = appSpacing()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val deletedText = stringResource(Res.string.diary_deleted)
    val deleteFailedText = stringResource(Res.string.diary_delete_failed)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = { Text(stringResource(Res.string.diary_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = null
                        )
                    }
                },
                actions = {
                    if (detail != null) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.edit_24px),
                                contentDescription = stringResource(Res.string.diary_action_edit)
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.delete_24px),
                                contentDescription = stringResource(Res.string.diary_action_delete),
                                tint = colors.danger
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            detail == null -> AppLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            detail?.entry?.deletedAt != null -> AppEmptyState(
                hint = stringResource(Res.string.diary_load_failed),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                fillScreen = true
            )

            else -> {
                val current = detail
                if (current != null) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = spacing.pageHorizontal,
                        end = spacing.pageHorizontal,
                        top = spacing.cardGap,
                        bottom = spacing.contentBottom
                    )
                ) {
                    item(key = "meta") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${formatDiaryDate(current.entry.occurredAt, current.entry.timeZoneId)} " +
                                        formatDiaryTime(current.entry.occurredAt, current.entry.timeZoneId),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.textSecondary
                                )
                                val moodScore = current.mood?.score
                                if (moodScore != null) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .background(colors.primarySoft),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = moodScore.toString(),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = diaryMoodLabel(moodScore),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                            if (!current.entry.title.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(spacing.listGap))
                                Text(
                                    text = current.entry.title.orEmpty(),
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = appType().titleWeight),
                                    color = colors.textPrimary
                                )
                            }
                            if (current.tags.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(spacing.listGap))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    current.tags.forEach { tag ->
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(colors.inputBg)
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(tag.color ?: DiaryTagPalette.first()))
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = tag.name,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colors.textSecondary
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(spacing.listGap))
                        }
                    }
                    item(key = "markdown") {
                        DiaryMarkdown(
                            markdown = current.entry.markdown,
                            attachmentPaths = current.attachmentPaths
                        )
                    }
                }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AppAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(Res.string.diary_delete_title)) },
            text = { Text(stringResource(Res.string.diary_delete_text)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.diary_action_delete),
                    onConfirm = {
                        showDeleteConfirm = false
                        viewModel.delete { ok ->
                            if (ok) {
                                ToastManager.show(deletedText)
                                onBack()
                            } else {
                                ToastManager.show(deleteFailedText)
                            }
                        }
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { showDeleteConfirm = false },
                    danger = true
                )
            },
            dismissButton = {}
        )
    }
}
