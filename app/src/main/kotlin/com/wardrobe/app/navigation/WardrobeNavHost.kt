package com.wardrobe.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.wardrobe.app.BuildConfig
import com.wardrobe.app.core.ui.components.NavigationDock
import com.wardrobe.app.core.ui.components.NavigationDockDestination
import com.wardrobe.app.feature.calendar.calendar.CalendarScreen
import com.wardrobe.app.feature.calendar.navigation.CalendarRoute
import com.wardrobe.app.feature.capture.capture.GarmentCaptureScreen
import com.wardrobe.app.feature.capture.navigation.GarmentCaptureRoute
import com.wardrobe.app.feature.capture.navigation.GarmentImportQueueRoute
import com.wardrobe.app.feature.capture.navigation.GarmentReviewMetadataRoute
import com.wardrobe.app.feature.capture.queue.GarmentImportQueueScreen
import com.wardrobe.app.feature.capture.review.GarmentReviewMetadataScreen
import com.wardrobe.app.feature.closet.closet.ClosetAddActions
import com.wardrobe.app.feature.closet.closet.ClosetScreen
import com.wardrobe.app.feature.closet.debug.DeveloperPanelScreen
import com.wardrobe.app.feature.closet.detail.GarmentDetailScreen
import com.wardrobe.app.feature.closet.edit.EditGarmentScreen
import com.wardrobe.app.feature.closet.home.HomeScreen
import com.wardrobe.app.feature.closet.navigation.ClosetRoute
import com.wardrobe.app.feature.closet.navigation.DeveloperPanelRoute
import com.wardrobe.app.feature.closet.navigation.EditGarmentRoute
import com.wardrobe.app.feature.closet.navigation.GarmentDetailRoute
import com.wardrobe.app.feature.closet.navigation.HomeRoute
import com.wardrobe.app.feature.onboarding.navigation.OnboardingAiRoute
import com.wardrobe.app.feature.onboarding.navigation.OnboardingFinishRoute
import com.wardrobe.app.feature.onboarding.navigation.OnboardingNameRoute
import com.wardrobe.app.feature.onboarding.navigation.OnboardingStyleRoute
import com.wardrobe.app.feature.onboarding.navigation.OnboardingWelcomeRoute
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingAiScreen
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingFinishScreen
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingGateViewModel
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingNameScreen
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingStyleScreen
import com.wardrobe.app.feature.onboarding.onboarding.OnboardingWelcomeScreen
import com.wardrobe.app.feature.outfits.builder.OutfitBuilderScreen
import com.wardrobe.app.feature.outfits.capsules.CapsulesScreen
import com.wardrobe.app.feature.outfits.detail.OutfitDetailScreen
import com.wardrobe.app.feature.outfits.duplicates.DuplicatesScreen
import com.wardrobe.app.feature.outfits.list.SavedLooksScreen
import com.wardrobe.app.feature.outfits.navigation.CapsulesRoute
import com.wardrobe.app.feature.outfits.navigation.DuplicateGarmentsRoute
import com.wardrobe.app.feature.outfits.navigation.OutfitBuilderRoute
import com.wardrobe.app.feature.outfits.navigation.OutfitDetailRoute
import com.wardrobe.app.feature.outfits.navigation.OutfitPreviewRoute
import com.wardrobe.app.feature.outfits.navigation.RecommendationsRoute
import com.wardrobe.app.feature.outfits.navigation.SavedLooksRoute
import com.wardrobe.app.feature.outfits.navigation.StylistPreferencesRoute
import com.wardrobe.app.feature.outfits.preferences.StylistPreferencesScreen
import com.wardrobe.app.feature.outfits.preview.OutfitPreviewScreen
import com.wardrobe.app.feature.outfits.recommendations.RecommendationsScreen
import com.wardrobe.app.feature.settings.aiproviders.AiProvidersScreen
import com.wardrobe.app.feature.settings.navigation.AiProvidersRoute
import com.wardrobe.app.feature.settings.navigation.PairingRoute
import com.wardrobe.app.feature.settings.navigation.ProfileRoute
import com.wardrobe.app.feature.settings.navigation.WardrobeSyncRoute
import com.wardrobe.app.feature.settings.navigation.WeatherSettingsRoute
import com.wardrobe.app.feature.settings.profile.ProfileScreen
import com.wardrobe.app.feature.settings.sync.PairingScreen
import com.wardrobe.app.feature.settings.sync.WardrobeSyncScreen
import com.wardrobe.app.feature.settings.weather.WeatherSettingsScreen
import com.wardrobe.app.feature.stats.health.HealthScreen
import com.wardrobe.app.feature.stats.insights.InsightsScreen
import com.wardrobe.app.feature.stats.navigation.InsightsRoute
import com.wardrobe.app.feature.stats.navigation.WardrobeHealthRoute
import com.wardrobe.app.feature.stats.navigation.WardrobeStoryRoute
import com.wardrobe.app.feature.stats.story.StoryScreen
import com.wardrobe.app.feature.trips.detail.TripDetailScreen
import com.wardrobe.app.feature.trips.list.TripsScreen
import com.wardrobe.app.feature.trips.navigation.PackingRoute
import com.wardrobe.app.feature.trips.navigation.TripDetailRoute
import com.wardrobe.app.feature.trips.navigation.TripsRoute
import com.wardrobe.app.feature.trips.packing.PackingScreen
import com.wardrobe.app.feature.tryon.capture.BodyProfileCaptureScreen
import com.wardrobe.app.feature.tryon.compare.TryOnCompareScreen
import com.wardrobe.app.feature.tryon.masking.MaskEditorScreen
import com.wardrobe.app.feature.tryon.navigation.BodyProfileCaptureRoute
import com.wardrobe.app.feature.tryon.navigation.MaskEditorRoute
import com.wardrobe.app.feature.tryon.navigation.TryOnCompareRoute
import com.wardrobe.app.feature.tryon.navigation.TryOnRoute
import com.wardrobe.app.feature.tryon.render.TryOnScreen

