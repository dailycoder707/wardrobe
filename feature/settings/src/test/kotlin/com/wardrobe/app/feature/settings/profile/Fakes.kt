package com.wardrobe.app.feature.settings.profile

import com.wardrobe.app.core.domain.repository.PersonalizationRepository
import com.wardrobe.app.core.domain.repository.SyncRepository
import com.wardrobe.app.core.model.profile.GreetingStyle
import com.wardrobe.app.core.model.profile.PersonalizationSettings
import com.wardrobe.app.core.model.sync.ConflictResolution
import com.wardrobe.app.core.model.sync.SyncConflict
import com.wardrobe.app.core.model.sync.SyncHistoryEntry
import com.wardrobe.app.core.model.sync.SyncStatusSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mirrors `feature:closet`'s own `FakePersonalizationRepository` — each
 * feature module keeps its own fakes (established convention, see e.g.
 * `feature:capture`'s `FakeRepositories.kt`), not a shared cross-module test
 * artifact. */
class FakePersonalizationRepository(
    initial: PersonalizationSettings = PersonalizationSettings.DEFAULT,
) : PersonalizationRepository {
    private val flow = MutableStateFlow(initial)

    override fun observe(): Flow<PersonalizationSettings> = flow.asStateFlow()

    override suspend fun setDisplayName(name: String?) {
        flow.value = flow.value.copy(displayName = name)
    }

    override suspend fun setGreetingStyle(style: GreetingStyle) {
        flow.value = flow.value.copy(greetingStyle = style)
    }

    override suspend fun setCustomHomeTitle(title: String?) {
        flow.value = flow.value.copy(customHomeTitle = title)
    }

    override suspend fun setAvatarImageUri(uri: String?) {
        flow.value = flow.value.copy(avatarImageUri = uri)
    }

    override suspend fun setShowGreeting(show: Boolean) {
        flow.value = flow.value.copy(showGreeting = show)
    }

    override suspend fun setShowWeatherCard(show: Boolean) {
        flow.value = flow.value.copy(showWeatherCard = show)
    }

    override suspend fun setShowRecommendationCard(show: Boolean) {
        flow.value = flow.value.copy(showRecommendationCard = show)
    }

    override suspend fun setShowWardrobeHealthCard(show: Boolean) {
        flow.value = flow.value.copy(showWardrobeHealthCard = show)
    }

    override suspend fun setShowInspirationCard(show: Boolean) {
        flow.value = flow.value.copy(showInspirationCard = show)
    }
}

class FakeSyncRepository(
    initial: SyncStatusSnapshot = SyncStatusSnapshot(),
) : SyncRepository {
    private val statusFlow = MutableStateFlow(initial)
    private val conflictsFlow = MutableStateFlow<List<SyncConflict>>(emptyList())
    private val historyFlow = MutableStateFlow<List<SyncHistoryEntry>>(emptyList())

    override fun observeStatus(): Flow<SyncStatusSnapshot> = statusFlow.asStateFlow()

    override suspend fun syncNow() = Unit

    override fun observeUnresolvedConflicts(): Flow<List<SyncConflict>> = conflictsFlow.asStateFlow()

    override suspend fun resolveConflict(
        conflictId: Long,
        resolution: ConflictResolution,
    ) = Unit

    override fun observeHistory(limit: Int): Flow<List<SyncHistoryEntry>> = historyFlow.asStateFlow()
}
