package com.shangkeschedule.data.db.main

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaryDao {

    /**
     * 正式记录分页查询：日期边界、关键词、标签任一/全部匹配、去重。
     *
     * 关键词用 `instr` 做普通子串匹配（不是 LIKE），`%`、`_` 不会变成 SQL 通配符；
     * 所有参数都是绑定参数。`tagIds` 为空时由调用方传哨兵值并把 `tagCount` 置 0。
     */
    @Transaction
    @Query(
        """
        SELECT * FROM diary_entries
        WHERE deletedAt IS NULL
          AND isDraft = :draft
          AND occurredAt >= :startAt
          AND occurredAt < :endAt
          AND (
            :keyword = ''
            OR instr(lower(coalesce(title, '')), lower(:keyword)) > 0
            OR instr(lower(markdown), lower(:keyword)) > 0
          )
          AND (
            :tagCount = 0
            OR EXISTS (
              SELECT 1 FROM diary_tag_cross_ref x
              INNER JOIN diary_tags t ON t.id = x.tagId
              WHERE x.diaryId = diary_entries.id
                AND x.tagId IN (:tagIds)
                AND t.deletedAt IS NULL
            )
          )
          AND (
            :tagCount = 0
            OR :matchAll = 0
            OR (
              SELECT COUNT(DISTINCT x.tagId) FROM diary_tag_cross_ref x
              INNER JOIN diary_tags t ON t.id = x.tagId
              WHERE x.diaryId = diary_entries.id
                AND x.tagId IN (:tagIds)
                AND t.deletedAt IS NULL
            ) = :tagCount
          )
        ORDER BY occurredAt DESC, createdAt DESC
        LIMIT :limit OFFSET :offset
        """
    )
    fun observeEntries(
        draft: Boolean,
        startAt: Long,
        endAt: Long,
        keyword: String,
        tagIds: List<String>,
        tagCount: Int,
        matchAll: Int,
        limit: Int,
        offset: Int
    ): Flow<List<DiaryEntryWithDetails>>

    /** 草稿区：只显示有实际内容的草稿，全空草稿不进入列表。 */
    @Transaction
    @Query(
        """
        SELECT * FROM diary_entries
        WHERE deletedAt IS NULL
          AND isDraft = 1
          AND (
            coalesce(title, '') != ''
            OR markdown != ''
            OR EXISTS (SELECT 1 FROM mood_records m WHERE m.diaryId = diary_entries.id AND m.deletedAt IS NULL)
            OR EXISTS (SELECT 1 FROM diary_attachments a WHERE a.diaryId = diary_entries.id AND a.deletedAt IS NULL)
          )
        ORDER BY updatedAt DESC
        """
    )
    fun observeDrafts(): Flow<List<DiaryEntryWithDetails>>

    @Query("SELECT id, title FROM diary_entries WHERE deletedAt IS NULL")
    fun observeTitles(): Flow<List<DiaryTitleRow>>

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getWithDetails(id: String): DiaryEntryWithDetails?

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun observeDetail(id: String): Flow<DiaryEntryWithDetails?>

    @Query("SELECT * FROM diary_entries WHERE id = :id LIMIT 1")
    suspend fun getEntry(id: String): DiaryEntry?

    // 不能用 REPLACE：该策略会先删行再插入，触发心情/标签/附件的外键级联删除。
    @Upsert
    suspend fun upsert(entry: DiaryEntry)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(entry: DiaryEntry)

    @Query("UPDATE diary_entries SET deletedAt = :now, updatedAt = :now WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDelete(id: String, now: Long)

    @Query("DELETE FROM diary_entries WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM diary_entries WHERE id = :id")
    suspend fun countById(id: String): Int

    /** 只清理静置够久的全空草稿；正在编辑的新草稿不会被误删。 */
    @Query(
        """
        DELETE FROM diary_entries
        WHERE isDraft = 1
          AND deletedAt IS NULL
          AND updatedAt < :cutoff
          AND coalesce(title, '') = ''
          AND markdown = ''
          AND NOT EXISTS (SELECT 1 FROM mood_records m WHERE m.diaryId = diary_entries.id AND m.deletedAt IS NULL)
          AND NOT EXISTS (SELECT 1 FROM diary_attachments a WHERE a.diaryId = diary_entries.id AND a.deletedAt IS NULL)
        """
    )
    suspend fun pruneEmptyDrafts(cutoff: Long)
}
