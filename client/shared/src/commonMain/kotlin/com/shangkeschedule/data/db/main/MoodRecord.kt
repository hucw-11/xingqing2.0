package com.shangkeschedule.data.db.main

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/**
 * 心情记录：五档评分 + 发生时刻。
 *
 * [diaryId] 可空：
 * - 非空 = 某篇日记的关联心情，全库最多一条（唯一索引；SQLite 允许任意多条 NULL）；
 * - 空 = 独立的“快速心情”，同一天可以记录任意多次。
 *
 * [score] 只在 1..5；没有评分就是**没有记录**，绝不写入 0 或最低分占位。
 * [scaleVersion] 记住量表版本，未来换量表时图表可按版本归一化。
 */
@Entity(
    tableName = "mood_records",
    foreignKeys = [
        ForeignKey(
            entity = DiaryEntry::class,
            parentColumns = ["id"],
            childColumns = ["diaryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["diaryId"], unique = true),
        Index(value = ["occurredAt"]),
        Index(value = ["deletedAt"])
    ]
)
data class MoodRecord(
    @PrimaryKey
    val id: String,
    val diaryId: String? = null,
    val score: Int,
    val scaleVersion: Int = CURRENT_MOOD_SCALE_VERSION,
    val occurredAt: Long,
    val timeZoneId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null
)

/** 当前五档量表版本。 */
const val CURRENT_MOOD_SCALE_VERSION = 1

/** 有效评分范围：1..5。 */
val MOOD_SCORE_RANGE = 1..5

/** 心情记录 + 独立心情自己的标签（日记关联心情的标签从日记派生，不写这里）。 */
data class MoodRecordWithTags(
    @Embedded
    val mood: MoodRecord,
    @Relation(
        entity = DiaryTag::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = androidx.room3.Junction(
            value = MoodTagCrossRef::class,
            parentColumn = "moodId",
            entityColumn = "tagId"
        )
    )
    val tags: List<DiaryTag>
)

/** 日记标签分配（供心情时间线把“日记心情”的标签还原成日记标签）。 */
data class DiaryTagAssignment(
    val ownerId: String,
    @Embedded
    val tag: DiaryTag
)

/** 日记标题投影（心情时间线显示“来自日记：标题”）。 */
data class DiaryTitleRow(
    val id: String,
    val title: String?
)
