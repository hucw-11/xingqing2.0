package com.shangkeschedule.data.repository

import androidx.room3.withWriteTransaction
import com.shangkeschedule.data.db.main.DIARY_ATTACHMENT_MAX_BYTES
import com.shangkeschedule.data.db.main.DIARY_ATTACHMENTS_MAX_COUNT
import com.shangkeschedule.data.db.main.DIARY_PAGE_SIZE
import com.shangkeschedule.data.db.main.DiaryAttachment
import com.shangkeschedule.data.db.main.DiaryAttachmentDao
import com.shangkeschedule.data.db.main.DiaryDao
import com.shangkeschedule.data.db.main.DiaryEntry
import com.shangkeschedule.data.db.main.DiaryEntryWithDetails
import com.shangkeschedule.data.db.main.DiaryTag
import com.shangkeschedule.data.db.main.DiaryTagCrossRef
import com.shangkeschedule.data.db.main.DiaryTagDao
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.db.main.MOOD_SCORE_RANGE
import com.shangkeschedule.data.db.main.MoodDao
import com.shangkeschedule.data.db.main.MoodRecord
import com.shangkeschedule.tool.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import okio.ByteString.Companion.toByteString
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 列表筛选条件。日期是用户所在时区的本地日期；关键词按普通文本子串处理。 */
data class DiaryFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val keyword: String = "",
    val tagIds: Set<String> = emptySet(),
    val matchAllTags: Boolean = false
) {
    val isActive: Boolean
        get() = startDate != null || endDate != null || keyword.isNotBlank() || tagIds.isNotEmpty()
}

/** 列表条目：日记 + 关联心情 + 标签 + 附件数量（不读取图片字节）。 */
data class DiarySummary(
    val entry: DiaryEntry,
    val mood: MoodRecord?,
    val tags: List<DiaryTag>,
    val attachmentCount: Int
)

/** 详情：正文 + 心情 + 标签 + 附件（含已解析的本地绝对路径）。 */
data class DiaryDetail(
    val entry: DiaryEntry,
    val mood: MoodRecord?,
    val tags: List<DiaryTag>,
    val attachments: List<DiaryAttachment>,
    val attachmentPaths: Map<String, String>
)

/** 一次保存请求；自动草稿与手动发布共用同一条写入路径。 */
data class DiarySaveRequest(
    val entryId: String,
    val title: String?,
    val markdown: String,
    val occurredAt: Long,
    val timeZoneId: String,
    val isDraft: Boolean,
    val moodScore: Int?,
    val tagIds: Set<String>,
    val keptAttachmentIds: Set<String>,
    val createdAt: Long
)

/**
 * 日记与附件的本地仓库。
 *
 * 写入约束：
 * - 一次保存（正文、标签、心情、附件引用）在同一个写事务中完成；
 * - 关联心情与日记共用发生时刻，且一篇日记最多一条；
 * - 附件文件先写临时文件再改名，数据库关系最后提交；写盘失败不留下半张图。
 * - 删除日记是逻辑删除，不碰独立快速心情，也不物理删除仍在磁盘上的图片。
 */
