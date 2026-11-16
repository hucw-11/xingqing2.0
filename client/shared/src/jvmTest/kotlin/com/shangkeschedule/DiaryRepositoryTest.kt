package com.shangkeschedule

import com.shangkeschedule.data.db.main.CourseTable
import com.shangkeschedule.data.db.main.MainAppDatabase
import com.shangkeschedule.data.di.AppStorage
import com.shangkeschedule.data.repository.DiaryFilter
import com.shangkeschedule.data.repository.DiaryRepository
import com.shangkeschedule.data.repository.DiarySaveRequest
import com.shangkeschedule.data.repository.DiaryTagRepository
import com.shangkeschedule.data.repository.MoodRepository
import com.shangkeschedule.data.repository.TagWriteResult
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.TimeZone
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 日记仓库的真实数据库测试：使用临时目录建独立主库，不触碰用户资料目录。
 * 覆盖同一天多篇、心情唯一、快速心情、草稿清理、标签改名/冲突/删除、
 * 关键词按普通文本匹配、标签任一/全部筛选与附件路径。
 */
class DiaryRepositoryTest {

    private val tempRoot: File = Files.createTempDirectory("xinqing-diary-test").toFile()
    private val storage = TestStorage(tempRoot)
    private val database = MainAppDatabase.getDatabase(storage)
    private val repository = DiaryRepository(
        database = database,
        diaryDao = database.diaryDao(),
        moodDao = database.moodDao(),
        tagDao = database.diaryTagDao(),
        attachmentDao = database.diaryAttachmentDao(),
        fileSystem = FileSystem.SYSTEM,
        filesDir = storage.filesDir
    )
    private val tagRepository = DiaryTagRepository(database, database.diaryTagDao())
    private val moodRepository = MoodRepository(
        database = database,
        moodDao = database.moodDao(),
        diaryDao = database.diaryDao(),
        tagDao = database.diaryTagDao()
    )

    private val zone = TimeZone.currentSystemDefault().id

    @AfterTest
    fun cleanup() {
        database.close()
        tempRoot.deleteRecursively()
    }

    @Test
    fun `同一天两篇独立保存互不覆盖`() = runBlocking {
        val morning = repository.createDraft(BASE + HOUR, zone)
        val evening = repository.createDraft(BASE + HOUR * 10, zone)
        assertTrue(
            repository.save(request(morning, title = "上午", markdown = "第一篇"))
                .isSuccess
        )
        assertTrue(
            repository.save(request(evening, title = "晚上", markdown = "第二篇"))
                .isSuccess
        )

        val entries = entriesInRange()
        assertEquals(2, entries.size)
        assertEquals(setOf(morning.id, evening.id), entries.map { it.entry.id }.toSet())
        assertEquals("第一篇", repository.getEntry(morning.id)?.markdown)
        assertEquals("第二篇", repository.getEntry(evening.id)?.markdown)
    }

