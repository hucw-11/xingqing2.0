package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.db.main.DiaryTagDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.normalizeTagName
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 标签写入结果：同名冲突与空名必须能被 UI 区分，不能静默覆盖或合并。 */
sealed interface TagWriteResult {
    data class Success(val tag: DiaryTag) : TagWriteResult
    data object EmptyName : TagWriteResult
    data object Duplicate : TagWriteResult
    data object Failed : TagWriteResult
}

/**
 * 自定义标签仓库。
 *
 * - 同名判定使用 [normalizeTagName]（去首尾空白、折叠连续空白、英文小写）；
 * - 改名保持 ID 与既有引用不变；
 * - 删除是逻辑删除并解除所有关联，不删除日记、心情或图片；
 * - 再次使用已删除的同名标签时恢复原行与原 ID，避免唯一索引冲突。
 */
@Single
class DiaryTagRepository(
    private val database: MainAppDatabase,
    private val tagDao: DiaryTagDao
) {

    fun observeTags(): Flow<List<DiaryTag>> = tagDao.observeActiveTags()

    @OptIn(ExperimentalUuidApi::class)
    suspend fun createTag(rawName: String, color: Int? = null): TagWriteResult {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        val normalized = normalizeTagName(name)
        if (normalized.isEmpty()) return TagWriteResult.EmptyName
        return runCatching {
            database.withWriteTransaction {
                val now = Clock.System.now().toEpochMilliseconds()
                val existing = tagDao.getByNormalizedName(normalized)
                if (existing != null && existing.deletedAt == null) {
                    return@withWriteTransaction TagWriteResult.Duplicate
                }
                val tag = existing?.copy(
                    name = name,
                    color = color ?: existing.color,
                    updatedAt = now,
                    deletedAt = null
                ) ?: DiaryTag(
                    id = Uuid.random().toString(),
                    name = name,
                    normalizedName = normalized,
                    color = color,
                    createdAt = now,
                    updatedAt = now
                )
                tagDao.upsert(tag)
                TagWriteResult.Success(tag)
            }
        }.getOrElse { TagWriteResult.Failed }
    }

    suspend fun renameTag(tagId: String, rawName: String): TagWriteResult {
        val name = rawName.trim().replace(Regex("\\s+"), " ")
        val normalized = normalizeTagName(name)
        if (normalized.isEmpty()) return TagWriteResult.EmptyName
        return runCatching {
            database.withWriteTransaction {
                val current = tagDao.getById(tagId)
                    ?: return@withWriteTransaction TagWriteResult.Failed
                if (current.deletedAt != null) return@withWriteTransaction TagWriteResult.Failed
                val sameName = tagDao.getByNormalizedName(normalized)
                if (sameName != null && sameName.id != tagId) {
                    return@withWriteTransaction TagWriteResult.Duplicate
                }
                val updated = current.copy(
                    name = name,
                    normalizedName = normalized,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
                tagDao.upsert(updated)
                TagWriteResult.Success(updated)
            }
        }.getOrElse { TagWriteResult.Failed }
    }

    suspend fun setColor(tagId: String, color: Int?): Boolean = runCatching {
        database.withWriteTransaction {
            val current = tagDao.getById(tagId) ?: return@withWriteTransaction false
            if (current.deletedAt != null) return@withWriteTransaction false
            tagDao.upsert(
                current.copy(
                    color = color,
                    updatedAt = Clock.System.now().toEpochMilliseconds()
                )
            )
            true
        }
    }.getOrDefault(false)

    /** 删除标签：解除日记/独立心情关联，但不删除任何记录或图片。 */
    suspend fun deleteTag(tagId: String): Boolean = runCatching {
        database.withWriteTransaction {
            val now = Clock.System.now().toEpochMilliseconds()
            tagDao.softDelete(tagId, now)
            tagDao.clearRefsForTag(tagId)
            tagDao.clearMoodRefsForTag(tagId)
        }
        true
    }.getOrDefault(false)
}
