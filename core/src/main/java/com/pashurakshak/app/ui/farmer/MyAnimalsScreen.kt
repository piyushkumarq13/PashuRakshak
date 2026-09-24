package com.pashurakshak.app.ui.farmer

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import com.pashurakshak.app.data.local.Animal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.pashurakshak.app.di.ServiceLocator
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
import com.pashurakshak.app.ui.components.LoadingState
import com.pashurakshak.app.ui.components.ScreenHeader
import com.pashurakshak.app.ui.components.StatusPill
import com.pashurakshak.app.ui.theme.AppColors

private val SPECIES_OPTIONS =
    listOf("Cow", "Buffalo", "Goat", "Sheep", "Pig", "Poultry", "Horse", "Camel")

private fun speciesEmoji(species: String): String = when (species.lowercase()) {
    "cow" -> "🐄"
    "buffalo" -> "🐃"
    "goat" -> "🐐"
    "sheep" -> "🐑"
    "pig" -> "🐖"
    "poultry" -> "🐔"
    "horse" -> "🐴"
    "camel" -> "🐫"
    else -> "🐾"
}

@Composable
fun MyAnimalsScreen(
    onAnimalClick: (animalId: String) -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MyAnimalsViewModel = viewModel {
        MyAnimalsViewModel(
            animalRepository = ServiceLocator.animalRepository,
            reportRepository = ServiceLocator.reportRepository,
            farmerId = SessionManager.farmerId,
        )
    },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showEditDialog by rememberSaveable { mutableStateOf<Animal?>(null) }
    var deleteConfirmId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add animal")
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ScreenHeader(
                title = "My Animals",
                subtitle = if (state.animals.isNotEmpty()) {
                    "${state.animals.size} registered"
                } else {
                    "Tap + to add your first animal"
                },
            )
            state.error?.let { error ->
                ErrorBanner(
                    message = error,
                    onRetry = { viewModel.refresh() },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when {
                state.isLoading -> LoadingState()
                state.animals.isEmpty() -> EmptyState(
                    icon = Icons.Default.Pets,
                    title = "No animals yet",
                    message = "Add your cattle, goats, or poultry to track health and generate QR passports.",
                    actionLabel = "Add animal",
                    onAction = { showAddDialog = true },
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
                        items(state.animals, key = { it.animal.id }) { item ->
                            AnimalCard(
                                item = item,
                                onClick = { onAnimalClick(item.animal.id) },
                                onEdit = { showEditDialog = item.animal },
                                onDelete = { deleteConfirmId = item.animal.id },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog != null) {
        EditAnimalDialog(
            animal = showEditDialog!!,
            onDismiss = { showEditDialog = null },
            onConfirm = { species, name ->
                showEditDialog?.let { animal ->
                    viewModel.editAnimal(animal.copy(species = species, name = name.trim()))
                }
                showEditDialog = null
            },
        )
    }

    if (showAddDialog) {
        AddAnimalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { species, name ->
                viewModel.addAnimal(species, name)
                showAddDialog = false
            },
        )
    }

    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete animal") },
            text = { Text("Are you sure you want to delete this animal? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAnimal(id)
                        deleteConfirmId = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnimalCard(
    item: AnimalWithStatus,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = speciesEmoji(item.animal.species),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.animal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${item.animal.species} · ${item.animal.qrCodeId.takeLast(6)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HealthBadge(status = item.healthStatus)
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
        }
    }
}

@Composable
private fun HealthBadge(status: HealthStatus) {
    val (label, container, content) = when (status) {
        HealthStatus.HEALTHY -> Triple("Healthy", AppColors.SoftGreen, AppColors.Healthy)
        HealthStatus.UNDER_OBSERVATION ->
            Triple("Observation", AppColors.SoftAmber, AppColors.Warning)
    }
    StatusPill(text = label, container = container, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAnimalDialog(
    onDismiss: () -> Unit,
    onConfirm: (species: String, name: String) -> Unit,
) {
    var species by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var speciesExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add animal") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = speciesExpanded,
                    onExpandedChange = { speciesExpanded = it },
                ) {
                    OutlinedTextField(
                        value = species,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Species") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = speciesExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = speciesExpanded,
                        onDismissRequest = { speciesExpanded = false },
                    ) {
                        SPECIES_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option ${speciesEmoji(option)}") },
                                onClick = {
                                    species = option
                                    speciesExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(species, name) },
                enabled = species.isNotEmpty() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add animal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditAnimalDialog(
    animal: Animal,
    onDismiss: () -> Unit,
    onConfirm: (species: String, name: String) -> Unit,
) {
    var species by remember { mutableStateOf(animal.species) }
    var name by remember { mutableStateOf(animal.name) }
    var speciesExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit animal") },
        text = {
            androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExposedDropdownMenuBox(
                    expanded = speciesExpanded,
                    onExpandedChange = { speciesExpanded = it },
                ) {
                    OutlinedTextField(
                        value = species,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Species") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = speciesExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(
                        expanded = speciesExpanded,
                        onDismissRequest = { speciesExpanded = false },
                    ) {
                        SPECIES_OPTIONS.forEach { option ->
                            DropdownMenuItem(
                                text = { Text("$option ${speciesEmoji(option)}") },
                                onClick = {
                                    species = option
                                    speciesExpanded = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(species, name) },
                enabled = species.isNotEmpty() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
