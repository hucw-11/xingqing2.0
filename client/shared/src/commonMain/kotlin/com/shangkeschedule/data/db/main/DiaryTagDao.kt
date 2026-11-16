package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryTagDao {

    @Query("SELECT * FROM diary_tags WHERE deletedAt IS NULL ORDER BY name COLLATE NOCASE ASC")
    fun observeActiveTags(): Flow<List<DiaryTag>>

    @Query("SELECT * FROM diary_tags WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): DiaryTag?

    @Query("SELECT * FROM diary_tags WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiaryTag?

    // 改名/配色必须保持 ID 与关联不变；REPLACE 会级联删除所有标签关联。
    @Upsert
    suspend fun upsert(tag: DiaryTag)

    @Query(
        "UPDATE diary_tags SET deletedAt = :now, updatedAt = :now " +
            "WHERE id = :id AND deletedAt IS NULL"
    )
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM diary_tag_cross_ref WHERE diaryId = :diaryId")
    suspend fun clearDiaryRefs(diaryId: String)

    @Query("DELETE FROM diary_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearRefsForTag(tagId: String)

    @Query("DELETE FROM mood_tag_cross_ref WHERE tagId = :tagId")
    suspend fun clearMoodRefsForTag(tagId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDiaryRefs(refs: List<DiaryTagCrossRef>)

    @Query("SELECT * FROM diary_tag_cross_ref WHERE diaryId = :diaryId")
    suspend fun getDiaryRefs(diaryId: String): List<DiaryTagCrossRef>

    @Query("SELECT * FROM mood_tag_cross_ref WHERE moodId = :moodId")
    suspend fun getMoodRefs(moodId: String): List<MoodTagCrossRef>

    @Query(
        """
        SELECT x.diaryId AS ownerId, t.*
        FROM diary_tag_cross_ref x
        INNER JOIN diary_tags t ON t.id = x.tagId
        WHERE t.deletedAt IS NULL
        """
    )
    fun observeDiaryTagAssignments(): Flow<List<DiaryTagAssignment>>
}
