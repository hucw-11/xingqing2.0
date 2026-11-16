package com.shangkeschedule.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import coil3.compose.AsyncImage
import com.shangkeschedule.data.db.main.DIARY_ATTACHMENT_MAX_BYTES
import com.shangkeschedule.data.db.main.DIARY_ATTACHMENTS_MAX_COUNT
import com.shangkeschedule.data.db.main.DIARY_MARKDOWN_SAFE_LIMIT
import com.shangkeschedule.data.repository.TagWriteResult
import com.shangkeschedule.tool.FileManagerCallbacks
import com.shangkeschedule.tool.rememberFileManager
import com.shangkeschedule.ui.components.AppAlertDialog
import com.shangkeschedule.ui.components.AppDialogActions
import com.shangkeschedule.ui.components.AppEmptyState
import com.shangkeschedule.ui.components.AppLoading
import com.shangkeschedule.ui.components.AppSegmentedControl
import com.shangkeschedule.ui.components.AppTextField
import com.shangkeschedule.ui.components.AppTopAppBar
import com.shangkeschedule.ui.components.DatePickerModal
import com.shangkeschedule.ui.components.ToastManager
import com.shangkeschedule.ui.components.cropImageBitmapNative
import com.shangkeschedule.ui.theme.appColors
import com.shangkeschedule.ui.theme.appSpacing
import com.shangkeschedule.ui.theme.appType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import shangkeschedule.shared.generated.resources.Res
import shangkeschedule.shared.generated.resources.action_cancel
import shangkeschedule.shared.generated.resources.add_24px
import shangkeschedule.shared.generated.resources.arrow_back_24px
import shangkeschedule.shared.generated.resources.delete_24px
import shangkeschedule.shared.generated.resources.diary_action_close
import shangkeschedule.shared.generated.resources.diary_action_delete
import shangkeschedule.shared.generated.resources.diary_action_edit_mode
import shangkeschedule.shared.generated.resources.diary_action_preview
import shangkeschedule.shared.generated.resources.diary_action_save
import shangkeschedule.shared.generated.resources.diary_auto_saved_at
import shangkeschedule.shared.generated.resources.diary_delete_failed
import shangkeschedule.shared.generated.resources.diary_delete_text
import shangkeschedule.shared.generated.resources.diary_delete_title
import shangkeschedule.shared.generated.resources.diary_deleted
import shangkeschedule.shared.generated.resources.diary_draft_saved_at
import shangkeschedule.shared.generated.resources.diary_drafts
import shangkeschedule.shared.generated.resources.diary_editor_edit_title
import shangkeschedule.shared.generated.resources.diary_editor_new_title
import shangkeschedule.shared.generated.resources.diary_image_failed
import shangkeschedule.shared.generated.resources.diary_image_limit
import shangkeschedule.shared.generated.resources.diary_image_missing
import shangkeschedule.shared.generated.resources.diary_image_too_large
import shangkeschedule.shared.generated.resources.diary_markdown_bold
import shangkeschedule.shared.generated.resources.diary_markdown_code
import shangkeschedule.shared.generated.resources.diary_markdown_heading
import shangkeschedule.shared.generated.resources.diary_markdown_hint
import shangkeschedule.shared.generated.resources.diary_markdown_image
import shangkeschedule.shared.generated.resources.diary_markdown_italic
import shangkeschedule.shared.generated.resources.diary_markdown_link
import shangkeschedule.shared.generated.resources.diary_markdown_list
import shangkeschedule.shared.generated.resources.diary_markdown_quote
import shangkeschedule.shared.generated.resources.diary_publish_needs_content
import shangkeschedule.shared.generated.resources.diary_save_failed
import shangkeschedule.shared.generated.resources.diary_saved
import shangkeschedule.shared.generated.resources.diary_tag_add
import shangkeschedule.shared.generated.resources.diary_tag_duplicate
import shangkeschedule.shared.generated.resources.diary_tag_empty
import shangkeschedule.shared.generated.resources.diary_tag_new_placeholder
import shangkeschedule.shared.generated.resources.diary_tag_saved
import shangkeschedule.shared.generated.resources.diary_title_hint
import shangkeschedule.shared.generated.resources.diary_load_failed

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiaryEditorScreen(
    entryId: String?,
    onBack: () -> Unit,
    viewModel: DiaryEditorViewModel = koinViewModel()
) {
    LaunchedEffect(entryId) { viewModel.open(entryId) }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val colors = appColors()
    val spacing = appSpacing()
    val scope = rememberCoroutineScope()

    var editorValue by remember { mutableStateOf(TextFieldValue("")) }
    var editorInitialized by remember(state.entryId) { mutableStateOf(false) }
    var previewMode by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showNewTag by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var imageLoading by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    LaunchedEffect(state.loading, state.entryId) {
        if (!state.loading && !state.loadFailed && !editorInitialized) {
            editorValue = TextFieldValue(state.markdown)
            editorInitialized = true
        }
    }

    val saveFailedText = stringResource(Res.string.diary_save_failed)
    val savedText = stringResource(Res.string.diary_saved)
    val needsContentText = stringResource(Res.string.diary_publish_needs_content)
    val imageFailedText = stringResource(Res.string.diary_image_failed)
    val tagSavedText = stringResource(Res.string.diary_tag_saved)
    val tagDuplicateText = stringResource(Res.string.diary_tag_duplicate)
    val tagEmptyText = stringResource(Res.string.diary_tag_empty)
    val deletedText = stringResource(Res.string.diary_deleted)
    val deleteFailedText = stringResource(Res.string.diary_delete_failed)
    val imageAltText = stringResource(Res.string.diary_markdown_image)
    val imageLimitText = stringResource(Res.string.diary_image_limit)
    val imageTooLargeText = stringResource(Res.string.diary_image_too_large)

    fun applyEditorValue(value: TextFieldValue) {
        if (value.text.length > DIARY_MARKDOWN_SAFE_LIMIT) return
        editorValue = value
        viewModel.onMarkdownChange(value.text)
    }

    fun requestExit() {
        if (exiting) return
        exiting = true
        viewModel.flushAndExit { ok ->
            exiting = false
            if (ok) onBack() else ToastManager.show(saveFailedText)
        }
    }

    val backState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = backState,
        isBackEnabled = !exiting,
        onBackCompleted = { requestExit() }
    )

    val fileManager = rememberFileManager(
        FileManagerCallbacks(
            onImagePicked = { bitmap ->
                if (bitmap != null && !imageLoading && !state.loading) {
                    scope.launch {
                        imageLoading = true
                        try {
                            if (state.attachments.size >= DIARY_ATTACHMENTS_MAX_COUNT) {
                                ToastManager.show(imageLimitText)
                                return@launch
                            }
                            val bytes = runCatching {
                                cropImageBitmapNative(bitmap, 0, 0, bitmap.width, bitmap.height)
                            }.getOrNull()
                            if (bytes == null || bytes.isEmpty()) {
                                ToastManager.show(imageFailedText)
                            } else if (bytes.size.toLong() > DIARY_ATTACHMENT_MAX_BYTES) {
                                ToastManager.show(imageTooLargeText)
                            } else {
                                val attachment = viewModel.addImage(bytes)
                                if (attachment == null) {
                                    ToastManager.show(imageFailedText)
                                } else {
                                    applyEditorValue(
                                        insertImageReference(editorValue, attachment.id, imageAltText)
                                    )
                                }
                            }
                        } finally {
                            imageLoading = false
                        }
                    }
                }
            }
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (entryId == null) Res.string.diary_editor_new_title
                            else Res.string.diary_editor_edit_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { requestExit() }) {
                        Icon(
                            imageVector = vectorResource(Res.drawable.arrow_back_24px),
                            contentDescription = stringResource(Res.string.diary_action_close)
                        )
                    }
                },
                actions = {
                    if (!state.loading && !state.loadFailed && state.entryId.isNotEmpty()) {
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
            state.loadFailed -> AppEmptyState(
                hint = stringResource(Res.string.diary_load_failed),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                fillScreen = true
            )

            state.loading -> AppLoading(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(
                        start = spacing.pageHorizontal,
                        end = spacing.pageHorizontal,
                        top = spacing.cardGap,
                        bottom = spacing.contentBottom
                    )
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { showDatePicker = true },
                        label = { Text(formatDiaryDate(state.occurredAt, state.timeZoneId)) }
                    )
                    FilterChip(
                        selected = false,
                        onClick = { showTimePicker = true },
                        label = { Text(formatDiaryTime(state.occurredAt, state.timeZoneId)) }
                    )
                    if (state.isDraft) {
                        Text(
                            text = stringResource(Res.string.diary_drafts),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.warning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                AppTextField(
                    value = state.title,
                    onValueChange = viewModel::onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.diary_title_hint),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))
                MoodScoreSelector(score = state.moodScore, onSelect = viewModel::setMoodScore)

                Spacer(modifier = Modifier.height(14.dp))
                TagEditorRow(
                    tags = tags,
                    selectedIds = state.tagIds,
                    onToggle = viewModel::toggleTag,
                    onCreateTag = { showNewTag = true }
                )

                Spacer(modifier = Modifier.height(16.dp))
                AppSegmentedControl(
                    options = listOf(
                        stringResource(Res.string.diary_action_edit_mode),
                        stringResource(Res.string.diary_action_preview)
                    ),
                    selectedIndex = if (previewMode) 1 else 0,
                    onSelect = { previewMode = it == 1 }
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (previewMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBg)
                            .border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (state.markdown.isBlank()) {
                            Text(
                                text = stringResource(Res.string.diary_markdown_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.textSecondary
                            )
                        } else {
                            DiaryMarkdown(
                                markdown = state.markdown,
                                attachmentPaths = state.attachmentPaths
                            )
                        }
                    }
                } else {
                    MarkdownToolbar(
                        onBold = { applyEditorValue(toggleInlineWrap(editorValue, "**")) },
                        onItalic = { applyEditorValue(toggleInlineWrap(editorValue, "*")) },
                        onHeading = { applyEditorValue(toggleLinePrefix(editorValue, "## ")) },
                        onList = { applyEditorValue(toggleLinePrefix(editorValue, "- ")) },
                        onQuote = { applyEditorValue(toggleLinePrefix(editorValue, "> ")) },
                        onCode = { applyEditorValue(insertCodeBlock(editorValue)) },
                        onLink = { applyEditorValue(insertLink(editorValue)) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.cardBg)
                            .border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        BasicTextField(
                            value = editorValue,
                            onValueChange = ::applyEditorValue,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 260.dp, max = 520.dp),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                            cursorBrush = SolidColor(colors.primary),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (editorValue.text.isEmpty()) {
                                        Text(
                                            text = stringResource(Res.string.diary_markdown_hint),
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = colors.textSecondary
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.diary_markdown_image),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                AttachmentStrip(
                    state = state,
                    imageLoading = imageLoading,
                    onAdd = { if (!imageLoading && !state.isSaving) fileManager.pickImage() },
                    onRemove = { attachmentId ->
                        viewModel.removeAttachment(attachmentId)
                        val newText = removeImageReference(editorValue.text, attachmentId)
                        val cursor = editorValue.selection.start.coerceIn(0, newText.length)
                        editorValue = TextFieldValue(newText, TextRange(cursor))
                        viewModel.onMarkdownChange(newText)
                    }
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(Res.string.diary_image_limit),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(20.dp))
                AppDialogActions(
                    confirmText = stringResource(Res.string.diary_action_save),
                    confirmEnabled = state.canPublish && !state.isSaving && !imageLoading,
                    onConfirm = {
                        if (!state.canPublish) {
                            ToastManager.show(needsContentText)
                            return@AppDialogActions
                        }
                        viewModel.save(publish = true) { ok ->
                            if (ok) {
                                ToastManager.show(savedText)
                                exiting = true
                                onBack()
                            } else {
                                ToastManager.show(saveFailedText)
                            }
                        }
                    },
                    dismissText = stringResource(Res.string.diary_action_close),
                    onDismiss = { requestExit() }
                )
                if (!state.canPublish) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(Res.string.diary_publish_needs_content),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                val lastSavedAt = state.lastSavedAt
                if (lastSavedAt != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val timeText = formatDiaryTime(lastSavedAt, state.timeZoneId)
                    Text(
                        text = stringResource(
                            if (state.isDraft) Res.string.diary_draft_saved_at
                            else Res.string.diary_auto_saved_at,
                            timeText
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                if (state.saveFailed) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.diary_save_failed),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.danger
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        DatePickerModal(
            onDateSelected = { millis ->
                if (millis != null) {
                    val current = localDateTimeOf(state.occurredAt, state.timeZoneId)
                    viewModel.setOccurredAt(
                        combineDateAndTime(
                            dateOfPickerMillis(millis),
                            current.hour,
                            current.minute,
                            state.timeZoneId
                        )
                    )
                }
            },
            onDismiss = { showDatePicker = false }
        )
    }
    if (showTimePicker) {
        DiaryTimePickerDialog(
            initialEpochMillis = state.occurredAt,
            timeZoneId = state.timeZoneId,
            onDismiss = { showTimePicker = false },
            onConfirm = { viewModel.setOccurredAt(it) }
        )
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
                        viewModel.deleteEntry { ok ->
                            if (ok) {
                                ToastManager.show(deletedText)
                                exiting = true
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

    if (showNewTag) {
        AppAlertDialog(
            onDismissRequest = { showNewTag = false },
            title = { Text(stringResource(Res.string.diary_tag_add)) },
            text = {
                AppTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(Res.string.diary_tag_new_placeholder),
                    singleLine = true
                )
            },
            confirmButton = {
                AppDialogActions(
                    confirmText = stringResource(Res.string.diary_tag_add),
                    onConfirm = {
                        viewModel.createTag(newTagName) { result ->
                            when (result) {
                                is TagWriteResult.Success -> {
                                    viewModel.toggleTag(result.tag.id)
                                    ToastManager.show(tagSavedText)
                                    showNewTag = false
                                    newTagName = ""
                                }

                                TagWriteResult.Duplicate -> ToastManager.show(tagDuplicateText)
                                TagWriteResult.EmptyName -> ToastManager.show(tagEmptyText)
                                TagWriteResult.Failed -> ToastManager.show(saveFailedText)
                            }
                        }
                    },
                    dismissText = stringResource(Res.string.action_cancel),
                    onDismiss = { showNewTag = false }
                )
            },
            dismissButton = {}
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditorRow(
    tags: List<com.shangkeschedule.data.db.main.DiaryTag>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onCreateTag: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.diary_tag_add),
            style = MaterialTheme.typography.labelLarge,
            color = appColors().textSecondary
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
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
            FilterChip(
                selected = false,
                onClick = onCreateTag,
                label = { Text("+") }
            )
        }
    }
}

@Composable
private fun MarkdownToolbar(
    onBold: () -> Unit,
    onItalic: () -> Unit,
    onHeading: () -> Unit,
    onList: () -> Unit,
    onQuote: () -> Unit,
    onCode: () -> Unit,
    onLink: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val items = listOf(
            "B" to onBold,
            "I" to onItalic,
            "H" to onHeading,
            "•" to onList,
            ">" to onQuote,
            "</>" to onCode,
            "[]" to onLink
        )
        items.forEach { (label, action) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(appColors().inputBg)
                    .clickable(onClick = action)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (label == "B") FontWeight.Bold else FontWeight.Medium
                    ),
                    color = appColors().textPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttachmentStrip(
    state: DiaryEditorState,
    imageLoading: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit
) {
    val colors = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        state.attachments.forEach { attachment ->
            Box(modifier = Modifier.size(72.dp)) {
                val path = state.attachmentPaths[attachment.id]
                if (path == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.inputBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.diary_image_missing),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary.copy(alpha = 0.55f))
                        .clickable { onRemove(attachment.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = vectorResource(Res.drawable.delete_24px),
                        contentDescription = null,
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.inputBg)
                .clickable(enabled = !imageLoading, onClick = onAdd),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = vectorResource(Res.drawable.add_24px),
                    contentDescription = stringResource(Res.string.diary_markdown_image),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp)
                )
                if (imageLoading) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}
