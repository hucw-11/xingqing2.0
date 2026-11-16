package com.shangkeschedule.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.repository.TagWriteResult
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppGlassBottomSheet
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.NativeNumberPicker
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.check_24px
import shangkeschedule.shared.generated.resources.diary_action_delete
import shangkeschedule.shared.generated.resources.diary_action_save
import shangkeschedule.shared.generated.resources.diary_image_limit
import shangkeschedule.shared.generated.resources.diary_mood_1
import shangkeschedule.shared.generated.resources.diary_mood_2
import shangkeschedule.shared.generated.resources.diary_mood_3
import shangkeschedule.shared.generated.resources.diary_mood_4
import shangkeschedule.shared.generated.resources.diary_mood_5
import shangkeschedule.shared.generated.resources.diary_mood_edit_title
import shangkeschedule.shared.generated.resources.diary_mood_new_title
import shangkeschedule.shared.generated.resources.diary_mood_score_label
import shangkeschedule.shared.generated.resources.diary_mood_saved
import shangkeschedule.shared.generated.resources.diary_mood_save_failed
import shangkeschedule.shared.generated.resources.diary_occurred_at
import shangkeschedule.shared.generated.resources.diary_tag_add
import shangkeschedule.shared.generated.resources.diary_tag_color
import shangkeschedule.shared.generated.resources.diary_tag_delete
import shangkeschedule.shared.generated.resources.diary_tag_deleted
import shangkeschedule.shared.generated.resources.diary_tag_delete_text
import shangkeschedule.shared.generated.resources.diary_tag_delete_title
import shangkeschedule.shared.generated.resources.diary_tag_duplicate
import shangkeschedule.shared.generated.resources.diary_tag_empty
import shangkeschedule.shared.generated.resources.diary_tag_new_placeholder
import shangkeschedule.shared.generated.resources.diary_tag_rename
import shangkeschedule.shared.generated.resources.diary_tag_saved
import shangkeschedule.shared.generated.resources.diary_tags_title
import shangkeschedule.shared.generated.resources.diary_time_title
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.edit_24px
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.action_confirm

/** 标签可选颜色（ARGB）。每个标签只存一个颜色，UI 用它做圆点与选中底色。 */
val DiaryTagPalette: List<Int> = listOf(
    0xFFE57373.toInt(),
    0xFFF06292.toInt(),
    0xFFBA68C8.toInt(),
    0xFF7986CB.toInt(),
    0xFF4FC3F7.toInt(),
    0xFF4DB6AC.toInt(),
    0xFF81C784.toInt(),
    0xFFFFB74D.toInt(),
    0xFFA1887F.toInt()
)

@Composable
fun diaryMoodLabel(score: Int?): String = when (score) {
    1 -> stringResource(Res.string.diary_mood_1)
    2 -> stringResource(Res.string.diary_mood_2)
    3 -> stringResource(Res.string.diary_mood_3)
    4 -> stringResource(Res.string.diary_mood_4)
    5 -> stringResource(Res.string.diary_mood_5)
    else -> ""
}

fun safeTimeZone(id: String): TimeZone =
    runCatching { TimeZone.of(id) }.getOrElse { TimeZone.currentSystemDefault() }

fun localDateTimeOf(epochMillis: Long, timeZoneId: String): LocalDateTime =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(safeTimeZone(timeZoneId))

fun combineDateAndTime(
    date: LocalDate,
    hour: Int,
    minute: Int,
    timeZoneId: String
): Long = LocalDateTime(date, LocalTime(hour, minute))
    .toInstant(safeTimeZone(timeZoneId))
    .toEpochMilliseconds()

fun dateOfPickerMillis(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date

private fun Int.pad2(): String = toString().padStart(2, '0')

fun formatDiaryDate(epochMillis: Long, timeZoneId: String): String {
    val date = localDateTimeOf(epochMillis, timeZoneId).date
    return "${date.year}-${date.monthNumber.pad2()}-${date.dayOfMonth.pad2()}"
}

fun formatDiaryTime(epochMillis: Long, timeZoneId: String): String {
    val time = localDateTimeOf(epochMillis, timeZoneId)
    return "${time.hour.pad2()}:${time.minute.pad2()}"
}

@Composable
fun DiaryTagChip(
    tag: DiaryTag,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(tag.name, maxLines = 1) },
        leadingIcon = {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(tag.color ?: DiaryTagPalette.first()))
            )
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiaryTagSelector(
    tags: List<DiaryTag>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tags.forEach { tag ->
            DiaryTagChip(
                tag = tag,
                selected = tag.id in selectedIds,
                onClick = { onToggle(tag.id) }
            )
        }
    }
}

