package com.wardrobe.app.core.database.converter

import androidx.room.TypeConverter
import com.wardrobe.app.core.model.ai.AiCallOutcome
import com.wardrobe.app.core.model.ai.AiCapability
import com.wardrobe.app.core.model.ai.AiJobStatus
import com.wardrobe.app.core.model.ai.AiResultSource
import com.wardrobe.app.core.model.garment.CategoryLevel
import com.wardrobe.app.core.model.garment.Condition
import com.wardrobe.app.core.model.garment.DressCode
import com.wardrobe.app.core.model.garment.Fit
import com.wardrobe.app.core.model.garment.GarmentGender
import com.wardrobe.app.core.model.garment.GarmentLength
import com.wardrobe.app.core.model.garment.GarmentStatus
import com.wardrobe.app.core.model.garment.ImageType
import com.wardrobe.app.core.model.garment.ImportQueueItemStatus
import com.wardrobe.app.core.model.garment.Neckline
import com.wardrobe.app.core.model.garment.Season
import com.wardrobe.app.core.model.garment.SleeveLength
import com.wardrobe.app.core.model.garment.WaterproofLevel
import com.wardrobe.app.core.model.outfit.OutfitSource
import com.wardrobe.app.core.model.styling.FeedbackTargetType
import com.wardrobe.app.core.model.styling.FeedbackVote
import com.wardrobe.app.core.model.styling.StyleRuleSourceType
import com.wardrobe.app.core.model.styling.StyleRuleType
import com.wardrobe.app.core.model.trip.LuggageSize
import com.wardrobe.app.core.model.wear.WearEventStatus

/**
 * Every enum used anywhere in the schema is stored as its [Enum.name] (TEXT), one
 * converter pair per enum — registered once via `@TypeConverters(Converters::class)`
 * on [com.wardrobe.app.core.database.WardrobeDatabase]. Dates are stored as ISO-8601
 * TEXT and timestamps as epoch-millisecond Long directly on entity fields (no
 * converter needed — see phase-3-persistence.md) rather than through this class.
 *
 * Every converter here is nullable-in/nullable-out, even for enums whose entity
 * column is `NOT NULL` (e.g. [GarmentStatus], [Season]): Kotlin can't overload a
 * converter pair on nullability alone (a reference type's nullable and non-null forms
 * erase to the same JVM signature), and several of these enums are also used as
 * *optional query parameters* (e.g. `GarmentFilter.status`/`.season`), which requires
 * the nullable form regardless. One nullable-tolerant converter per enum covers both
 * cases without a signature clash.
 */
class Converters {
    @TypeConverter
    fun fromCategoryLevel(value: CategoryLevel?): String? = value?.name

    @TypeConverter
    fun toCategoryLevel(value: String?): CategoryLevel? = value?.let(CategoryLevel::valueOf)

    @TypeConverter
    fun fromFit(value: Fit?): String? = value?.name

    @TypeConverter
    fun toFit(value: String?): Fit? = value?.let(Fit::valueOf)

    @TypeConverter
    fun fromGarmentLength(value: GarmentLength?): String? = value?.name

    @TypeConverter
    fun toGarmentLength(value: String?): GarmentLength? = value?.let(GarmentLength::valueOf)

    @TypeConverter
    fun fromSleeveLength(value: SleeveLength?): String? = value?.name

    @TypeConverter
    fun toSleeveLength(value: String?): SleeveLength? = value?.let(SleeveLength::valueOf)

    @TypeConverter
    fun fromCondition(value: Condition?): String? = value?.name

    @TypeConverter
    fun toCondition(value: String?): Condition? = value?.let(Condition::valueOf)

    @TypeConverter
    fun fromGarmentStatus(value: GarmentStatus?): String? = value?.name

    @TypeConverter
    fun toGarmentStatus(value: String?): GarmentStatus? = value?.let(GarmentStatus::valueOf)

    @TypeConverter
    fun fromSeason(value: Season?): String? = value?.name

    @TypeConverter
    fun toSeason(value: String?): Season? = value?.let(Season::valueOf)

