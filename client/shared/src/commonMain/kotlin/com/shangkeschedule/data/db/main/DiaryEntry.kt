package com.shangkeschedule.data.db.main

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/**
 * 图文日记（P3 批次）。
 *
 * 与 [CourseNote] 的区别：
 * - 日记是**个人记录**，与课程/课表无外键关系，删除课程或整张课表不能级联删除日记；
 * - 正文是 Markdown 原文，不设 2000 字上限；
 * - 同一天可以有任意多篇（主键是 UUID，不以日期为键）；
 * - 删除使用逻辑删除（[deletedAt]），为后续同步保留墓碑。
 *
 * 时间语义：[occurredAt] 是用户记录的实际发生时刻（UTC 毫秒），[timeZoneId] 是记录时
 * 所在时区，用于把“某一天”换算成该时区下的本地日期边界；[createdAt] / [updatedAt]
 * 是系统时间，补记只改 [occurredAt]，不改创建时间。
 */
@Entity(
    tableName = "diary_entries",
    indices = [
        Index(value = ["occurredAt"]),
        Index(value = ["updatedAt"]),
        Index(value = ["isDraft"]),
        Index(value = ["deletedAt"])
    ]
)
data class DiaryEntry(
    @PrimaryKey
    val id: String,
    val title: String? = null,
    val markdown: String = "",
    val occurredAt: Long,
    val timeZoneId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDraft: Boolean = false,
    val deletedAt: Long? = null
)

/**
 * 日记图片附件。
 *
 * [relativePath] 相对 [com.shangkeschedule.data.di.AppStorage.filesDir]，例如
 * `diary/attachments/<uuid>.jpg`；不保存相册 URI 或源文件绝对路径，跨设备/换盘符后仍可解析。
 * [sha256] 是文件内容哈希，用于后续同步去重与完整性校验。
 *
 * 逻辑删除（[deletedAt]）只解除本篇引用；物理文件回收按任务书允许留到后续批次，
 * 避免在未确认没有其他引用时误删有效图片。
 */
@Entity(
    tableName = "diary_attachments",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["diaryId"]),
        Index(value = ["deletedAt"])
    ]
)
data class DiaryAttachment(
    @PrimaryKey
    val id: String,
    val diaryId: String,
    val relativePath: String,
    val mimeType: String,
    val byteSize: Long,
    val sha256: String,
    val sortOrder: Int = 0,
    val createdAt: Long,
    val deletedAt: Long? = null
)

/** 日记 + 心情 + 标签 + 附件的关系投影，列表与详情共用。 */
data class DiaryEntryWithDetails(
    @Embedded
    val entry: DiaryEntry,
    @Relation(parentColumn = "id", entityColumn = "diaryId")
    val attachments: List<DiaryAttachment>,
    @Relation(parentColumn = "id", entityColumn = "diaryId")
    val moods: List<MoodRecord>,
    @Relation(
        entity = DiaryTag::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room3.Junction(
            value = DiaryTagCrossRef::class,
            parentColumn = "diaryId",
            entityColumn = "tagId"
        )
    )
    val tags: List<DiaryTag>
)

/** 日记列表分页大小；不一次读取全部记录及其附件关系。 */
const val DIARY_PAGE_SIZE = 80

/**
 * 日记标题上限：只用于列表摘要与页面标题，不限制正文。
 * 与 CourseNote 的 60 字同一口径，避免超长标题撑爆列表项。
 */
const val DIARY_TITLE_MAX_LENGTH = 120

/**
 * Markdown 正文不设业务上限；此值只作为一次输入的软保护，远高于任务书要求的一万字。
 * 超出时拒绝继续追加新字符，而不是静默截断已保存内容。
 */
const val DIARY_MARKDOWN_SAFE_LIMIT = 2_000_000

/** 单张日记图片的字节上限（12 MiB）；编辑时先压缩为 JPEG，正常远低于此值。 */
const val DIARY_ATTACHMENT_MAX_BYTES = 12L * 1024 * 1024

/** 单篇日记的附件数量上限，防止误选整个相册导致列表与备份失控。 */
const val DIARY_ATTACHMENTS_MAX_COUNT = 30