private val TOP_LEVEL_DESTINATIONS =
    listOf(
        NavigationDockDestination("Home", Icons.Outlined.Home, Icons.Filled.Home),
        NavigationDockDestination("Closet", Icons.Outlined.Checkroom, Icons.Filled.Checkroom),
        NavigationDockDestination("Outfits", Icons.Outlined.Style, Icons.Filled.Style),
        NavigationDockDestination("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
        NavigationDockDestination("Insights", Icons.Outlined.Insights, Icons.Filled.Insights),
    )

private val TOP_LEVEL_ROUTES: List<Any> =
    listOf(HomeRoute, ClosetRoute(), SavedLooksRoute, CalendarRoute, InsightsRoute)

private const val HOME_INDEX = 0
private const val CLOSET_INDEX = 1
private const val SAVED_LOOKS_INDEX = 2
private const val CALENDAR_INDEX = 3
private const val INSIGHTS_INDEX = 4

/**
 * App-level NavHost. [HomeRoute]/[ClosetRoute]/[SavedLooksRoute]/[CalendarRoute]/
 * [InsightsRoute] show the floating [NavigationDock] — per `navigation-flow.md`,
 * all 5 of the originally-planned top-level destinations are real screens as of
 * Phase 5e. [WardrobeStoryRoute]/[WardrobeHealthRoute] are reached from within
 * Insights, not dock-level, the same way `GarmentDetailRoute`/`EditGarmentRoute`
 * are real screens without being nav-dock destinations.
 */
@Composable
fun WardrobeNavHost() {
    val gateViewModel: OnboardingGateViewModel = hiltViewModel()
    val isOnboardingComplete by gateViewModel.isOnboardingComplete.collectAsStateWithLifecycle()
    val onboardingComplete = isOnboardingComplete
    if (onboardingComplete == null) {
        // First read of the onboarding-complete signal (DataStore + Room) is
        // still in flight — a blank frame for at most a moment, never a
        // flash of Home before possibly redirecting to Onboarding.
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val selectedIndex =
        when {
            destination?.hasRoute<ClosetRoute>() == true -> CLOSET_INDEX
            destination?.hasRoute<SavedLooksRoute>() == true -> SAVED_LOOKS_INDEX
            destination?.hasRoute<CalendarRoute>() == true -> CALENDAR_INDEX
            destination?.hasRoute<InsightsRoute>() == true -> INSIGHTS_INDEX
            else -> HOME_INDEX
        }
    val isTopLevel =
        destination?.hasRoute<HomeRoute>() == true ||
            destination?.hasRoute<ClosetRoute>() == true ||
            destination?.hasRoute<SavedLooksRoute>() == true ||
            destination?.hasRoute<CalendarRoute>() == true ||
            destination?.hasRoute<InsightsRoute>() == true

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationDock(
                    destinations = TOP_LEVEL_DESTINATIONS,
                    selectedIndex = selectedIndex,
                    onSelect = { index ->
                        navController.navigate(TOP_LEVEL_ROUTES[index]) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    modifier = Modifier.padding(16.dp),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (onboardingComplete) HomeRoute else OnboardingWelcomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            onboardingDestinations(navController)
            closetDestinations(navController, currentDestinationRoute = destination?.route.orEmpty())
            outfitsDestinations(navController)
            calendarDestinations()
            statsDestinations(navController)
            tripsDestinations(navController)
            tryOnDestinations(navController)
            captureDestinations(navController)
        }
    }
}

/** M16 — first-run onboarding. Each screen pops itself off the back stack
 * once it hands off to the next (`popUpTo` clears the whole onboarding run
 * once Finish/Skip reaches [HomeRoute]), so Back never re-enters a
 * completed onboarding step from Home. [OnboardingAiRoute]'s "Configure AI"
 * is a plain forward navigation (no `popUpTo`), so Back from
 * [AiProvidersRoute] returns to the AI step exactly as it would for any
 * other screen-to-screen hop in this app. */
private fun NavGraphBuilder.onboardingDestinations(navController: NavHostController) {
    composable<OnboardingWelcomeRoute> {
        OnboardingWelcomeScreen(
            onGetStarted = { navController.navigate(OnboardingNameRoute) },
            onSkipped = {
                navController.navigate(HomeRoute) {
                    popUpTo(OnboardingWelcomeRoute) { inclusive = true }
                }
            },
        )
    }

    composable<OnboardingNameRoute> {
        OnboardingNameScreen(
            onContinue = { navController.navigate(OnboardingStyleRoute) },
            onSkip = { navController.navigate(OnboardingStyleRoute) },
        )
    }

    composable<OnboardingStyleRoute> {
        OnboardingStyleScreen(
            onContinue = { navController.navigate(OnboardingAiRoute) },
            onSkip = { navController.navigate(OnboardingAiRoute) },
        )
    }

    composable<OnboardingAiRoute> {
        OnboardingAiScreen(
            onConfigureAi = { navController.navigate(AiProvidersRoute) },
            onContinueOnDevice = { navController.navigate(OnboardingFinishRoute) },
        )
    }

    composable<OnboardingFinishRoute> {
        OnboardingFinishScreen(
            onDone = {
                navController.navigate(HomeRoute) {
                    popUpTo(OnboardingWelcomeRoute) { inclusive = true }
                }
            },
        )
    }
}

private fun NavGraphBuilder.closetDestinations(
    navController: NavHostController,
    currentDestinationRoute: String,
) {
    composable<HomeRoute> {
        HomeScreen(
            onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) },
            onBrowseCloset = { navController.navigate(ClosetRoute()) },
            onOpenFavorites = { navController.navigate(ClosetRoute(favoritesOnly = true)) },
            onOpenSearch = { navController.navigate(ClosetRoute(focusSearch = true)) },
            onOpenInsights = { navController.navigate(InsightsRoute) },
            onOpenRecommendations = { navController.navigate(RecommendationsRoute) },
            onOpenTrips = { navController.navigate(TripsRoute) },
            onTryOnRecommendation = { ids -> navController.navigate(TryOnRoute(garmentIds = ids.joinToString(","))) },
            onTakePhoto = { navController.navigate(GarmentCaptureRoute) },
            onImportStarted = { navController.navigate(GarmentImportQueueRoute) },
            onOpenProfile = { navController.navigate(ProfileRoute) },
            onOpenAiProviders = { navController.navigate(AiProvidersRoute) },
            onOpenDeveloperPanel =
                if (BuildConfig.DEBUG) {
                    { navController.navigate(DeveloperPanelRoute) }
                } else {
                    null
                },
        )
    }

    composable<ClosetRoute> { entry ->
        val route = entry.toRoute<ClosetRoute>()
        ClosetScreen(
            onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) },
            addActions =
                ClosetAddActions(
                    onTakePhoto = { navController.navigate(GarmentCaptureRoute) },
                    onImportStarted = { navController.navigate(GarmentImportQueueRoute) },
                ),
            initialFavoriteFilter = route.favoritesOnly,
            initialSearchFocus = route.focusSearch,
        )
    }

    composable<GarmentDetailRoute> {
        GarmentDetailScreen(
            onBack = { navController.popBackStack() },
            onEdit = { id -> navController.navigate(EditGarmentRoute(id)) },
        )
    }

    composable<EditGarmentRoute> {
        EditGarmentScreen(
            onBack = { navController.popBackStack() },
            onSaved = { navController.popBackStack() },
        )
    }

    if (BuildConfig.DEBUG) {
        composable<DeveloperPanelRoute> {
            DeveloperPanelScreen(
                onBack = { navController.popBackStack() },
                currentDestination = currentDestinationRoute,
            )
        }
    }
}