    @Test
    fun `关联心情多次编辑只保留一条且分值为最后值`() = runBlocking {
        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, markdown = "心情", mood = 3))
        repository.save(request(entry, markdown = "心情", mood = 5))

        val active = database.moodDao().getActiveForDiary(entry.id)
        assertNotNull(active)
        assertEquals(5, active.score)
        val all = database.moodDao().observeTimeline(0, Long.MAX_VALUE).first()
            .filter { it.mood.diaryId == entry.id }
        assertEquals(1, all.size, "同一篇日记不能不断新增心情点")
    }

    @Test
    fun `无评分日记与三次快速心情互不影响`() = runBlocking {
        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, markdown = "没有心情"))

        assertEquals(3, listOf(1, 2, 3).map { index ->
            moodRepository.saveQuickMood(
                moodId = null,
                score = 2 + index % 3,
                occurredAt = BASE + index * 1000L,
                timeZoneId = zone,
                tagIds = emptySet()
            ).isSuccess
        }.count { it })

        assertNull(database.moodDao().getActiveForDiary(entry.id), "无评分日记不能补零")
        val timeline = moodRepository.observeTimeline().first()
        assertEquals(3, timeline.count { it.mood.diaryId == null })
    }

    @Test
    fun `补记昨天不篡改创建时间`() = runBlocking {
        val now = BASE + HOUR
        val yesterday = BASE - 24 * HOUR
        val entry = repository.createDraft(now, zone)
        repository.save(
            DiarySaveRequest(
                entryId = entry.id,
                title = "补记",
                markdown = "昨天上午",
                occurredAt = yesterday,
                timeZoneId = zone,
                isDraft = false,
                moodScore = null,
                tagIds = emptySet(),
                keptAttachmentIds = emptySet(),
                createdAt = now
            )
        )
        val saved = repository.getEntry(entry.id)
        assertNotNull(saved)
        assertEquals(yesterday, saved.occurredAt)
        assertEquals(entry.createdAt, saved.createdAt)
    }

    @Test
    fun `标签改名保持引用 同名冲突有提示 删除不删日记`() = runBlocking {
        val work = (tagRepository.createTag("工作") as TagWriteResult.Success).tag
        val other = (tagRepository.createTag("Other") as TagWriteResult.Success).tag

        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, markdown = "带标签", tags = setOf(work.id)))

        val renamed = tagRepository.renameTag(work.id, "工作记录")
        assertTrue(renamed is TagWriteResult.Success)
        assertEquals(work.id, (renamed as TagWriteResult.Success).tag.id)
        val detail = repository.getDetail(entry.id)
        assertNotNull(detail)
        assertEquals(listOf("工作记录"), detail.tags.map { it.name })

        assertEquals(
            TagWriteResult.Duplicate,
            tagRepository.renameTag(other.id, "工作记录")
        )
        assertEquals(
            TagWriteResult.Duplicate,
            tagRepository.createTag("  OTHER  ")
        )

        assertTrue(tagRepository.deleteTag(work.id))
        val afterDelete = repository.getDetail(entry.id)
        assertNotNull(afterDelete, "删除标签不能删除日记")
        assertTrue(afterDelete.tags.isEmpty())
    }

    @Test
    fun `关键词百分号与下划线按普通文本匹配`() = runBlocking {
        val percent = repository.createDraft(BASE, zone)
        val plain = repository.createDraft(BASE + HOUR, zone)
        repository.save(request(percent, markdown = "完成 100% 的目标"))
        repository.save(request(plain, markdown = "完成 100 的目标"))

        val percentResult = withTimeout(10_000) {
            repository.observeEntries(DiaryFilter(keyword = "%")).first()
        }
        assertEquals(listOf(percent.id), percentResult.map { it.entry.id })

        val underScore = repository.createDraft(BASE + HOUR * 2, zone)
        repository.save(request(underScore, markdown = "file_name"))
        val underscoreResult = withTimeout(10_000) {
            repository.observeEntries(DiaryFilter(keyword = "_")).first()
        }
        assertEquals(listOf(underScore.id), underscoreResult.map { it.entry.id })
    }

    @Test
    fun `标签筛选支持任一匹配与全部匹配且不重复条目`() = runBlocking {
        val a = (tagRepository.createTag("A") as TagWriteResult.Success).tag
        val b = (tagRepository.createTag("B") as TagWriteResult.Success).tag
        val both = repository.createDraft(BASE, zone)
        val onlyA = repository.createDraft(BASE + HOUR, zone)
        repository.save(request(both, markdown = "AB", tags = setOf(a.id, b.id)))
        repository.save(request(onlyA, markdown = "A", tags = setOf(a.id)))

        val any = withTimeout(10_000) {
            repository.observeEntries(
                DiaryFilter(tagIds = setOf(a.id, b.id), matchAllTags = false)
            ).first()
        }
        assertEquals(2, any.size)
        assertEquals(2, any.map { it.entry.id }.toSet().size)

        val all = withTimeout(10_000) {
            repository.observeEntries(
                DiaryFilter(tagIds = setOf(a.id, b.id), matchAllTags = true)
            ).first()
        }
        assertEquals(listOf(both.id), all.map { it.entry.id })
    }

    @Test
    fun `草稿区显示有内容草稿 全空草稿退出时清理`() = runBlocking {
        val empty = repository.createDraft(BASE, zone)
        assertTrue(repository.purgeIfEmpty(empty.id))
        assertNull(repository.getEntry(empty.id))

        val draft = repository.createDraft(BASE, zone)
        repository.save(request(draft, markdown = "未发布", isDraft = true))
        val drafts = withTimeout(10_000) { repository.observeDrafts().first() }
        assertEquals(listOf(draft.id), drafts.map { it.entry.id })
        assertTrue(drafts.single().entry.isDraft)
    }

    @Test
    fun `附件写入临时目录 路径越界被拒绝 逻辑删除保留文件`() = runBlocking {
        val entry = repository.createDraft(BASE, zone)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(64) { it.toByte() }
        val attachment = repository.addAttachment(entry.id, jpeg)
        assertNotNull(attachment)
        assertEquals("image/jpeg", attachment.mimeType)
        val path = repository.resolveAttachmentPath(attachment.relativePath)
        assertNotNull(path)
        assertTrue(File(path).isFile)
        assertFalse(
            File(path).absolutePath.contains(".."),
            "附件必须落在应用私有目录内"
        )
        assertNull(repository.resolveAttachmentPath("../../etc/passwd"))
        assertEquals(64 + 3, repository.readAttachment(attachment.id)?.size)

        repository.save(
            request(entry, markdown = "图片", keptAttachmentIds = emptySet())
        )
        val detail = repository.getDetail(entry.id)
        assertNotNull(detail)
        assertTrue(detail.attachments.isEmpty())
        // 本批允许延迟回收：逻辑删除后文件仍在，避免误删仍被草稿引用的图
        assertTrue(File(path).isFile)
    }

    @Test
    fun `Markdown 原文保存后往返不变`() = runBlocking {
        val markdown = """
            # 标题

            **粗体**、*斜体* 与 `行内代码`

            > 引用一段话

            - 第一项
            - 第二项

            ```kotlin
            val answer = 42
            ```

            [链接](https://example.com)

            ![说明](attachment://abc)
        """.trimIndent()
        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, title = "Markdown", markdown = markdown))
        assertEquals(markdown, repository.getEntry(entry.id)?.markdown)
    }

    @Test
    fun `一万字正文保存与读取`() = runBlocking {
        val body = buildString {
            repeat(200) { index ->
                append("第${index + 1}段：")
                append("心晴日记长文测试。".repeat(5))
                append('\n')
            }
        }
        assertEquals(true, body.length >= 10_000)
        val entry = repository.createDraft(BASE, zone)
        assertTrue(repository.save(request(entry, markdown = body)).isSuccess)
        assertEquals(body, repository.getEntry(entry.id)?.markdown)
    }

    @Test
    fun `删除一篇的图片不影响另一篇`() = runBlocking {
        val first = repository.createDraft(BASE, zone)
        val second = repository.createDraft(BASE + HOUR, zone)
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + ByteArray(32)
        val firstImage = repository.addAttachment(first.id, jpeg)
        val secondImage = repository.addAttachment(second.id, jpeg)
        assertNotNull(firstImage)
        assertNotNull(secondImage)

        repository.save(request(first, markdown = "第一篇"))
        repository.save(
            request(second, markdown = "第二篇", keptAttachmentIds = setOf(secondImage.id))
        )

        assertTrue(repository.getDetail(first.id)!!.attachments.isEmpty())
        val secondDetail = repository.getDetail(second.id)
        assertNotNull(secondDetail)
        assertEquals(listOf(secondImage.id), secondDetail.attachments.map { it.id })
        assertNotNull(repository.resolveAttachmentPath(secondImage.relativePath))
    }

    @Test
    fun `快速心情可以修改和删除`() = runBlocking {
        val created = moodRepository.saveQuickMood(null, 3, BASE, zone, emptySet())
        assertTrue(created.isSuccess)
        val id = created.getOrThrow()
        moodRepository.saveQuickMood(id, 5, BASE + HOUR, zone, emptySet())
        val afterEdit = moodRepository.observeTimeline().first()
        assertEquals(1, afterEdit.size)
        assertEquals(5, afterEdit.single().mood.score)

        assertTrue(moodRepository.deleteQuickMood(id))
        assertTrue(moodRepository.observeTimeline().first().isEmpty())
    }

    @Test
    fun `删除整张课表不级联删除日记`() = runBlocking {
        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, markdown = "独立于课表"))
        val courseTableDao = database.courseTableDao()
        courseTableDao.insert(CourseTable(id = "table-1", name = "2026 秋", createdAt = 1_700_000_000_000L))
        courseTableDao.deleteById("table-1")
        assertNotNull(repository.getEntry(entry.id))
        assertTrue(!repository.getEntry(entry.id)!!.isDraft)
    }

    @Test
    fun `删除日记不影响独立快速心情`() = runBlocking {
        val entry = repository.createDraft(BASE, zone)
        repository.save(request(entry, markdown = "会被删除", mood = 4))
        assertTrue(
            moodRepository.saveQuickMood(null, 2, BASE + HOUR, zone, emptySet()).isSuccess
        )
        repository.deleteEntry(entry.id)

        assertNull(repository.getDetail(entry.id))
        assertTrue(repository.observeDrafts().first().isEmpty())
        val timeline = moodRepository.observeTimeline().first()
        assertEquals(1, timeline.size)
        assertNull(timeline.single().mood.diaryId)
    }

    private suspend fun entriesInRange() = withTimeout(10_000) {
        repository.observeEntries(DiaryFilter()).first()
    }

    private fun request(
        entry: com.shangkeschedule.data.db.main.DiaryEntry,
        title: String? = null,
        markdown: String = "",
        isDraft: Boolean = false,
        mood: Int? = null,
        tags: Set<String> = emptySet(),
        keptAttachmentIds: Set<String> = emptySet()
    ) = DiarySaveRequest(
        entryId = entry.id,
        title = title,
        markdown = markdown,
        occurredAt = entry.occurredAt,
        timeZoneId = entry.timeZoneId,
        isDraft = isDraft,
        moodScore = mood,
        tagIds = tags,
        keptAttachmentIds = keptAttachmentIds,
        createdAt = entry.createdAt
    )

    private class TestStorage(private val root: File) : AppStorage {
        override val filesDir = File(root, "files").apply { mkdirs() }.absolutePath.toPath()
        override val cacheDir = File(root, "cache").apply { mkdirs() }.absolutePath.toPath()
        override fun getDatabasePath(dbName: String): String {
            val dir = File(root, "databases").apply { mkdirs() }
            return File(dir, dbName).absolutePath
        }
    }

    private companion object {
        const val HOUR = 60L * 60 * 1000
        /** 2026-11-16 00:00 UTC，避免依赖测试运行当天的本地时区细节。 */
        const val BASE = 1_794_787_200_000L
    }
}
