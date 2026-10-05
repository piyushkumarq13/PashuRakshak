package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.Vaccination
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppDialog
import com.pashurakshak.app.ui.components.AppDropdownField
import com.pashurakshak.app.ui.components.AppDropdownMenu
import com.pashurakshak.app.ui.components.AppMenuItem
import com.pashurakshak.app.ui.components.AppPickerField
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import androidx.compose.material3.Scaffold
import com.pashurakshak.app.ui.components.LoadingState
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors
import java.util.Calendar
import java.util.TimeZone

private val VACCINE_OPTIONS = listOf(
    "FMD (Foot & Mouth Disease)",
    "HS (Haemorrhagic Septicaemia)",
    "BQ (Blackquarter)",
    "Anthrax",
    "Brucellosis",
    "TT (Tick Typhus)",
    "Enterotoxemia",
    "PPR",
    "CD (Contagious Eplex)",
    "Rabies",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaccinationStatusScreen(
    modifier: Modifier = Modifier,
    viewModel: VaccinationStatusViewModel = viewModel {
        VaccinationStatusViewModel(
            animalRepository = ServiceLocator.animalRepository,
            vaccinationRepository = ServiceLocator.vaccinationRepository,
            alertRepository = ServiceLocator.alertRepository,
            farmerId = SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var editVaccinationId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirmPair by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.notice) {
        state.notice?.let { notice ->
            showAddDialog = false
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = {
            SnackbarHost(snackbarHostState, modifier = Modifier.padding(bottom = AppSpacing.FloatingBar))
        },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.animalCount > 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = AppSpacing.FloatingBar),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Log vaccination")
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenHeader(
                title = "Vaccination",
                subtitle = "Doses given & upcoming due dates",
            )
            when {
                state.isLoading -> LoadingState()
                state.items.isEmpty() -> EmptyState(
                    icon = Icons.Default.Vaccines,
                    title = "No animals yet",
                    message = "Add an animal in My Animals first, then log vaccinations here.",
                )

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(
                            start = AppSpacing.Screen,
                            end = AppSpacing.Screen,
                            top = AppSpacing.XS,
                            bottom = AppSpacing.ListBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.MD),
                    ) {
                        items(state.items, key = { it.vaccination.id }) { item ->
                            VaccinationCard(
                                animal = item.animal,
                                vaccination = item.vaccination,
                                onEdit = { editVaccinationId = item.vaccination.id },
                                onDelete = {
                                    deleteConfirmPair = item.animal.id to item.vaccination.id
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    val editItem = editVaccinationId?.let { id -> state.items.firstOrNull { it.vaccination.id == id } }
    if (editItem != null) {
        AddVaccinationDialog(
            animals = state.animals,
            initialVaccination = editItem.vaccination,
            isSaving = state.isSaving,
            onDismiss = { editVaccinationId = null },
            onConfirm = { animalId, vaccineName, dateGiven, nextDue ->
                viewModel.editVaccination(editItem.vaccination.copy(
                    animalId = animalId, vaccineName = vaccineName, dateGiven = dateGiven, nextDue = nextDue,
                ))
                editVaccinationId = null
            },
        )
    }

    if (showAddDialog) {
        AddVaccinationDialog(
            animals = state.animals,
            isSaving = state.isSaving,
            onDismiss = { if (!state.isSaving) showAddDialog = false },
            onConfirm = { animalId, vaccineName, dateGiven, nextDue ->
                viewModel.addVaccination(animalId, vaccineName, dateGiven, nextDue)
            },
        )
    }

    deleteConfirmPair?.let { (animalId, vaccId) ->
        AppDialog(
            onDismissRequest = { deleteConfirmPair = null },
            title = "Delete vaccination",
            message = "Are you sure you want to delete this vaccination record?",
            icon = Icons.Default.DeleteOutline,
            danger = true,
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteVaccination(vaccId)
                deleteConfirmPair = null
            },
        )
    }
}

@Composable
private fun AddVaccinationDialog(
    animals: List<Animal>,
    initialVaccination: Vaccination? = null,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (animalId: String, vaccineName: String, dateGiven: Long, nextDue: Long) -> Unit,
) {
    var selectedAnimalId by rememberSaveable { mutableStateOf<String?>(initialVaccination?.animalId) }
    var vaccineName by rememberSaveable { mutableStateOf(initialVaccination?.vaccineName ?: "") }
    var dateGiven by rememberSaveable { mutableStateOf<Long?>(initialVaccination?.dateGiven) }
    var nextDue by rememberSaveable { mutableStateOf<Long?>(initialVaccination?.nextDue) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    val isEditing = initialVaccination != null

    AppDialog(
        onDismissRequest = onDismiss,
        title = if (isEditing) "Edit vaccination" else "Log vaccination",
        icon = Icons.Default.Vaccines,
        confirmText = if (isEditing) "Save" else "Add",
        onConfirm = {
            val animalId = selectedAnimalId ?: return@AppDialog
            val given = dateGiven ?: return@AppDialog
            val due = nextDue ?: return@AppDialog
            onConfirm(animalId, vaccineName, given, due)
        },
        confirmEnabled = selectedAnimalId != null &&
            vaccineName.isNotEmpty() &&
            dateGiven != null &&
            nextDue != null,
        confirmLoading = isSaving,
        dismissText = if (isSaving) null else "Cancel",
        content = {
            AppDropdownField(
                label = "Animal",
                options = animals,
                selected = animals.find { it.id == selectedAnimalId },
                optionLabel = { "${it.name} (${it.species})" },
                onSelect = { selectedAnimalId = it.id },
            )
            AppDropdownField(
                label = "Vaccine",
                options = VACCINE_OPTIONS,
                selected = vaccineName.takeIf { it.isNotEmpty() },
                optionLabel = { it },
                onSelect = { vaccineName = it },
            )
            AppPickerField(
                label = "Date given",
                value = dateGiven?.let { formatDateMillis(it) },
                icon = Icons.Default.CalendarMonth,
                placeholder = "Select date given",
                onClick = { pickerTarget = PickerTarget.DATE_GIVEN },
            )
            AppPickerField(
                label = "Next due",
                value = nextDue?.let { formatDateMillis(it) },
                icon = Icons.Default.EventAvailable,
                placeholder = "Select next due",
                onClick = {
                    pickerTarget = PickerTarget.NEXT_DUE
                    if (nextDue == null) {
                        nextDue = dateGiven?.let { defaultNextDue(it) }
                    }
                },
            )
        },
    )

    pickerTarget?.let { target ->
        val initial = when (target) {
            PickerTarget.DATE_GIVEN -> dateGiven ?: todayUtcMillis()
            PickerTarget.NEXT_DUE -> nextDue
                ?: dateGiven?.let { defaultNextDue(it) }
                ?: todayUtcMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initial)

        DatePickerDialog(
            onDismissRequest = { pickerTarget = null },
            shape = MaterialTheme.shapes.extraLarge,
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            confirmButton = {
                Button(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val localMillis = utcMidnightToLocalNoon(utcMillis)
                            when (target) {
                                PickerTarget.DATE_GIVEN -> {
                                    dateGiven = localMillis
                                    nextDue = defaultNextDue(localMillis)
                                }

                                PickerTarget.NEXT_DUE -> nextDue = localMillis
                            }
                        }
                        pickerTarget = null
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("OK", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { pickerTarget = null },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.height(44.dp),
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private enum class PickerTarget { DATE_GIVEN, NEXT_DUE }

private fun todayUtcMillis(): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = System.currentTimeMillis()
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun utcMidnightToLocalNoon(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun defaultNextDue(dateGiven: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = dateGiven
        add(Calendar.YEAR, 1)
    }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaccinationCard(
    animal: Animal,
    vaccination: Vaccination,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val overdue = vaccination.nextDue != null && vaccination.nextDue < System.currentTimeMillis()
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (overdue) AppColors.SoftRed else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.LG),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.SM),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = animal.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = animal.species,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (showMenu) {
                        AppDropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            AppMenuItem(
                                text = "Edit",
                                leadingIcon = Icons.Default.Edit,
                                onClick = { showMenu = false; onEdit() },
                            )
                            AppMenuItem(
                                text = "Delete",
                                leadingIcon = Icons.Default.DeleteOutline,
                                danger = true,
                                onClick = { showMenu = false; onDelete() },
                            )
                        }
                    }
                }
                when {
                    overdue -> StatusPill(
                        text = "Overdue",
                        container = AppColors.SoftRed,
                        content = AppColors.Danger,
                    )
                    vaccination.nextDue != null -> StatusPill(
                        text = "Scheduled",
                        container = AppColors.SoftGreen,
                        content = AppColors.Healthy,
                    )
                    else -> StatusPill(
                        text = "No doses",
                        container = MaterialTheme.colorScheme.surfaceVariant,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(AppColors.SoftTeal),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Vaccines,
                        contentDescription = null,
                        tint = AppColors.Secondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacing.SM))
                Text(
                    text = "${vaccination.vaccineName} — ${formatDateMillis(vaccination.dateGiven)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            vaccination.nextDue?.let { nextDue ->
                StatusPill(
                    text = if (overdue) {
                        "Overdue · ${formatDateMillis(nextDue)}"
                    } else {
                        "Next due · ${formatDateMillis(nextDue)}"
                    },
                    container = if (overdue) AppColors.SoftRed else AppColors.SoftBlue,
                    content = if (overdue) AppColors.Danger else AppColors.Info,
                )
            }
        }
    }
}