@Composable
fun MoodScoreSelector(
    score: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = appColors()
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.diary_mood_score_label),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (1..5).forEach { value ->
                val selected = score == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selected) colors.primary else colors.inputBg)
                        .clickable { onSelect(if (selected) null else value) }
                        .border(
                            width = if (selected) 0.dp else 0.5.dp,
                            color = colors.divider,
                            shape = MaterialTheme.shapes.small
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) colors.textOnPrimary else colors.textPrimary
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = diaryMoodLabel(score).ifBlank { "—" },
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 时间选择对话框：日期由 [DatePickerModal] 负责，这里只选小时/分钟。
 * 返回值为所选日期 + 新时刻组合后的 UTC 毫秒。
 */
@Composable
fun DiaryTimePickerDialog(
    initialEpochMillis: Long,
    timeZoneId: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val local = localDateTimeOf(initialEpochMillis, timeZoneId)
    var hour by remember { mutableIntStateOf(local.hour) }
    var minute by remember { mutableIntStateOf(local.minute) }
    val hours = remember { (0..23).map { it.pad2() } }
    val minutes = remember { (0..59).map { it.pad2() } }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.diary_time_title)) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NativeNumberPicker(
                    values = hours,
                    selectedValue = hour.pad2(),
                    onValueChange = { hour = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
                Text(":", style = MaterialTheme.typography.titleMedium)
                NativeNumberPicker(
                    values = minutes,
                    selectedValue = minute.pad2(),
                    onValueChange = { minute = it.toInt() },
                    modifier = Modifier.weight(1f)
                )
            }
        },
        confirmButton = {
            AppDialogActions(
                confirmText = stringResource(Res.string.action_confirm),
                onConfirm = {
                    onConfirm(combineDateAndTime(local.date, hour, minute, timeZoneId))
                    onDismiss()
                },
                dismissText = stringResource(Res.string.action_cancel),
                onDismiss = onDismiss
            )
        },
        dismissButton = {}
    )
}

