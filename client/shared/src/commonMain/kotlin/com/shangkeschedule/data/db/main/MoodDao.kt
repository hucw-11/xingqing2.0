package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {

    /** 时间线：包含日记关联心情与独立快速心情；已逻辑删除的不出现。 */
    @Transaction
    @Query(
        """
        SELECT * FROM mood_records
        WHERE deletedAt IS NULL AND occurredAt >= :startAt AND occurredAt < :endAt
        ORDER BY occurredAt DESC, createdAt DESC
        """
    )
    fun observeTimeline(startAt: Long, endAt: Long): Flow<List<MoodRecordWithTags>>

    @Query("SELECT * FROM mood_records WHERE diaryId = :diaryId AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveForDiary(diaryId: String): MoodRecord?

    @Query("SELECT * FROM mood_records WHERE diaryId = :diaryId LIMIT 1")
    suspend fun getAnyForDiary(diaryId: String): MoodRecord?

    @Query("SELECT * FROM mood_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): MoodRecord?

    // 用 Upsert 而非 REPLACE：REPLACE 的删除+插入会级联清掉 MoodTagCrossRef。
    @Upsert
    suspend fun upsert(mood: MoodRecord)

    @Query(
        "UPDATE mood_records SET deletedAt = :now, updatedAt = :now " +
            "WHERE diaryId = :diaryId AND deletedAt IS NULL"
    )
    suspend fun softDeleteForDiary(diaryId: String, now: Long)

    @Query(
        "UPDATE mood_records SET deletedAt = :now, updatedAt = :now " +
            "WHERE id = :id AND deletedAt IS NULL AND diaryId IS NULL"
    )
    suspend fun softDeleteIndependent(id: String, now: Long)

    @Query("DELETE FROM mood_tag_cross_ref WHERE moodId = :moodId")
    suspend fun clearMoodTags(moodId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMoodTags(refs: List<MoodTagCrossRef>)
}
