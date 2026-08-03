package com.wardrobe.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.wardrobe.app.core.database.entity.FeedbackEntity
import kotlinx.coroutines.flow.Flow

/** Phase 9 — backs [com.wardrobe.app.core.model.intelligence.OutfitRating]:
 * this app's only rating concept is Phase 6's up/down [FeedbackEntity.vote],
 * so "Average Rating" is always derived from these two counts, never a
 * second, independently-tracked rating. */
data class FeedbackVoteCountRow(
    val positiveVotes: Int,
    val totalVotes: Int,
)

@Dao
interface FeedbackDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: FeedbackEntity): Long

    /** Links a feedback row to the [StyleRuleEntity][com.wardrobe.app.core.database.entity.StyleRuleEntity]
     * it generated, once that rule exists — see phase-3-persistence.md's note on the
     * mutual FK between `feedback` and `style_rules`. */
    @Update
    suspend fun update(entity: FeedbackEntity)

    @Query("SELECT * FROM feedback WHERE generatedStyleRuleId = :styleRuleId")
    suspend fun getByGeneratedStyleRule(styleRuleId: Long): List<FeedbackEntity>

    @Query("SELECT * FROM feedback WHERE id = :id")
    suspend fun getById(id: Long): FeedbackEntity?

    @Query("DELETE FROM feedback WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Phase 8 sync — see `CategoryDao.getBySyncId`'s KDoc. */
    @Query("SELECT * FROM feedback WHERE syncId = :syncId")
    suspend fun getBySyncId(syncId: String): FeedbackEntity?

    @Query(
        """
        SELECT
            SUM(CASE WHEN vote = 'UP' THEN 1 ELSE 0 END) AS positiveVotes,
            COUNT(*) AS totalVotes
        FROM feedback
        WHERE targetOutfitId = :outfitId
        """,
    )
    fun observeVoteCountsForOutfit(outfitId: Long): Flow<FeedbackVoteCountRow>
}
