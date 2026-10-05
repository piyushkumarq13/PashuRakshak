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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.pashurakshak.app.ui.components.AppDialog
import com.pashurakshak.app.ui.components.AppDropdownField
import com.pashurakshak.app.ui.components.AppDropdownMenu
import com.pashurakshak.app.ui.components.AppMenuItem
import com.pashurakshak.app.ui.components.AppSpacing
import com.pashurakshak.app.ui.components.AppTextField
import com.pashurakshak.app.ui.components.EmptyState
import com.pashurakshak.app.ui.components.ErrorBanner
import androidx.compose.material3.Scaffold
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
    var editAnimalId by rememberSaveable { mutableStateOf<String?>(null) }
    var deleteConfirmId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = AppSpacing.FloatingBar),
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
                    modifier = Modifier.padding(bottom = AppSpacing.SM),
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
                        items(state.animals, key = { it.animal.id }) { item ->
                            AnimalCard(
                                item = item,
                                onClick = { onAnimalClick(item.animal.id) },
                                onEdit = { editAnimalId = item.animal.id },
                                onDelete = { deleteConfirmId = item.animal.id },
                            )
                        }
                    }
                }
            }
        }
    }

    val editAnimal = editAnimalId?.let { id -> state.animals.firstOrNull { it.animal.id == id }?.animal }
    if (editAnimal != null) {
        AnimalFormDialog(
            title = "Edit animal",
            confirmText = "Save",
            initialSpecies = editAnimal.species,
            initialName = editAnimal.name,
            onDismiss = { editAnimalId = null },
            onConfirm = { species, name ->
                viewModel.editAnimal(editAnimal.copy(species = species, name = name.trim()))
                editAnimalId = null
            },
        )
    }

    if (showAddDialog) {
        AnimalFormDialog(
            title = "Add animal",
            confirmText = "Add animal",
            initialSpecies = "",
            initialName = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { species, name ->
                viewModel.addAnimal(species, name)
                showAddDialog = false
            },
        )
    }

    deleteConfirmId?.let { id ->
        AppDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = "Delete animal",
            message = "Are you sure you want to delete this animal? This action cannot be undone.",
            icon = Icons.Default.DeleteOutline,
            danger = true,
            confirmText = "Delete",
            onConfirm = {
                viewModel.deleteAnimal(id)
                deleteConfirmId = null
            },
        )
    }
}

@Composable
private fun AnimalCard(
    item: AnimalWithStatus,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.LG),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = speciesEmoji(item.animal.species),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(modifier = Modifier.width(AppSpacing.MD))
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

@Composable
private fun AnimalFormDialog(
    title: String,
    confirmText: String,
    initialSpecies: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (species: String, name: String) -> Unit,
) {
    var species by rememberSaveable { mutableStateOf(initialSpecies) }
    var name by rememberSaveable { mutableStateOf(initialName) }

    AppDialog(
        onDismissRequest = onDismiss,
        title = title,
        icon = Icons.Default.Pets,
        confirmText = confirmText,
        onConfirm = { onConfirm(species, name) },
        confirmEnabled = species.isNotEmpty() && name.isNotBlank(),
        content = {
            AppDropdownField(
                label = "Species",
                options = SPECIES_OPTIONS,
                selected = species.takeIf { it.isNotEmpty() },
                optionLabel = { "$it ${speciesEmoji(it)}" },
                onSelect = { species = it },
            )
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
            )
        },
    )
}