private fun NavGraphBuilder.outfitsDestinations(navController: NavHostController) {
    composable<SavedLooksRoute> {
        SavedLooksScreen(
            onOpenOutfit = { id -> navController.navigate(OutfitDetailRoute(id)) },
            onCreateLook = { navController.navigate(OutfitBuilderRoute()) },
            onOpenRecommendations = { navController.navigate(RecommendationsRoute) },
            onTryOnOutfit = { id -> navController.navigate(TryOnRoute(outfitId = id)) },
        )
    }

    composable<OutfitBuilderRoute> {
        OutfitBuilderScreen(onBack = { navController.popBackStack() })
    }

    composable<OutfitDetailRoute> { entry ->
        val route = entry.toRoute<OutfitDetailRoute>()
        OutfitDetailScreen(
            onBack = { navController.popBackStack() },
            onRestyle = { navController.navigate(OutfitBuilderRoute(route.outfitId)) },
            onTryOn = { navController.navigate(TryOnRoute(outfitId = route.outfitId)) },
            onDuplicated = { newId ->
                navController.navigate(OutfitDetailRoute(newId)) {
                    popUpTo(OutfitDetailRoute(route.outfitId)) { inclusive = true }
                }
            },
        )
    }

    composable<RecommendationsRoute> {
        RecommendationsScreen(
            onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) },
            onOpenPreferences = { navController.navigate(StylistPreferencesRoute) },
            onOpenPreview = { ids -> navController.navigate(OutfitPreviewRoute(ids.joinToString(","))) },
            onOpenWeatherSettings = { navController.navigate(WeatherSettingsRoute) },
            onOpenWardrobeSync = { navController.navigate(WardrobeSyncRoute) },
            onOpenAiProviders = { navController.navigate(AiProvidersRoute) },
            onOpenCapsules = { navController.navigate(CapsulesRoute) },
            onOpenDuplicates = { navController.navigate(DuplicateGarmentsRoute) },
            onAddGarment = { navController.navigate(ClosetRoute()) },
            onTryOn = { ids -> navController.navigate(TryOnRoute(garmentIds = ids.joinToString(","))) },
        )
    }

    composable<StylistPreferencesRoute> {
        StylistPreferencesScreen()
    }

    composable<OutfitPreviewRoute> {
        OutfitPreviewScreen()
    }

    composable<CapsulesRoute> {
        CapsulesScreen(onBack = { navController.popBackStack() })
    }

    composable<DuplicateGarmentsRoute> {
        DuplicatesScreen(onBack = { navController.popBackStack() })
    }

    outfitsSettingsDestinations(navController)
}

