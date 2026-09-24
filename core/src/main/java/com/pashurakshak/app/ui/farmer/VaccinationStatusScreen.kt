package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pashurakshak.app.data.SessionManager
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.Vaccination
import com.pashurakshak.app.ui.components.AccentDot
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
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
    bottomBar: @Composable () -> Unit = {},
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
    var showEditDialog by rememberSaveable { mutableStateOf<VaccinationListItem?>(null) }
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
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (state.animalCount > 0) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
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
                        contentPadding = PaddingValues(
                            start = AppSpacing.Screen,
                            end = AppSpacing.Screen,
                            top = 4.dp,
                            bottom = AppSpacing.ListBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.items, key = { it.vaccination.id }) { item ->
                            VaccinationCard(
                                animal = item.animal,
                                vaccination = item.vaccination,
                                onEdit = { showEditDialog = item },
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

    if (showEditDialog != null) {
        val initial = showEditDialog?.vaccination
        if (initial != null) {
            AddVaccinationDialog(
                animals = state.animals,
                initialVaccination = initial,
                isSaving = state.isSaving,
                onDismiss = { showEditDialog = null },
                onConfirm = { animalId, vaccineName, dateGiven, nextDue ->
                    viewModel.editVaccination(initial.copy(
                        animalId = animalId, vaccineName = vaccineName, dateGiven = dateGiven, nextDue = nextDue,
                    ))
                    showEditDialog = null
                },
            )
        }
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
        AlertDialog(
            onDismissRequest = { deleteConfirmPair = null },
            title = { Text("Delete vaccination") },
            text = { Text("Are you sure you want to delete this vaccination record?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteVaccination(vaccId)
                        deleteConfirmPair = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmPair = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddVaccinationDialog(
    animals: List<Animal>,
    initialVaccination: Vaccination? = null,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (animalId: String, vaccineName: String, dateGiven: Long, nextDue: Long) -> Unit,
) {
    var selectedAnimalId by remember { mutableStateOf<String?>(initialVaccination?.animalId) }
    var vaccineName by remember { mutableStateOf(initialVaccination?.vaccineName ?: "") }
    var dateGiven by remember { mutableStateOf<Long?>(initialVaccination?.dateGiven) }
    var nextDue by remember { mutableStateOf<Long?>(initialVaccination?.nextDue) }
    var animalExpanded by remember { mutableStateOf(false) }
    var vaccineExpanded by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf<PickerTarget?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialVaccination != null) "Edit vaccination" else "Log vaccination") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = animalExpanded,
                    onExpandedChange = { animalExpanded = it },
                ) {
                    val selected = animals.find { it.id == selectedAnimalId }
                    OutlinedTextField(
                        value = selected?.let { "${it.name} (${it.species})" } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Animal") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = animalExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = animalExpanded,
                        onDismissRequest = { animalExpanded = false },
                    ) {
                        animals.forEach { animal ->
                            DropdownMenuItem(
                                text = { Text("${animal.name} (${animal.species})") },
                                onClick = {
                                    selectedAnimalId = animal.id
                                    animalExpanded = false
                                },
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = vaccineExpanded,
                    onExpandedChange = { vaccineExpanded = it },
                ) {
                    OutlinedTextField(
                        value = vaccineName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vaccine") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = vaccineExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = vaccineExpanded,
                        onDismissRequest = { vaccineExpanded = false },
                    ) {
                        VACCINE_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    vaccineName = option
                                    vaccineExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { pickerTarget = PickerTarget.DATE_GIVEN },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = dateGiven?.let { "Date given: ${formatDateMillis(it)}" }
                            ?: "Select date given",
                    )
                }
                OutlinedButton(
                    onClick = {
                        pickerTarget = PickerTarget.NEXT_DUE
                        if (nextDue == null) {
                            nextDue = dateGiven?.let { defaultNextDue(it) }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = nextDue?.let { "Next due: ${formatDateMillis(it)}" }
                            ?: "Select next due",
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val animalId = selectedAnimalId ?: return@Button
                    val given = dateGiven ?: return@Button
                    val due = nextDue ?: return@Button
                    onConfirm(animalId, vaccineName, given, due)
                },
                enabled = selectedAnimalId != null &&
                    vaccineName.isNotEmpty() &&
                    dateGiven != null &&
                    nextDue != null &&
                    !isSaving,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(if (initialVaccination != null) "Save" else "Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
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
            confirmButton = {
                TextButton(
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
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { pickerTarget = null }) {
                    Text("Cancel")
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

// DatePicker returns UTC midnight; rebuild it as local noon so the formatted day never shifts.
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (overdue) AppColors.SoftRed else MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        androidx.compose.material3.DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                onClick = { showMenu = false; onEdit() },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
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
                AccentDot(color = AppColors.Primary)
                Spacer(modifier = Modifier.width(10.dp))
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
