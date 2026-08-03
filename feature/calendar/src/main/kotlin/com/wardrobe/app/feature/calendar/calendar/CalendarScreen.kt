package com.wardrobe.app.feature.calendar.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wardrobe.app.core.model.common.GarmentId
import com.wardrobe.app.core.model.common.OutfitId
import com.wardrobe.app.core.ui.components.ConfirmationToastHost
import com.wardrobe.app.core.ui.components.ConfirmationToastState
import com.wardrobe.app.core.ui.components.rememberConfirmationToastState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

private enum class DatePickerPurpose { RESCHEDULE, DUPLICATE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isLogSheetOpen by remember { mutableStateOf(false) }
    var datePickerTarget by remember { mutableStateOf<Pair<DatePickerPurpose, Long>?>(null) }
    val toastState = rememberConfirmationToastState()

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            toastState.show(it)
            viewModel.onToastShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CalendarTopBar(
                state = state,
                onPreviousMonth = { viewModel.onMonthChange(-1) },
                onNextMonth = { viewModel.onMonthChange(1) },
                onToggleViewMode = viewModel::onToggleViewMode,
            )
        },
        floatingActionButton = {
            if (state.viewMode == CalendarViewMode.CALENDAR) {
                FloatingActionButton(onClick = { isLogSheetOpen = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Log what you wore")
                }
            }
        },
    ) { innerPadding ->
        val dayDetailActions =
            DayDetailActions(
                onLogWear = { isLogSheetOpen = true },
                onClearDay = { viewModel.actions.onClearDay(state.selectedDate) },
                onDuplicateDay = { datePickerTarget = DatePickerPurpose.DUPLICATE to 0L },
                onScheduleRecurring = { outfitId ->
                    viewModel.actions.onScheduleRecurringOutfit(OutfitId(outfitId), state.selectedDate)
                },
                onRescheduleEvent = { eventId -> datePickerTarget = DatePickerPurpose.RESCHEDULE to eventId },
                onConfirmWorn = viewModel.actions::onConfirmWorn,
                onDeleteEvent = viewModel.actions::onDeleteEvent,
            )
        CalendarScaffoldContent(
            state = state,
            toastState = toastState,
            onSelectDate = viewModel::onSelectDate,
            dayDetailActions = dayDetailActions,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    CalendarOverlays(
        state = state,
        viewModel = viewModel,
        isLogSheetOpen = isLogSheetOpen,
        onLogSheetDismissed = { isLogSheetOpen = false },
        datePickerTarget = datePickerTarget,
        onDatePickerDismissed = { datePickerTarget = null },
    )
}

@Composable
private fun CalendarScaffoldContent(
    state: CalendarUiState,
    toastState: ConfirmationToastState,
    onSelectDate: (LocalDate) -> Unit,
    dayDetailActions: DayDetailActions,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.viewMode == CalendarViewMode.LIST) {
            WearHistoryList(groups = state.historyByMonth, modifier = Modifier.fillMaxSize())
        } else {
            CalendarBody(
                state = state,
                onSelectDate = onSelectDate,
                dayDetailActions = dayDetailActions,
            )
        }

        ConfirmationToastHost(
            state = toastState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarOverlays(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    isLogSheetOpen: Boolean,
    onLogSheetDismissed: () -> Unit,
    datePickerTarget: Pair<DatePickerPurpose, Long>?,
    onDatePickerDismissed: () -> Unit,
) {
    if (isLogSheetOpen) {
        LogWearSheet(
            savedOutfits = state.savedOutfits,
            closetGarments = state.closetGarments,
            onSelectOutfit = { outfitId ->
                viewModel.actions.onLogOutfitWear(OutfitId(outfitId), state.selectedDate)
                onLogSheetDismissed()
            },
            onSelectGarment = { garmentId ->
                viewModel.actions.onLogGarmentWear(GarmentId(garmentId), state.selectedDate)
                onLogSheetDismissed()
            },
            onDismiss = onLogSheetDismissed,
        )
    }

    datePickerTarget?.let { (purpose, eventId) ->
        CalendarDatePickerDialog(
            onDateSelected = { date ->
                when (purpose) {
                    DatePickerPurpose.RESCHEDULE -> viewModel.actions.onRescheduleEvent(eventId, date)
                    DatePickerPurpose.DUPLICATE -> viewModel.actions.onDuplicateDay(state.selectedDate, date)
                }
                onDatePickerDismissed()
            },
            onDismiss = onDatePickerDismissed,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarTopBar(
    state: CalendarUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onToggleViewMode: () -> Unit,
) {
    TopAppBar(
        title = {
            if (state.viewMode == CalendarViewMode.CALENDAR) {
                val locale = LocalLocale.current.platformLocale
                Text(
                    state.visibleMonth.month.getDisplayName(TextStyle.FULL, locale) +
                        " " +
                        state.visibleMonth.year,
                )
            } else {
                Text("Wear History")
            }
        },
        navigationIcon = {
            if (state.viewMode == CalendarViewMode.CALENDAR) {
                IconButton(onClick = onPreviousMonth) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
                }
            }
        },
        actions = {
            if (state.viewMode == CalendarViewMode.CALENDAR) {
                IconButton(onClick = onNextMonth) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
                }
            }
            val isCalendarView = state.viewMode == CalendarViewMode.CALENDAR
            val toggleIcon = if (isCalendarView) Icons.AutoMirrored.Filled.List else Icons.Filled.CalendarViewMonth
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = toggleIcon,
                    contentDescription = if (isCalendarView) "Switch to list" else "Switch to calendar",
                )
            }
        },
    )
}

@Composable
private fun CalendarBody(
    state: CalendarUiState,
    onSelectDate: (LocalDate) -> Unit,
    dayDetailActions: DayDetailActions,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                MonthGrid(
                    days = state.monthDays,
                    selectedDate = state.selectedDate,
                    onSelectDate = onSelectDate,
                    modifier = Modifier.weight(1f).padding(16.dp),
                )
                DayDetailPanel(
                    date = state.selectedDate,
                    events = state.selectedDayEvents,
                    actions = dayDetailActions,
                    modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()),
                    conflictMessages = state.selectedDayConflictMessages,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                MonthGrid(
                    days = state.monthDays,
                    selectedDate = state.selectedDate,
                    onSelectDate = onSelectDate,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                DayDetailPanel(
                    date = state.selectedDate,
                    events = state.selectedDayEvents,
                    actions = dayDetailActions,
                    modifier = Modifier.fillMaxWidth(),
                    conflictMessages = state.selectedDayConflictMessages,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarDatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onDateSelected(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                }
            }) { Text("Choose") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}