private fun NavGraphBuilder.outfitsSettingsDestinations(navController: NavHostController) {
    composable<WeatherSettingsRoute> {
        WeatherSettingsScreen()
    }

    composable<WardrobeSyncRoute> {
        WardrobeSyncScreen(
            onBack = { navController.popBackStack() },
            onConnectPhone = { navController.navigate(PairingRoute) },
        )
    }

    composable<PairingRoute> {
        PairingScreen(onBack = { navController.popBackStack() })
    }

    composable<AiProvidersRoute> {
        AiProvidersScreen(onBack = { navController.popBackStack() })
    }

    composable<ProfileRoute> {
        ProfileScreen(
            onBack = { navController.popBackStack() },
            onOpenWardrobePreferences = { navController.navigate(StylistPreferencesRoute) },
            onOpenAiProviders = { navController.navigate(AiProvidersRoute) },
            onOpenWardrobeSync = { navController.navigate(WardrobeSyncRoute) },
        )
    }
}

private fun NavGraphBuilder.calendarDestinations() {
    composable<CalendarRoute> {
        CalendarScreen()
    }
}

private fun NavGraphBuilder.statsDestinations(navController: NavHostController) {
    composable<InsightsRoute> {
        InsightsScreen(
            onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) },
            onOpenOutfit = { id -> navController.navigate(OutfitDetailRoute(id)) },
            onOpenStory = { navController.navigate(WardrobeStoryRoute) },
            onOpenHealth = { navController.navigate(WardrobeHealthRoute) },
        )
    }

    composable<WardrobeStoryRoute> {
        StoryScreen(
            onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) },
            onOpenOutfit = { id -> navController.navigate(OutfitDetailRoute(id)) },
        )
    }

    composable<WardrobeHealthRoute> {
        HealthScreen(onOpenGarment = { id -> navController.navigate(GarmentDetailRoute(id)) })
    }
}

