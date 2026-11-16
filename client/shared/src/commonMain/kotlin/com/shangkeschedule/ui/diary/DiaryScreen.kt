package com.shangkeschedule.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shangkeschedule.Destination
import com.shangkeschedule.data.repository.DiarySummary
import com.shangkeschedule.data.repository.MoodPoint
import com.shangkeschedule.ui.components.AdaptiveNavigationScaffold
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppErrorState
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.DateRangePickerModal
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.settings.SectionCard
import com.shangkeschedule.ui.settings.SettingItem
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.diary_action_delete
import shangkeschedule.shared.generated.resources.diary_action_manage_tags
import shangkeschedule.shared.generated.resources.diary_action_new
import shangkeschedule.shared.generated.resources.diary_action_quick_mood
import shangkeschedule.shared.generated.resources.diary_draft_badge
import shangkeschedule.shared.generated.resources.diary_drafts
import shangkeschedule.shared.generated.resources.diary_empty_action
import shangkeschedule.shared.generated.resources.diary_empty_hint
import shangkeschedule.shared.generated.resources.diary_empty_title
import shangkeschedule.shared.generated.resources.diary_filter_all_dates
import shangkeschedule.shared.generated.resources.diary_filter_clear
import shangkeschedule.shared.generated.resources.diary_filter_date
import shangkeschedule.shared.generated.resources.diary_filter_match_all
import shangkeschedule.shared.generated.resources.diary_filter_match_any
import shangkeschedule.shared.generated.resources.diary_filter_tags
import shangkeschedule.shared.generated.resources.diary_from_diary
import shangkeschedule.shared.generated.resources.diary_load_failed
import shangkeschedule.shared.generated.resources.diary_load_more
import shangkeschedule.shared.generated.resources.diary_mood_action
import shangkeschedule.shared.generated.resources.diary_mood_deleted
import shangkeschedule.shared.generated.resources.diary_mood_delete_text
import shangkeschedule.shared.generated.resources.diary_mood_delete_title
import shangkeschedule.shared.generated.resources.diary_mood_empty
import shangkeschedule.shared.generated.resources.diary_mood_empty_hint
import shangkeschedule.shared.generated.resources.diary_mood_save_failed
import shangkeschedule.shared.generated.resources.diary_mood_saved
import shangkeschedule.shared.generated.resources.diary_pane_diary
import shangkeschedule.shared.generated.resources.diary_pane_mood
import shangkeschedule.shared.generated.resources.diary_search_placeholder
import shangkeschedule.shared.generated.resources.diary_title
import shangkeschedule.shared.generated.resources.filter_list_24px
import shangkeschedule.shared.generated.resources.image_24px
import shangkeschedule.shared.generated.resources.more_horiz_24px
import shangkeschedule.shared.generated.resources.sticky_note_2_24px
import kotlin.time.Clock