    @TypeConverter
    fun fromDressCode(value: DressCode?): String? = value?.name

    @TypeConverter
    fun toDressCode(value: String?): DressCode? = value?.let(DressCode::valueOf)

    @TypeConverter
    fun fromNeckline(value: Neckline?): String? = value?.name

    @TypeConverter
    fun toNeckline(value: String?): Neckline? = value?.let(Neckline::valueOf)

    @TypeConverter
    fun fromGarmentGender(value: GarmentGender?): String? = value?.name

    @TypeConverter
    fun toGarmentGender(value: String?): GarmentGender? = value?.let(GarmentGender::valueOf)

    @TypeConverter
    fun fromWaterproofLevel(value: WaterproofLevel?): String? = value?.name

    @TypeConverter
    fun toWaterproofLevel(value: String?): WaterproofLevel? = value?.let(WaterproofLevel::valueOf)

    @TypeConverter
    fun fromImageType(value: ImageType?): String? = value?.name

    @TypeConverter
    fun toImageType(value: String?): ImageType? = value?.let(ImageType::valueOf)

    @TypeConverter
    fun fromOutfitSource(value: OutfitSource?): String? = value?.name

    @TypeConverter
    fun toOutfitSource(value: String?): OutfitSource? = value?.let(OutfitSource::valueOf)

    @TypeConverter
    fun fromStyleRuleSourceType(value: StyleRuleSourceType?): String? = value?.name

    @TypeConverter
    fun toStyleRuleSourceType(value: String?): StyleRuleSourceType? = value?.let(StyleRuleSourceType::valueOf)

    @TypeConverter
    fun fromStyleRuleType(value: StyleRuleType?): String? = value?.name

    @TypeConverter
    fun toStyleRuleType(value: String?): StyleRuleType? = value?.let(StyleRuleType::valueOf)

    @TypeConverter
    fun fromFeedbackTargetType(value: FeedbackTargetType?): String? = value?.name

    @TypeConverter
    fun toFeedbackTargetType(value: String?): FeedbackTargetType? = value?.let(FeedbackTargetType::valueOf)

    @TypeConverter
    fun fromFeedbackVote(value: FeedbackVote?): String? = value?.name

    @TypeConverter
    fun toFeedbackVote(value: String?): FeedbackVote? = value?.let(FeedbackVote::valueOf)

    @TypeConverter
    fun fromLuggageSize(value: LuggageSize?): String? = value?.name

    @TypeConverter
    fun toLuggageSize(value: String?): LuggageSize? = value?.let(LuggageSize::valueOf)

    @TypeConverter
    fun fromWearEventStatus(value: WearEventStatus?): String? = value?.name

    @TypeConverter
    fun toWearEventStatus(value: String?): WearEventStatus? = value?.let(WearEventStatus::valueOf)

    @TypeConverter
    fun fromImportQueueItemStatus(value: ImportQueueItemStatus?): String? = value?.name

    @TypeConverter
    fun toImportQueueItemStatus(value: String?): ImportQueueItemStatus? = value?.let(ImportQueueItemStatus::valueOf)

    @TypeConverter
    fun fromAiCapability(value: AiCapability?): String? = value?.name

    @TypeConverter
    fun toAiCapability(value: String?): AiCapability? = value?.let(AiCapability::valueOf)

    @TypeConverter
    fun fromAiJobStatus(value: AiJobStatus?): String? = value?.name

    @TypeConverter
    fun toAiJobStatus(value: String?): AiJobStatus? = value?.let(AiJobStatus::valueOf)

    @TypeConverter
    fun fromAiResultSource(value: AiResultSource?): String? = value?.name

    @TypeConverter
    fun toAiResultSource(value: String?): AiResultSource? = value?.let(AiResultSource::valueOf)

    @TypeConverter
    fun fromAiCallOutcome(value: AiCallOutcome?): String? = value?.name

    @TypeConverter
    fun toAiCallOutcome(value: String?): AiCallOutcome? = value?.let(AiCallOutcome::valueOf)
}
