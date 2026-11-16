package com.shangkeschedule.data.db.main

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * 自定义标签（日记与独立心情共用）。
 *
 * 同名检测以 [normalizedName] 为准：`trim` + 连续空白折叠 + 英文小写。
 * 删除是逻辑删除；再次创建同名标签时恢复原行与原 ID，避免唯一索引冲突。
 * 颜色为 ARGB（Long，可空；为空时 UI 使用主题色）。
 */
@Entity(
    tableName = "diary_tags",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["deletedAt"])
    ]
)
data class DiaryTag(
    @PrimaryKey
    val id: String,
    val name: String,
    val normalizedName: String,
    val color: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

@Entity(
    tableName = "diary_tag_cross_ref",
    primaryKeys = ["diaryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DiaryTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["diaryId"]), Index(value = ["tagId"])]
)
data class DiaryTagCrossRef(
    val diaryId: String,
    val tagId: String
)

@Entity(
    tableName = "mood_tag_cross_ref",
    primaryKeys = ["moodId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = MoodRecord::class,
            parentColumns = ["id"],
            childColumns = ["moodId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DiaryTag::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["moodId"]), Index(value = ["tagId"])]
)
data class MoodTagCrossRef(
    val moodId: String,
    val tagId: String
)

/** 标签名的规范化结果；空串表示不是有效标签名。 */
fun normalizeTagName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").lowercase()
