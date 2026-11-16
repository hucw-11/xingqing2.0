package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.DiaryDao
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.db.main.DiaryTagDao
import com.shangkeschedule.data.db.main.MOOD_SCORE_RANGE
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.MoodDao
import com.shangkeschedule.data.db.main.MoodRecord
import com.shangkeschedule.data.db.main.MoodTagCrossRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 心情时间线上的一个点。 */
data class MoodPoint(
    val mood: MoodRecord,
    /** 独立心情 = 自己的标签；日记关联心情 = 日记标签（派生，不复制关联行）。 */
    val tags: List<DiaryTag>,
    val diaryTitle: String?
) {
    val fromDiary: Boolean get() = mood.diaryId != null
}

/**
 * 心情仓库：独立快速心情的读写，以及日记心情与快速心情合并后的时间线。
 *
 * 本批不画曲线，但所有记录都按真实发生时刻（[MoodRecord.occurredAt]）保存，
 * 未来按当天实际时刻排序即可直接使用。
 */
@Single
class MoodRepository(
    private val database: MainAppDatabase,
    private val moodDao: MoodDao,
    private val diaryDao: DiaryDao,
    private val tagDao: DiaryTagDao
) {

    fun observeTimeline(
        startDate: LocalDate? = null,
        endDate: LocalDate? = null,
        tagIds: Set<String> = emptySet(),
        matchAllTags: Boolean = false
    ): Flow<List<MoodPoint>> {
        val (startAt, endAt) = widenedUtcRange(startDate, endDate)

        return combine(
            moodDao.observeTimeline(startAt, endAt),
            tagDao.observeDiaryTagAssignments(),
            diaryDao.observeTitles()
        ) { rows, assignments, titles ->
            val tagsByDiary = assignments.groupBy({ it.ownerId }, { it.tag })
            val titleById = titles.associate { it.id to it.title }
            rows.mapNotNull { row ->
                val mood = row.mood
                if (mood.deletedAt != null) return@mapNotNull null
                if (!matchesLocalDateRange(mood.occurredAt, mood.timeZoneId, startDate, endDate)) {
                    return@mapNotNull null
                }
                val diaryId = mood.diaryId
                val tags = if (diaryId != null) {
                    tagsByDiary[diaryId].orEmpty().filter { it.deletedAt == null }
                } else {
                    row.tags.filter { it.deletedAt == null }
                }
                if (!matchesTagFilter(tags, tagIds, matchAllTags)) return@mapNotNull null
                MoodPoint(
                    mood = mood,
                    tags = tags.sortedBy { it.name.lowercase() },
                    diaryTitle = diaryId?.let { titleById[it] }
                )
            }.sortedByDescending { it.mood.occurredAt }
        }
    }

    /** 保存独立快速心情；[moodId] 为 null 表示新建。日记关联心情不从这里写入。 */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun saveQuickMood(
        moodId: String?,
        score: Int,
        occurredAt: Long,
        timeZoneId: String,
        tagIds: Set<String>
    ): Result<String> {
        if (score !in MOOD_SCORE_RANGE) {
            return Result.failure(IllegalArgumentException("心情评分必须在 1..5"))
        }
        return runCatching {
            database.withWriteTransaction {
                val now = Clock.System.now().toEpochMilliseconds()
                val existing = moodId?.let { moodDao.getById(it) }
                if (existing != null && existing.diaryId != null) {
                    error("日记关联心情不能在快速心情里单独修改")
                }
                val id = existing?.id ?: Uuid.random().toString()
                val record = existing?.copy(
                    score = score,
                    occurredAt = occurredAt,
                    timeZoneId = timeZoneId,
                    updatedAt = now,
                    deletedAt = null
                ) ?: MoodRecord(
                    id = id,
                    diaryId = null,
                    score = score,
                    occurredAt = occurredAt,
                    timeZoneId = timeZoneId,
                    createdAt = now,
                    updatedAt = now
                )
                moodDao.upsert(record)
                moodDao.clearMoodTags(id)
                if (tagIds.isNotEmpty()) {
                    moodDao.insertMoodTags(tagIds.map { MoodTagCrossRef(id, it) })
                }
                id
            }
        }
    }

    suspend fun getQuickMood(id: String): MoodRecord? {
        val mood = moodDao.getById(id) ?: return null
        return mood.takeIf { it.deletedAt == null && it.diaryId == null }
    }

    suspend fun getTagIdsForMood(id: String): Set<String> =
        tagDao.getMoodRefs(id).map { it.tagId }.toSet()

    /** 只允许删除独立快速心情；日记关联心情随日记一起失效。 */
    suspend fun deleteQuickMood(id: String): Boolean = runCatching {
        database.withWriteTransaction {
            val mood = moodDao.getById(id) ?: return@withWriteTransaction false
            if (mood.diaryId != null) return@withWriteTransaction false
            val now = Clock.System.now().toEpochMilliseconds()
            moodDao.softDeleteIndependent(id, now)
            moodDao.clearMoodTags(id)
            true
        }
    }.getOrDefault(false)

    private fun matchesTagFilter(
        tags: List<DiaryTag>,
        tagIds: Set<String>,
        matchAll: Boolean
    ): Boolean {
        if (tagIds.isEmpty()) return true
        val ids = tags.map { it.id }.toSet()
        return if (matchAll) ids.containsAll(tagIds) else ids.any { it in tagIds }
    }
}