@Single
class DiaryRepository(
    private val database: MainAppDatabase,
    private val diaryDao: DiaryDao,
    private val moodDao: MoodDao,
    private val tagDao: DiaryTagDao,
    private val attachmentDao: DiaryAttachmentDao,
    private val fileSystem: FileSystem,
    @Named("FilesDir") private val filesDir: Path
) {

    fun observeEntries(
        filter: DiaryFilter,
        limit: Int = DIARY_PAGE_SIZE,
        offset: Int = 0
    ): Flow<List<DiarySummary>> {
        val (startAt, endAt) = widenedUtcRange(filter.startDate, filter.endDate)
        val activeTagIds = filter.tagIds.distinct()
        // Room 对空 IN 列表的展开在部分版本不可靠：无标签时传哨兵并让 tagCount = 0 短路。
        val boundTagIds = activeTagIds.ifEmpty { listOf(NO_TAG_SENTINEL) }
        return diaryDao.observeEntries(
            draft = false,
            startAt = startAt,
            endAt = endAt,
            keyword = filter.keyword,
            tagIds = boundTagIds,
            tagCount = activeTagIds.size,
            matchAll = if (filter.matchAllTags) 1 else 0,
            limit = limit,
            offset = offset
        ).map { rows ->
            rows.map(::toSummary)
                .filter {
                    matchesLocalDateRange(
                        it.entry.occurredAt,
                        it.entry.timeZoneId,
                        filter.startDate,
                        filter.endDate
                    )
                }
        }
    }

    fun observeDrafts(): Flow<List<DiarySummary>> =
        diaryDao.observeDrafts().map { rows -> rows.map(::toSummary) }

    fun observeAttachmentPaths(entryId: String): Flow<Map<String, String>> =
        attachmentDao.observeForDiary(entryId).map { list ->
            list.mapNotNull { attachment ->
                resolveAttachmentPath(attachment.relativePath)?.let { attachment.id to it }
            }.toMap()
        }

    suspend fun getDetail(entryId: String): DiaryDetail? {
        val row = diaryDao.getWithDetails(entryId) ?: return null
        return toDetail(row)
    }

    fun observeDetail(entryId: String): Flow<DiaryDetail?> =
        diaryDao.observeDetail(entryId).map { row -> row?.let(::toDetail) }

    suspend fun getEntry(entryId: String): DiaryEntry? = diaryDao.getEntry(entryId)

    /** 新建草稿身份：先落一行空草稿，后续自动保存始终更新同一行。 */
    suspend fun createDraft(occurredAt: Long, timeZoneId: String): DiaryEntry {
        val now = Clock.System.now().toEpochMilliseconds()
        val entry = DiaryEntry(
            id = newId(),
            title = null,
            markdown = "",
            occurredAt = occurredAt,
            timeZoneId = timeZoneId,
            createdAt = now,
            updatedAt = now,
            isDraft = true
        )
        diaryDao.insertIfAbsent(entry)
        return entry
    }

    /**
     * 保存日记（草稿或正式）。
     *
     * 正式保存前由调用方校验“正文/图片/心情至少一项”；仓库这里只保证事务一致性。
     */
    suspend fun save(request: DiarySaveRequest): Result<Unit> = runCatching {
        database.withWriteTransaction {
            val now = Clock.System.now().toEpochMilliseconds()
            val existing = diaryDao.getEntry(request.entryId)
            val entry = DiaryEntry(
                id = request.entryId,
                title = request.title
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() },
                markdown = request.markdown,
                occurredAt = request.occurredAt,
                timeZoneId = request.timeZoneId,
                createdAt = existing?.createdAt ?: request.createdAt,
                updatedAt = now,
                isDraft = request.isDraft,
                deletedAt = existing?.deletedAt
            )
            diaryDao.upsert(entry)

            tagDao.clearDiaryRefs(entry.id)
            if (request.tagIds.isNotEmpty()) {
                tagDao.insertDiaryRefs(request.tagIds.map { DiaryTagCrossRef(entry.id, it) })
            }

            val existingMood = moodDao.getAnyForDiary(entry.id)
            if (request.moodScore == null) {
                moodDao.softDeleteForDiary(entry.id, now)
            } else {
                val score = request.moodScore.coerceIn(MOOD_SCORE_RANGE.first, MOOD_SCORE_RANGE.last)
                val mood = existingMood?.copy(
                    score = score,
                    occurredAt = entry.occurredAt,
                    timeZoneId = entry.timeZoneId,
                    updatedAt = now,
                    deletedAt = null
                ) ?: MoodRecord(
                    id = newId(),
                    diaryId = entry.id,
                    score = score,
                    occurredAt = entry.occurredAt,
                    timeZoneId = entry.timeZoneId,
                    createdAt = now,
                    updatedAt = now
                )
                moodDao.upsert(mood)
            }

            attachmentDao.listActiveForDiary(entry.id)
                .filter { it.id !in request.keptAttachmentIds }
                .forEach { attachmentDao.softDelete(it.id, entry.id, now) }
        }
    }

    /** 逻辑删除：日记、关联心情、标签关联与附件关系一起失效；独立心情不受影响。 */
    suspend fun deleteEntry(entryId: String) {
        database.withWriteTransaction {
            val now = Clock.System.now().toEpochMilliseconds()
            diaryDao.softDelete(entryId, now)
            moodDao.softDeleteForDiary(entryId, now)
            tagDao.clearDiaryRefs(entryId)
            attachmentDao.softDeleteForDiary(entryId, now)
        }
    }

    /**
     * 退出全空新建页时清掉刚建的草稿身份，避免垃圾条目。
     * 只有确实全空（无标题、无正文、无心情、无附件）才物理删除。
     */
    suspend fun purgeIfEmpty(entryId: String): Boolean = database.withWriteTransaction {
        val row = diaryDao.getWithDetails(entryId) ?: return@withWriteTransaction false
        // 仅标签不算有内容：这类记录既不能发布，也不应成为找不到的隐藏草稿。
        val empty = row.entry.title.isNullOrBlank() &&
            row.entry.markdown.isBlank() &&
            row.moods.none { it.deletedAt == null } &&
            row.attachments.none { it.deletedAt == null }
        if (empty) {
            diaryDao.hardDelete(entryId)
        }
        empty
    }

    /** 清理异常残留的全空草稿（应用崩溃等），只处理静置超过一小时的旧行。 */
    suspend fun pruneStaleEmptyDrafts() {
        val cutoff = Clock.System.now().toEpochMilliseconds() - STALE_DRAFT_GRACE_MILLIS
        runCatching { diaryDao.pruneEmptyDrafts(cutoff) }
            .onFailure { AppLog.w(TAG, "清理空草稿失败", it) }
    }

    /**
     * 保存一张图片附件：先写临时文件，改名成功后再提交数据库关系。
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun addAttachment(entryId: String, bytes: ByteArray): DiaryAttachment? =
        withContext(Dispatchers.Default) {
            if (bytes.isEmpty()) return@withContext null
            if (bytes.size > DIARY_ATTACHMENT_MAX_BYTES) return@withContext null
            val activeCount = attachmentDao.listActiveForDiary(entryId).size
            if (activeCount >= DIARY_ATTACHMENTS_MAX_COUNT) return@withContext null
            runCatching {
                val kind = sniffImage(bytes)
                val id = Uuid.random().toString()
                val relativePath = "$ATTACHMENT_DIR/$id.${kind.extension}"
                val target = filesDir / relativePath
                val parent = target.parent ?: error("附件路径缺少目录")
                fileSystem.createDirectories(parent)
                val temp = parent / "$id.tmp"
                try {
                    fileSystem.write(temp) { write(bytes) }
                    fileSystem.atomicMove(temp, target)
                } catch (e: Exception) {
                    runCatching { fileSystem.delete(temp) }
                    throw e
                }
                val attachment = DiaryAttachment(
                    id = id,
                    diaryId = entryId,
                    relativePath = relativePath,
                    mimeType = kind.mimeType,
                    byteSize = bytes.size.toLong(),
                    sha256 = bytes.toByteString().sha256().hex(),
                    sortOrder = activeCount,
                    createdAt = Clock.System.now().toEpochMilliseconds()
                )
                try {
                    attachmentDao.upsert(attachment)
                } catch (e: Exception) {
                    // 数据库失败时回收刚写入的文件，不留下无主图片
                    runCatching { fileSystem.delete(target) }
                    throw e
                }
                attachment
            }.getOrElse { e ->
                AppLog.w(TAG, "日记图片保存失败（entryId=$entryId, ${bytes.size} B）", e)
                null
            }
        }

    /** 读取附件字节（用于查看大图/导出）；越界路径一律拒绝。 */
    suspend fun readAttachment(attachmentId: String): ByteArray? = withContext(Dispatchers.Default) {
        val attachment = attachmentDao.getById(attachmentId) ?: return@withContext null
        val path = resolveAttachmentPath(attachment.relativePath)?.toPath() ?: return@withContext null
        runCatching { fileSystem.read(path) { readByteArray() } }.getOrNull()
    }

    /** 把数据库里的相对路径解析为当前设备上的文件绝对路径；不存在或越界返回 null。 */
    fun resolveAttachmentPath(relativePath: String): String? {
        if (relativePath.isBlank()) return null
        return runCatching {
            val root = (filesDir / ATTACHMENT_DIR).normalize()
            val candidate = (filesDir / relativePath.toPath()).normalize()
            val rootString = root.toString().trimEnd('/')
            val candidateString = candidate.toString()
            if (!candidateString.startsWith("$rootString/")) return null
            if (!fileSystem.exists(candidate)) return null
            if (!fileSystem.metadata(candidate).isRegularFile) return null
            // 存在符号链接时再做一次真实路径校验，阻止目录穿越。
            val canonical = runCatching { fileSystem.canonicalize(candidate) }.getOrNull() ?: return null
            if (!canonical.toString().startsWith("$rootString/")) return null
            canonical.toString()
        }.getOrNull()
    }

    fun newId(): String = newDiaryId()

    private fun toSummary(row: DiaryEntryWithDetails): DiarySummary = DiarySummary(
        entry = row.entry,
        mood = row.moods.filter { it.deletedAt == null }.minByOrNull { it.createdAt },
        tags = row.tags.filter { it.deletedAt == null }.sortedBy { it.name.lowercase() },
        attachmentCount = row.attachments.count { it.deletedAt == null }
    )

    private fun toDetail(row: DiaryEntryWithDetails): DiaryDetail {
        val attachments = row.attachments
            .filter { it.deletedAt == null }
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
        return DiaryDetail(
            entry = row.entry,
            mood = row.moods.filter { it.deletedAt == null }.minByOrNull { it.createdAt },
            tags = row.tags.filter { it.deletedAt == null }.sortedBy { it.name.lowercase() },
            attachments = attachments,
            attachmentPaths = attachments.mapNotNull { attachment ->
                resolveAttachmentPath(attachment.relativePath)?.let { attachment.id to it }
            }.toMap()
        )
    }

    private data class ImageKind(val mimeType: String, val extension: String)

    private fun sniffImage(bytes: ByteArray): ImageKind {
        fun startsWith(vararg values: Int): Boolean =
            bytes.size >= values.size && values.indices.all { bytes[it] == values[it].toByte() }

        return when {
            startsWith(0xFF, 0xD8, 0xFF) -> ImageKind("image/jpeg", "jpg")
            startsWith(0x89, 0x50, 0x4E, 0x47) -> ImageKind("image/png", "png")
            startsWith(0x47, 0x49, 0x46, 0x38) -> ImageKind("image/gif", "gif")
            startsWith(0x52, 0x49, 0x46, 0x46) &&
                bytes.size >= 12 &&
                bytes.sliceArray(8..11).contentEquals("WEBP".encodeToByteArray()) ->
                ImageKind("image/webp", "webp")
            else -> ImageKind("application/octet-stream", "bin")
        }
    }

    private companion object {
        const val TAG = "DiaryRepository"
        const val ATTACHMENT_DIR = "diary/attachments"
        const val NO_TAG_SENTINEL = "__no_tag__"
        const val STALE_DRAFT_GRACE_MILLIS = 60L * 60 * 1000
    }
}

@OptIn(ExperimentalUuidApi::class)
private fun newDiaryId(): String = Uuid.random().toString()