/** 正在编辑的快速心情；null 表示新建。 */
private data class QuickMoodTarget(
    val id: String?,
    val score: Int?,
    val occurredAt: Long,
    val timeZoneId: String,
    val tagIds: Set<String>
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryScreen(
    onNavigate: (Destination) -> Unit,
    onBack: () -> Unit,
    viewModel: DiaryViewModel = koinViewModel()
) {
    val pane by viewModel.pane.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val drafts by viewModel.drafts.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val limit by viewModel.limit.collectAsStateWithLifecycle()
    val moodPoints by viewModel.moodPoints.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val colors = appColors()
    val spacing = appSpacing()

    var showTagManager by remember { mutableStateOf(false) }
    var quickMood by remember { mutableStateOf<QuickMoodTarget?>(null) }
    var showDateRange by remember { mutableStateOf(false) }
    var pendingDeleteMood by remember { mutableStateOf<MoodPoint?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    val now = Clock.System.now().toEpochMilliseconds()
    val textNewEntry = stringResource(Res.string.diary_action_new)
    val textQuickMood = stringResource(Res.string.diary_action_quick_mood)
    val textManageTags = stringResource(Res.string.diary_action_manage_tags)
    val textClearFilters = stringResource(Res.string.diary_filter_clear)
    val textMoodSaved = stringResource(Res.string.diary_mood_saved)
    val textMoodSaveFailed = stringResource(Res.string.diary_mood_save_failed)
    val textMoodDeleted = stringResource(Res.string.diary_mood_deleted)
    val textDelete = stringResource(Res.string.diary_action_delete)
    val textCancel = stringResource(Res.string.action_cancel)

    Box(modifier = Modifier.fillMaxSize()) {
        AdaptiveNavigationScaffold(
            currentDestination = Destination.Diary,
            onTabSelected = { dest -> onNavigate(dest) }
        ) { outerPadding ->
            Scaffold(
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                topBar = {
                    AppTopAppBar(
                        title = { Text(stringResource(Res.string.diary_title)) },
                        actions = {
                            IconButton(onClick = { showFilters = !showFilters }) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.filter_list_24px),
                                    contentDescription = stringResource(Res.string.diary_filter_tags),
                                    tint = if (showFilters || filter.isActive) colors.primary else colors.textPrimary
                                )
                            }
                            IconButton(onClick = { showTagManager = true }) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.more_horiz_24px),
                                    contentDescription = textManageTags,
                                    tint = colors.textPrimary
                                )
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (error != null) {
                        AppErrorState(
                            hint = error ?: stringResource(Res.string.diary_load_failed),
                            fillScreen = true,
                            onRetry = viewModel::retryLoad
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = spacing.pageHorizontal,
                                end = spacing.pageHorizontal,
                                top = spacing.cardGap,
                                bottom = outerPadding.calculateBottomPadding() + spacing.contentBottom + 72.dp
                            )
                        ) {
                            item(key = "entry-actions") {
                                SectionCard {
                                    SettingItem(
                                        title = textNewEntry,
                                        leadingIcon = vectorResource(Res.drawable.sticky_note_2_24px),
                                        verticalPadding = 10.dp,
                                        onClick = { onNavigate(Destination.DiaryEditor()) }
                                    )
                                    androidx.compose.material3.HorizontalDivider(color = colors.divider, thickness = 0.5.dp)
                                    SettingItem(
                                        title = textQuickMood,
                                        leadingIcon = vectorResource(Res.drawable.add_24px),
                                        verticalPadding = 10.dp,
                                        onClick = {
                                            quickMood = QuickMoodTarget(
                                                id = null,
                                                score = null,
                                                occurredAt = now,
                                                timeZoneId = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                                tagIds = emptySet()
                                            )
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(spacing.listGap))
                            }

                            item(key = "pane-switch") {
                                AppSegmentedControl(
                                    options = listOf(
                                        stringResource(Res.string.diary_pane_diary),
                                        stringResource(Res.string.diary_pane_mood)
                                    ),
                                    selectedIndex = pane.ordinal,
                                    onSelect = { index ->
                                        viewModel.setPane(if (index == 0) DiaryPane.DIARY else DiaryPane.MOOD)
                                    }
                                )
                                Spacer(modifier = Modifier.height(spacing.listGap))
                            }

                            if (showFilters) {
                                item(key = "filters") {
                                    DiaryFilterCard(
                                        filter = filter,
                                        tags = tags,
                                        onKeywordChange = viewModel::setKeyword,
                                        onToggleTag = viewModel::toggleTag,
                                        onSetMatchAll = viewModel::setMatchAllTags,
                                        onPickDateRange = { showDateRange = true },
                                        onClear = viewModel::clearFilters,
                                        clearLabel = textClearFilters
                                    )
                                    Spacer(modifier = Modifier.height(spacing.listGap))
                                }
                            }

                            if (pane == DiaryPane.DIARY) {
                                if (drafts.isNotEmpty()) {
                                    item(key = "drafts-header") {
                                        Text(
                                            text = stringResource(Res.string.diary_drafts),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = appType().titleWeight),
                                            color = colors.textSecondary
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                    }
                                    items(drafts, key = { "draft-${it.entry.id}" }) { summary ->
                                        DiaryEntryCard(
                                            summary = summary,
                                            isDraft = true,
                                            onClick = { onNavigate(Destination.DiaryEditor(summary.entry.id)) }
                                        )
                                        Spacer(modifier = Modifier.height(spacing.listGap))
                                    }
                                }

                                if (entries.isEmpty()) {
                                    item(key = "entries-empty") {
                                        AppEmptyState(
                                            hint = stringResource(Res.string.diary_empty_hint),
                                            actionLabel = if (!filter.isActive) {
                                                stringResource(Res.string.diary_empty_action)
                                            } else {
                                                null
                                            },
                                            onAction = if (!filter.isActive) {
                                                { onNavigate(Destination.DiaryEditor()) }
                                            } else {
                                                null
                                            }
                                        )
                                    }
                                } else {
                                    val groups = entries.groupBy {
                                        formatDiaryDate(it.entry.occurredAt, it.entry.timeZoneId)
                                    }
                                    groups.forEach { (date, groupEntries) ->
                                        item(key = "date-$date") {
                                            Text(
                                                text = date,
                                                modifier = Modifier.padding(top = 4.dp),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = appType().titleWeight),
                                                color = colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        items(groupEntries, key = { "entry-${it.entry.id}" }) { summary ->
                                            DiaryEntryCard(
                                                summary = summary,
                                                isDraft = false,
                                                onClick = { onNavigate(Destination.DiaryDetail(summary.entry.id)) }
                                            )
                                            Spacer(modifier = Modifier.height(spacing.listGap))
                                        }
                                    }
                                    if (entries.size >= limit) {
                                        item(key = "load-more") {
                                            TextButton(
                                                onClick = viewModel::loadMore,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(Res.string.diary_load_more))
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (moodPoints.isEmpty()) {
                                    item(key = "mood-empty") {
                                        AppEmptyState(
                                            hint = stringResource(Res.string.diary_mood_empty_hint),
                                            actionLabel = stringResource(Res.string.diary_mood_action),
                                            onAction = {
                                                quickMood = QuickMoodTarget(
                                                    id = null,
                                                    score = null,
                                                    occurredAt = now,
                                                    timeZoneId = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                                    tagIds = emptySet()
                                                )
                                            }
                                        )
                                    }
                                } else {
                                    val groups = moodPoints.groupBy {
                                        formatDiaryDate(it.mood.occurredAt, it.mood.timeZoneId)
                                    }
                                    groups.forEach { (date, groupPoints) ->
                                        item(key = "mood-date-$date") {
                                            Text(
                                                text = date,
                                                modifier = Modifier.padding(top = 4.dp),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = appType().titleWeight),
                                                color = colors.textSecondary
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        items(groupPoints, key = { "mood-${it.mood.id}" }) { point ->
                                            MoodPointCard(
                                                point = point,
                                                onOpen = {
                                                    val diaryId = point.mood.diaryId
                                                    if (diaryId != null) {
                                                        onNavigate(Destination.DiaryDetail(diaryId))
                                                    } else {
                                                        quickMood = QuickMoodTarget(
                                                            id = point.mood.id,
                                                            score = point.mood.score,
                                                            occurredAt = point.mood.occurredAt,
                                                            timeZoneId = point.mood.timeZoneId,
                                                            tagIds = point.tags.map { it.id }.toSet()
                                                        )
                                                    }
                                                },
                                                onDelete = if (point.fromDiary) null else {
                                                    { pendingDeleteMood = point }
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(spacing.listGap))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    androidx.compose.material3.FloatingActionButton(
                        onClick = {
                            if (pane == DiaryPane.DIARY) {
                                onNavigate(Destination.DiaryEditor())
                            } else {
                                quickMood = QuickMoodTarget(
                                    id = null,
                                    score = null,
                                    occurredAt = now,
                                    timeZoneId = kotlinx.datetime.TimeZone.currentSystemDefault().id,
                                    tagIds = emptySet()
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = spacing.pageHorizontal,
                                bottom = outerPadding.calculateBottomPadding() + 16.dp
                            ),
                        containerColor = colors.primary,
                        contentColor = colors.textOnPrimary
                    ) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.add_24px),
                            contentDescription = if (pane == DiaryPane.DIARY) textNewEntry else textQuickMood
                        )
                    }
                }
            }
        }
    }

    if (showDateRange) {
        DateRangePickerModal(
            title = stringResource(Res.string.diary_filter_date),
            onDismiss = { showDateRange = false },
            onConfirm = { start: LocalDate, end: LocalDate ->
                viewModel.setDateRange(start, end)
                showDateRange = false
            }
        )
    }

    if (showTagManager) {
        DiaryTagManagerSheet(
            tags = tags,
            onDismiss = { showTagManager = false },
            onCreate = { name, color, onResult -> viewModel.createTag(name, color, onResult) },
            onRename = { id, name, onResult -> viewModel.renameTag(id, name, onResult) },
            onSetColor = viewModel::setTagColor,
            onDelete = viewModel::deleteTag
        )
    }

    quickMood?.let { target ->
        QuickMoodSheet(
            editingId = target.id,
            initialScore = target.score,
            initialOccurredAt = target.occurredAt,
            initialTimeZoneId = target.timeZoneId,
            initialTagIds = target.tagIds,
            tags = tags,
            onDismiss = { quickMood = null },
            onSave = { score, occurredAt, timeZoneId, tagIds ->
                viewModel.saveQuickMood(target.id, score, occurredAt, timeZoneId, tagIds) { ok ->
                    if (ok) {
                        ToastManager.show(textMoodSaved)
                        quickMood = null
                    } else {
                        ToastManager.show(textMoodSaveFailed)
                    }
                }
            },
            onDelete = target.id?.let {
                {
                    viewModel.deleteQuickMood(it) { ok ->
                        if (ok) {
                            ToastManager.show(textMoodDeleted)
                            quickMood = null
                        } else {
                            ToastManager.show(textMoodSaveFailed)
                        }
                    }
                }
            }
        )
    }

    pendingDeleteMood?.let { point ->
        AppAlertDialog(
            onDismissRequest = { pendingDeleteMood = null },
            title = { Text(stringResource(Res.string.diary_mood_delete_title)) },
            text = { Text(stringResource(Res.string.diary_mood_delete_text)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = textDelete,
                    onConfirm = {
                        viewModel.deleteQuickMood(point.mood.id) { ok ->
                            if (ok) ToastManager.show(textMoodDeleted)
                            else ToastManager.show(textMoodSaveFailed)
                        }
                        pendingDeleteMood = null
                    },
                    dismissText = textCancel,
                    onDismiss = { pendingDeleteMood = null },
                    danger = true
                )
            },
            dismissButton = {}
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryFilterCard(
    filter: com.shangkeschedule.data.repository.DiaryFilter,
    tags: List<com.shangkeschedule.data.db.main.DiaryTag>,
    onKeywordChange: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onSetMatchAll: (Boolean) -> Unit,
    onPickDateRange: () -> Unit,
    onClear: () -> Unit,
    clearLabel: String
) {
    val colors = appColors()
    SectionCard {
        Column(modifier = Modifier.padding(vertical = 10.dp)) {
            AppTextField(
                value = filter.keyword,
                onValueChange = onKeywordChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.diary_search_placeholder),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filter.startDate != null && filter.endDate != null,
                    onClick = onPickDateRange,
                    label = {
                        Text(
                            if (filter.startDate != null && filter.endDate != null) {
                                "${filter.startDate} ~ ${filter.endDate}"
                            } else {
                                stringResource(Res.string.diary_filter_all_dates)
                            }
                        )
                    }
                )
                if (filter.isActive) {
                    FilterChip(
                        selected = false,
                        onClick = onClear,
                        label = { Text(clearLabel) }
                    )
                }
            }
            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(Res.string.diary_filter_tags),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                DiaryTagSelector(
                    tags = tags,
                    selectedIds = filter.tagIds,
                    onToggle = onToggleTag
                )
                if (filter.tagIds.size >= 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    AppSegmentedControl(
                        options = listOf(
                            stringResource(Res.string.diary_filter_match_any),
                            stringResource(Res.string.diary_filter_match_all)
                        ),
                        selectedIndex = if (filter.matchAllTags) 1 else 0,
                        onSelect = { onSetMatchAll(it == 1) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiaryEntryCard(
    summary: DiarySummary,
    isDraft: Boolean,
    onClick: () -> Unit
) {
    val colors = appColors()
    val entry = summary.entry
    val summaryText = remember(entry.markdown) {
        entry.markdown
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(140)
    }
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDiaryTime(entry.occurredAt, entry.timeZoneId),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary
                )
                if (isDraft || entry.isDraft) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.diary_draft_badge),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.warningSoft)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warning
                    )
                }
                val moodScore = summary.mood?.score
                if (moodScore != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$moodScore · ${diaryMoodLabel(moodScore)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary
                    )
                }
            }
            if (!entry.title.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = appType().titleWeight),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (summaryText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (summary.tags.isNotEmpty() || summary.attachmentCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    summary.tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.color ?: DiaryTagPalette.first()))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                    if (summary.attachmentCount > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.image_24px),
                                contentDescription = null,
                                tint = colors.textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = summary.attachmentCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodPointCard(
    point: MoodPoint,
    onOpen: () -> Unit,
    onDelete: (() -> Unit)?
) {
    val colors = appColors()
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.primarySoft),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = point.mood.score.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.primary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatDiaryTime(point.mood.occurredAt, point.mood.timeZoneId),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = diaryMoodLabel(point.mood.score),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textPrimary
                    )
                }
                if (point.fromDiary) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = listOfNotNull(
                            stringResource(Res.string.diary_from_diary),
                            point.diaryTitle?.takeIf { it.isNotBlank() }
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (point.tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        point.tags.forEach { tag ->
                            Text(
                                text = tag.name,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(colors.inputBg)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete_24px),
                        contentDescription = stringResource(Res.string.diary_action_delete),
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
