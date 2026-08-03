package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.styling.FeedbackTargetType
import com.wardrobe.app.core.model.styling.FeedbackVote

@Entity(
    tableName = "feedback",
    indices = [
        Index("targetGarmentId"),
        Index("targetOutfitId"),
        Index("generatedStyleRuleId"),
        Index("syncId", unique = true),
    ],
    foreignKeys = [
        ForeignKey(
            entity = GarmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetGarmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = OutfitEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetOutfitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StyleRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["generatedStyleRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetType: FeedbackTargetType,
    val targetGarmentId: Long?,
    val targetOutfitId: Long?,
    val vote: FeedbackVote,
    val reasonCode: String?,
    val reasonText: String?,
    val generatedStyleRuleId: Long?,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
