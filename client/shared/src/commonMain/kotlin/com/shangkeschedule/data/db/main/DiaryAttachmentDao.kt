package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryAttachmentDao {

    @Query(
        "SELECT * FROM diary_attachments WHERE diaryId = :diaryId AND deletedAt IS NULL " +
            "ORDER BY sortOrder ASC, createdAt ASC"
    )
    fun observeForDiary(diaryId: String): Flow<List<DiaryAttachment>>

    @Query(
        "SELECT * FROM diary_attachments WHERE diaryId = :diaryId AND deletedAt IS NULL " +
            "ORDER BY sortOrder ASC, createdAt ASC"
    )
    suspend fun listActiveForDiary(diaryId: String): List<DiaryAttachment>

    @Query("SELECT * FROM diary_attachments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DiaryAttachment?

    @Query(
        "SELECT COUNT(*) FROM diary_attachments WHERE relativePath = :relativePath AND deletedAt IS NULL"
    )
    suspend fun countActiveByPath(relativePath: String): Int

    @Upsert
    suspend fun upsert(attachment: DiaryAttachment)

    @Query(
        "UPDATE diary_attachments SET deletedAt = :now " +
            "WHERE id = :id AND diaryId = :diaryId AND deletedAt IS NULL"
    )
    suspend fun softDelete(id: String, diaryId: String, now: Long)

    @Query(
        "UPDATE diary_attachments SET deletedAt = :now " +
            "WHERE diaryId = :diaryId AND deletedAt IS NULL"
    )
    suspend fun softDeleteForDiary(diaryId: String, now: Long)
}
