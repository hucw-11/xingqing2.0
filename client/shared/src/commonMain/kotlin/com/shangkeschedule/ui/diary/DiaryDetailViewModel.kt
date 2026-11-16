package com.shangkeschedule.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shangkeschedule.data.repository.DiaryDetail
import com.shangkeschedule.data.repository.DiaryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

/** 日记详情：订阅数据库，编辑保存后无需手动刷新。 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class DiaryDetailViewModel(
    private val diaryRepository: DiaryRepository
) : ViewModel() {

    private val boundId = MutableStateFlow<String?>(null)

    val detail: StateFlow<DiaryDetail?> = boundId
        .filterNotNull()
        .flatMapLatest { diaryRepository.observeDetail(it) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    fun bind(entryId: String) {
        boundId.value = entryId
    }

    fun delete(onResult: (Boolean) -> Unit) {
        val entryId = boundId.value
        if (entryId == null) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val ok = runCatching { diaryRepository.deleteEntry(entryId) }.isSuccess
            if (ok) _deleted.value = true
            onResult(ok)
        }
    }
}