/** Phase 9 Trip Intelligence — this module's first screens, reached from
 * Home's upcoming-trip reminder or the "Trips" entry point, not a nav-dock
 * destination (the same "reachable, but not a fifth dock tile" posture
 * Weather Settings already established in Phase 7). */
private fun NavGraphBuilder.tripsDestinations(navController: NavHostController) {
    composable<TripsRoute> {
        TripsScreen(onOpenTrip = { id -> navController.navigate(TripDetailRoute(id)) })
    }

    composable<TripDetailRoute> { entry ->
        val route = entry.toRoute<TripDetailRoute>()
        TripDetailScreen(
            onBack = { navController.popBackStack() },
            onOpenPacking = { id -> navController.navigate(PackingRoute(id)) },
            tripId = route.tripId,
        )
    }

    composable<PackingRoute> {
        PackingScreen(
            onBack = { navController.popBackStack() },
            onTryOnGarment = { id -> navController.navigate(TryOnRoute(garmentIds = id.toString())) },
        )
    }
}

/** Phase 10 — the shared "Try On Me" flow every integration surface above
 * navigates into via [TryOnRoute]'s dual-input shape. [BodyProfileCaptureRoute]
 * is reached from [TryOnRoute]'s own "needs body profile" prompt (and,
 * later, independently from Settings for re-capture); [MaskEditorRoute]
 * from a per-garment "Edit Mask" action inside the try-on canvas itself;
 * [TryOnCompareRoute] (M12) from a per-garment "Compare with Cloud" action. */
private fun NavGraphBuilder.tryOnDestinations(navController: NavHostController) {
    composable<TryOnRoute> {
        TryOnScreen(
            onNeedsBodyProfile = { navController.navigate(BodyProfileCaptureRoute) },
            onEditMask = { garmentId -> navController.navigate(MaskEditorRoute(garmentId.value)) },
            onCompareWithCloud = { garmentId -> navController.navigate(TryOnCompareRoute(garmentId.value)) },
        )
    }

    composable<BodyProfileCaptureRoute> {
        BodyProfileCaptureScreen(
            onBack = { navController.popBackStack() },
            onComplete = { navController.popBackStack() },
        )
    }

    composable<MaskEditorRoute> {
        MaskEditorScreen(onDone = { navController.popBackStack() })
    }

    composable<TryOnCompareRoute> {
        TryOnCompareScreen(onBack = { navController.popBackStack() })
    }
}

/** The Add-to-Wardrobe ingestion flow — a single queue mechanism handles
 * "Take Photo" (a queue of one, enqueued once the camera screen captures a
 * file), "Choose from Gallery"/"Import Multiple" (enqueued directly by
 * `feature:closet`'s `AddToWardrobeSheet` before navigating here), and
 * resuming an import interrupted by an app restart (the queue screen always
 * reads Room, never nav args). [GarmentCaptureRoute] pops itself off the
 * back stack once it hands off to the queue, so Back from the queue returns
 * to wherever "Take Photo" was tapped from, not to the camera. */
private fun NavGraphBuilder.captureDestinations(navController: NavHostController) {
    composable<GarmentCaptureRoute> {
        GarmentCaptureScreen(
            onBack = { navController.popBackStack() },
            onQueued = {
                navController.navigate(GarmentImportQueueRoute) {
                    popUpTo(GarmentCaptureRoute) { inclusive = true }
                }
            },
        )
    }

    composable<GarmentImportQueueRoute> {
        GarmentImportQueueScreen(
            onDone = { navController.popBackStack() },
            onReviewItem = { id -> navController.navigate(GarmentReviewMetadataRoute(id)) },
        )
    }

    composable<GarmentReviewMetadataRoute> {
        GarmentReviewMetadataScreen(onDone = { navController.popBackStack() })
    }
}
