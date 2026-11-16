package com.shangkeschedule.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.DIARY_MARKDOWN_SAFE_LIMIT
import com.shangkeschedule.data.db.main.DIARY_TITLE_MAX_LENGTH
import com.shangkeschedule.data.db.main.DiaryAttachment
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.repository.DiaryRepository
import com.shangkeschedule.data.repository.DiarySaveRequest
import com.shangkeschedule.data.repository.DiaryTagRepository
import com.shangkeschedule.tool.AppLog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

/** 日记编辑页状态。 */
data class DiaryEditorState(
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
    val entryId: String = "",
    val title: String = "",
    val markdown: String = "",
    val occurredAt: Long = 0L,
    val timeZoneId: String = TimeZone.currentSystemDefault().id,
    val moodScore: Int? = null,
    val tagIds: Set<String> = emptySet(),
    val attachments: List<DiaryAttachment> = emptyList(),
    val attachmentPaths: Map<String, String> = emptyMap(),
    val isDraft: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val lastSavedAt: Long? = null
) {
    /** 正文、图片、心情至少有一项才能正式保存；仅有标题或标签不能发布。 */
    val canPublish: Boolean
        get() = markdown.isNotBlank() || attachments.isNotEmpty() || moodScore != null
}

/**
 * 日记编辑器。
 *
 * 并发与持久化约束：
 * - 每次输入只递增版本号；输入暂停后自动保存，永远保存**当前最新**状态；
 * - 自动保存与手动发布共享同一个 [Mutex]，不会出现旧草稿覆盖新输入；
 * - 手动发布先置位 [publishGate]，迟到的自动保存不会再把它改回草稿；
 * - 新建页在进入时先落草稿身份，连续自动保存始终更新同一行；
 * - 退出时全空草稿物理清理，非空草稿尽量 flush。
 */
