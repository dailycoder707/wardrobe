package com.wardrobe.app.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.wardrobe.app.core.model.styling.StyleRuleSourceType
import com.wardrobe.app.core.model.styling.StyleRuleType

/**
 * `parametersJson` is a deliberate JSON-blob denormalization — see
 * phase-3-persistence.md's rationale (the styling engine loads all active rules into
 * memory; nothing queries rules by parameter value in SQL).
 */
@Entity(
    tableName = "style_rules",
    indices = [Index("sourceFeedbackId"), Index("syncId", unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = FeedbackEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceFeedbackId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class StyleRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val sourceType: StyleRuleSourceType,
    val sourceFeedbackId: Long?,
    val ruleType: StyleRuleType,
    val parametersJson: String,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val syncId: String = "",
)