/**
 * 快速心情底部面板：五档评分 + 发生时间 + 可选标签，不要求写正文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMoodSheet(
    editingId: String?,
    initialScore: Int?,
    initialOccurredAt: Long,
    initialTimeZoneId: String,
    initialTagIds: Set<String>,
    tags: List<DiaryTag>,
    onDismiss: () -> Unit,
    onSave: (score: Int, occurredAt: Long, timeZoneId: String, tagIds: Set<String>) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val colors = appColors()
    val spacing = appSpacing()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var score by remember(editingId) { mutableStateOf(initialScore) }
    var occurredAt by remember(editingId) { mutableLongStateOf(initialOccurredAt) }
    var timeZoneId by remember(editingId) { mutableStateOf(initialTimeZoneId) }
    var selectedTagIds by remember(editingId) { mutableStateOf(initialTagIds) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val deleteText = stringResource(Res.string.diary_action_delete)
    val saveText = stringResource(Res.string.diary_action_save)
    val cancelText = stringResource(Res.string.action_cancel)

    AppGlassBottomSheet(
        hazeState = null,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Text(
                text = stringResource(
                    if (editingId == null) Res.string.diary_mood_new_title
                    else Res.string.diary_mood_edit_title
                ),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = appType().titleWeight),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.cardGap))
            MoodScoreSelector(score = score, onSelect = { score = it })
            Spacer(modifier = Modifier.height(spacing.cardGap))
            Text(
                text = stringResource(Res.string.diary_occurred_at),
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = false,
                    onClick = { showDatePicker = true },
                    label = { Text(formatDiaryDate(occurredAt, timeZoneId)) }
                )
                FilterChip(
                    selected = false,
                    onClick = { showTimePicker = true },
                    label = { Text(formatDiaryTime(occurredAt, timeZoneId)) }
                )
            }
            Spacer(modifier = Modifier.height(spacing.cardGap))
            DiaryTagSelector(
                tags = tags,
                selectedIds = selectedTagIds,
                onToggle = { id ->
                    selectedTagIds = if (id in selectedTagIds) selectedTagIds - id else selectedTagIds + id
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
            AppDialogActions(
                confirmText = saveText,
                confirmEnabled = score != null,
                onConfirm = {
                    val chosen = score ?: return@AppDialogActions
                    onSave(chosen, occurredAt, timeZoneId, selectedTagIds)
                },
                dismissText = cancelText,
                onDismiss = onDismiss
            )
            if (onDelete != null) {
                Spacer(modifier = Modifier.height(10.dp))
                AppDialogActions(
                    confirmText = deleteText,
                    onConfirm = onDelete,
                    danger = true
                )
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            onDateSelected = { millis ->
                if (millis != null) {
                    val current = localDateTimeOf(occurredAt, timeZoneId)
                    occurredAt = combineDateAndTime(
                        dateOfPickerMillis(millis),
                        current.hour,
                        current.minute,
                        timeZoneId
                    )
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        DiaryTimePickerDialog(
            initialEpochMillis = occurredAt,
            timeZoneId = timeZoneId,
            onDismiss = { showTimePicker = false },
            onConfirm = { occurredAt = it }
        )
    }
}

/**
 * 标签管理面板：新建、改名、配色、删除。
 *
 * 删除只解除关联；同名冲突与空名通过 Toast 明确提示，不静默合并。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryTagManagerSheet(
    tags: List<DiaryTag>,
    onDismiss: () -> Unit,
    onCreate: (name: String, color: Int?, onResult: (TagWriteResult) -> Unit) -> Unit,
    onRename: (id: String, name: String, onResult: (TagWriteResult) -> Unit) -> Unit,
    onSetColor: (id: String, color: Int?) -> Unit,
    onDelete: (id: String) -> Unit
) {
    val colors = appColors()
    val spacing = appSpacing()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf(DiaryTagPalette.first()) }
    var renameTarget by remember { mutableStateOf<DiaryTag?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<DiaryTag?>(null) }
    var colorTarget by remember { mutableStateOf<DiaryTag?>(null) }

    val duplicateText = stringResource(Res.string.diary_tag_duplicate)
    val emptyText = stringResource(Res.string.diary_tag_empty)
    val savedText = stringResource(Res.string.diary_tag_saved)
    val deletedText = stringResource(Res.string.diary_tag_deleted)
    val failedText = stringResource(Res.string.diary_mood_save_failed)
    val cancelText = stringResource(Res.string.action_cancel)

    fun handleResult(result: TagWriteResult, onSuccess: () -> Unit) {
        when (result) {
            is TagWriteResult.Success -> {
                ToastManager.show(savedText)
                onSuccess()
            }

            TagWriteResult.Duplicate -> ToastManager.show(duplicateText)
            TagWriteResult.EmptyName -> ToastManager.show(emptyText)
            TagWriteResult.Failed -> ToastManager.show(failedText)
        }
    }

    AppGlassBottomSheet(
        hazeState = null,
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
        ) {
            Text(
                text = stringResource(Res.string.diary_tags_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = appType().titleWeight),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.cardGap))

            AppTextField(
                value = newName,
                onValueChange = { newName = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.diary_tag_new_placeholder),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.diary_tag_color),
                style = MaterialTheme.typography.labelMedium,
                color = colors.textSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiaryTagPalette.forEach { argb ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .clickable { newColor = argb },
                        contentAlignment = Alignment.Center
                    ) {
                        if (newColor == argb) {
                            Icon(
                                imageVector = vectorResource(Res.drawable.check_24px),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            AppDialogActions(
                confirmText = stringResource(Res.string.diary_tag_add),
                confirmEnabled = newName.isNotBlank(),
                onConfirm = {
                    onCreate(newName, newColor) { result ->
                        handleResult(result) { newName = "" }
                    }
                },
                dismissText = cancelText,
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(spacing.cardGap))
            tags.forEach { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color(tag.color ?: DiaryTagPalette.first()))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = tag.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.textPrimary
                    )
                    IconButton(onClick = {
                        renameTarget = tag
                        renameText = tag.name
                    }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.edit_24px),
                            contentDescription = stringResource(Res.string.diary_tag_rename),
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = { colorTarget = tag }) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(tag.color ?: DiaryTagPalette.first()))
                                .border(1.dp, colors.divider, CircleShape)
                        )
                    }
                    IconButton(onClick = { deleteTarget = tag }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.delete_24px),
                            contentDescription = stringResource(Res.string.diary_tag_delete),
                            tint = colors.danger,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { tag ->
        AppAlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(Res.string.diary_tag_rename)) },
            text = {
                AppTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.action_confirm),
                    onConfirm = {
                        onRename(tag.id, renameText) { result ->
                            handleResult(result) { renameTarget = null }
                        }
                    },
                    dismissText = cancelText,
                    onDismiss = { renameTarget = null }
                )
            },
            dismissButton = {}
        )
    }

    colorTarget?.let { tag ->
        AppAlertDialog(
            onDismissRequest = { colorTarget = null },
            title = { Text(stringResource(Res.string.diary_tag_color)) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DiaryTagPalette.forEach { argb ->
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(argb))
                                .clickable {
                                    onSetColor(tag.id, argb)
                                    colorTarget = null
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (tag.color == argb) {
                                Icon(
                                    imageVector = vectorResource(Res.drawable.check_24px),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                AppDialogActions(
                    confirmText = cancelText,
                    onConfirm = { colorTarget = null }
                )
            }
        )
    }

    deleteTarget?.let { tag ->
        AppAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(Res.string.diary_tag_delete_title)) },
            text = { Text(stringResource(Res.string.diary_tag_delete_text)) },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.diary_tag_delete),
                    onConfirm = {
                        onDelete(tag.id)
                        ToastManager.show(deletedText)
                        deleteTarget = null
                    },
                    dismissText = cancelText,
                    onDismiss = { deleteTarget = null },
                    danger = true
                )
            },
            dismissButton = {}
        )
    }
}

/** 附件数量上限提示（编辑器与测试共用同一文案）。 */
@Composable
fun rememberAttachmentLimitText(): String = stringResource(Res.string.diary_image_limit)