@OptIn(FlowPreview::class)
@KoinViewModel
class DiaryEditorViewModel(
    private val diaryRepository: DiaryRepository,
    private val tagRepository: DiaryTagRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DiaryEditorState())
    val state: StateFlow<DiaryEditorState> = _state.asStateFlow()

    val tags: StateFlow<List<DiaryTag>> = tagRepository.observeTags()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val editTicks = MutableStateFlow(0L)
    private val mutex = Mutex()
    private var initialized = false
    /** 发布成功或已进入退出流程后，迟到的自动保存不再写库。 */
    private var publishGate = false
    /** 正在尝试发布：期间暂停自动保存，失败后恢复正常。 */
    private var publishing = false
    private var exiting = false
    private var createdAt = 0L

    init {
        viewModelScope.launch {
            editTicks.debounce(AUTOSAVE_DEBOUNCE_MS).collect { version ->
                if (version > 0L) persistDraft()
            }
        }
    }

    fun open(entryId: String?) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            if (entryId != null) {
                val detail = diaryRepository.getDetail(entryId)
                if (detail == null || detail.entry.deletedAt != null) {
                    _state.update { it.copy(loading = false, loadFailed = true) }
                    return@launch
                }
                createdAt = detail.entry.createdAt
                _state.value = DiaryEditorState(
                    loading = false,
                    entryId = detail.entry.id,
                    title = detail.entry.title.orEmpty(),
                    markdown = detail.entry.markdown,
                    occurredAt = detail.entry.occurredAt,
                    timeZoneId = detail.entry.timeZoneId,
                    moodScore = detail.mood?.score,
                    tagIds = detail.tags.map { it.id }.toSet(),
                    attachments = detail.attachments,
                    attachmentPaths = detail.attachmentPaths,
                    isDraft = detail.entry.isDraft,
                    lastSavedAt = detail.entry.updatedAt
                )
            } else {
                val now = Clock.System.now().toEpochMilliseconds()
                val timeZone = TimeZone.currentSystemDefault().id
                val draft = diaryRepository.createDraft(now, timeZone)
                createdAt = draft.createdAt
                _state.value = DiaryEditorState(
                    loading = false,
                    entryId = draft.id,
                    occurredAt = now,
                    timeZoneId = timeZone,
                    isDraft = true
                )
            }
        }
    }

    fun onTitleChange(value: String) {
        if (value.length > DIARY_TITLE_MAX_LENGTH) return
        update { it.copy(title = value) }
    }

    fun onMarkdownChange(value: String) {
        if (value.length > DIARY_MARKDOWN_SAFE_LIMIT) return
        update { it.copy(markdown = value) }
    }

    fun setOccurredAt(epochMillis: Long, timeZoneId: String = _state.value.timeZoneId) {
        update { it.copy(occurredAt = epochMillis, timeZoneId = timeZoneId) }
    }

    fun setMoodScore(score: Int?) {
        update { it.copy(moodScore = score?.coerceIn(1, 5)) }
    }

    fun createTag(name: String, onResult: (com.shangkeschedule.data.repository.TagWriteResult) -> Unit) {
        viewModelScope.launch {
            onResult(tagRepository.createTag(name, com.shangkeschedule.ui.diary.DiaryTagPalette.first()))
        }
    }

    fun toggleTag(tagId: String) {
        update {
            val next = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId
            it.copy(tagIds = next)
        }
    }

    /** 保存新选图片；返回附件（含可展示的本地路径），失败返回 null。 */
    suspend fun addImage(bytes: ByteArray): DiaryAttachment? {
        val entryId = _state.value.entryId
        if (entryId.isEmpty()) return null
        val attachment = diaryRepository.addAttachment(entryId, bytes) ?: return null
        _state.update {
            it.copy(
                attachments = it.attachments + attachment,
                attachmentPaths = diaryRepository.resolveAttachmentPath(attachment.relativePath)
                    ?.let { path -> it.attachmentPaths + (attachment.id to path) }
                    ?: it.attachmentPaths
            )
        }
        bumpVersion()
        return attachment
    }

    fun removeAttachment(attachmentId: String) {
        update { it.copy(attachments = it.attachments.filterNot { item -> item.id == attachmentId }) }
    }

    /**
     * 手动保存。[publish] = true 为正式发布，必须满足 [DiaryEditorState.canPublish]。
     */
    fun save(publish: Boolean, onResult: (Boolean) -> Unit) {
        if (_state.value.isSaving) {
            onResult(false)
            return
        }
        if (publish && !_state.value.canPublish) {
            onResult(false)
            return
        }
        if (publish) publishing = true
        _state.update { it.copy(isSaving = true, saveFailed = false) }
        viewModelScope.launch {
            val ok = mutex.withLock {
                val snapshot = _state.value
                if (snapshot.loading || snapshot.entryId.isEmpty()) return@withLock false
                if (publishGate && !publish) return@withLock false
                val result = diaryRepository.save(snapshot.toRequest(isDraft = !publish))
                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            isDraft = !publish,
                            lastSavedAt = Clock.System.now().toEpochMilliseconds(),
                            saveFailed = false
                        )
                    }
                } else {
                    _state.update { it.copy(saveFailed = true) }
                }
                result.isSuccess
            }
            // 发布失败时解除暂停，用户可继续编辑；成功后关闸，迟到草稿不会回退状态。
            if (publish) {
                publishing = false
                if (ok) publishGate = true
            }
            _state.update { it.copy(isSaving = false) }
            onResult(ok)
        }
    }

    /**
     * 离开编辑页前保存未发布内容。
     *
     * 全空新建页直接清理草稿身份；非空草稿写库失败时返回 false，由界面提示并留在页面，
     * 不静默丢弃输入。
     */
    fun flushAndExit(onResult: (Boolean) -> Unit) {
        exiting = true
        viewModelScope.launch {
            val ok = mutex.withLock {
                val snapshot = _state.value
                if (snapshot.loading || snapshot.entryId.isEmpty()) return@withLock true
                if (isBlank(snapshot)) {
                    diaryRepository.purgeIfEmpty(snapshot.entryId)
                    true
                } else if (!snapshot.isDraft) {
                    // 已发布记录：正文编辑仍要落盘，保持发布状态
                    diaryRepository.save(snapshot.toRequest(isDraft = false)).isSuccess
                } else {
                    diaryRepository.save(snapshot.toRequest(isDraft = true)).isSuccess
                }
            }
            onResult(ok)
        }
    }

    fun deleteEntry(onResult: (Boolean) -> Unit) {
        val entryId = _state.value.entryId
        if (entryId.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching { diaryRepository.deleteEntry(entryId) }.isSuccess
            if (ok) publishGate = true
            onResult(ok)
        }
    }

    private fun update(transform: (DiaryEditorState) -> DiaryEditorState) {
        if (publishGate || exiting) return
        _state.update(transform)
        bumpVersion()
    }

    private fun bumpVersion() {
        editTicks.value = editTicks.value + 1
    }

    private suspend fun persistDraft() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.entryId.isEmpty()) return
        if (publishGate || publishing || exiting) return
        if (isBlank(snapshot)) return
        runCatching {
            mutex.withLock {
                val latest = _state.value
                if (publishGate || publishing || exiting || latest.loading) return@withLock
                val result = diaryRepository.save(latest.toRequest(isDraft = latest.isDraft))
                if (result.isSuccess) {
                    _state.update {
                        it.copy(
                            lastSavedAt = Clock.System.now().toEpochMilliseconds(),
                            saveFailed = false
                        )
                    }
                } else {
                    _state.update { it.copy(saveFailed = true) }
                }
            }
        }.onFailure { error ->
            AppLog.w(TAG, "自动保存草稿失败", error)
            _state.update { it.copy(saveFailed = true) }
        }
    }

    /** 仅标签不构成一条记录，也不进入隐藏草稿；与发布校验口径一致。 */
    private fun isBlank(state: DiaryEditorState): Boolean =
        state.title.isBlank() &&
            state.markdown.isBlank() &&
            state.moodScore == null &&
            state.attachments.isEmpty()

    private fun DiaryEditorState.toRequest(isDraft: Boolean): DiarySaveRequest = DiarySaveRequest(
        entryId = entryId,
        title = title,
        markdown = markdown,
        occurredAt = occurredAt,
        timeZoneId = timeZoneId,
        isDraft = isDraft,
        moodScore = moodScore,
        tagIds = tagIds,
        keptAttachmentIds = attachments.map { it.id }.toSet(),
        createdAt = createdAt
    )

    private companion object {
        const val TAG = "DiaryEditorViewModel"
        const val AUTOSAVE_DEBOUNCE_MS = 700L
    }
}
