package com.shangkeschedule.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.db.main.DIARY_PAGE_SIZE
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.repository.DiaryFilter
import com.shangkeschedule.data.repository.DiaryRepository
import com.shangkeschedule.data.repository.DiarySummary
import com.shangkeschedule.data.repository.DiaryTagRepository
import com.shangkeschedule.data.repository.MoodPoint
import com.shangkeschedule.data.repository.MoodRepository
import com.shangkeschedule.data.repository.TagWriteResult
import com.shangkeschedule.tool.AppLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.KoinViewModel

/** 日记主页面当前显示日记列表还是心情列表。 */
enum class DiaryPane { DIARY, MOOD }

/**
 * 日记主页状态：草稿、正式记录、心情时间线、标签与筛选。
 *
 * 列表只订阅数据库行（不含图片字节）；图片缩略图按附件路径懒加载。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class DiaryViewModel(
    private val diaryRepository: DiaryRepository,
    private val moodRepository: MoodRepository,
    private val tagRepository: DiaryTagRepository
) : ViewModel() {

    private val _pane = MutableStateFlow(DiaryPane.DIARY)
    val pane: StateFlow<DiaryPane> = _pane.asStateFlow()

    private val _filter = MutableStateFlow(DiaryFilter())
    val filter: StateFlow<DiaryFilter> = _filter.asStateFlow()

    private val _limit = MutableStateFlow(DIARY_PAGE_SIZE)
    val limit: StateFlow<Int> = _limit.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val drafts: StateFlow<List<DiarySummary>> = diaryRepository.observeDrafts()
        .catch { e -> reportError(e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries: StateFlow<List<DiarySummary>> =
        combine(_filter, _limit) { filter, limit -> filter to limit }
            .flatMapLatest { (filter, limit) ->
                diaryRepository.observeEntries(filter, limit = limit)
            }
            .catch { e -> reportError(e); emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val moodPoints: StateFlow<List<MoodPoint>> = combine(_filter, _pane) { filter, pane ->
        filter to pane
    }.flatMapLatest { (filter, pane) ->
        if (pane != DiaryPane.MOOD) {
            kotlinx.coroutines.flow.flowOf(emptyList())
        } else {
            moodRepository.observeTimeline(
                startDate = filter.startDate,
                endDate = filter.endDate,
                tagIds = filter.tagIds,
                matchAllTags = filter.matchAllTags
            )
        }
    }.catch { e -> reportError(e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tags: StateFlow<List<DiaryTag>> = tagRepository.observeTags()
        .catch { e -> reportError(e); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch { diaryRepository.pruneStaleEmptyDrafts() }
    }

    fun setPane(value: DiaryPane) {
        _pane.value = value
    }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        _limit.value = DIARY_PAGE_SIZE
        _filter.update { it.copy(startDate = start, endDate = end) }
    }

    fun setKeyword(value: String) {
        _limit.value = DIARY_PAGE_SIZE
        _filter.update { it.copy(keyword = value) }
    }

    fun toggleTag(tagId: String) {
        _limit.value = DIARY_PAGE_SIZE
        _filter.update {
            val next = if (tagId in it.tagIds) it.tagIds - tagId else it.tagIds + tagId
            it.copy(tagIds = next)
        }
    }

    fun setMatchAllTags(matchAll: Boolean) {
        _limit.value = DIARY_PAGE_SIZE
        _filter.update { it.copy(matchAllTags = matchAll) }
    }

    fun clearFilters() {
        _limit.value = DIARY_PAGE_SIZE
        _filter.value = DiaryFilter()
    }

    fun loadMore() {
        _limit.update { it + DIARY_PAGE_SIZE }
    }

    fun retryLoad() {
        _error.value = null
    }

    fun createTag(name: String, color: Int?, onResult: (TagWriteResult) -> Unit) {
        viewModelScope.launch { onResult(tagRepository.createTag(name, color)) }
    }

    fun renameTag(id: String, name: String, onResult: (TagWriteResult) -> Unit) {
        viewModelScope.launch { onResult(tagRepository.renameTag(id, name)) }
    }

    fun setTagColor(id: String, color: Int?) {
        viewModelScope.launch { tagRepository.setColor(id, color) }
    }

    fun deleteTag(id: String) {
        viewModelScope.launch { tagRepository.deleteTag(id) }
    }

    fun saveQuickMood(
        moodId: String?,
        score: Int,
        occurredAt: Long,
        timeZoneId: String,
        tagIds: Set<String>,
        onResult: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            val result = moodRepository.saveQuickMood(moodId, score, occurredAt, timeZoneId, tagIds)
            onResult(result.isSuccess)
        }
    }

    fun deleteQuickMood(id: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch { onResult(moodRepository.deleteQuickMood(id)) }
    }

    private fun reportError(error: Throwable) {
        AppLog.e(TAG, "日记数据加载失败", error)
        _error.value = error.message ?: "日记数据加载失败"
    }

    private companion object {
        const val TAG = "DiaryViewModel"
    }
}
