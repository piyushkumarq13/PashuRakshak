package com.pashurakshak.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuBoxScope
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pashurakshak.app.ui.theme.AppColors

/** Rounded, card-like container used by every popup menu in the app. */
@Composable
fun appMenuShape() = MaterialTheme.shapes.medium

@Composable
fun appMenuBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = appMenuShape(),
        containerColor = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        border = appMenuBorder(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExposedDropdownMenuBoxScope.AppExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        matchAnchorWidth = true,
        shape = appMenuShape(),
        containerColor = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        border = appMenuBorder(),
        content = content,
    )
}

@Composable
fun AppMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    selected: Boolean = false,
    danger: Boolean = false,
) {
    val color = when {
        danger -> MaterialTheme.colorScheme.error
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = color,
            )
        },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            null
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(
            textColor = color,
            leadingIconColor = color,
            trailingIconColor = color,
        ),
        modifier = modifier.height(52.dp),
    )
}

@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: ImageVector? = null,
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    confirmLoading: Boolean = false,
    showConfirm: Boolean = true,
    dismissText: String? = "Cancel",
    onCancel: () -> Unit = onDismissRequest,
    content: (@Composable () -> Unit)? = null,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val accentContainer = if (danger) AppColors.SoftRed else MaterialTheme.colorScheme.primaryContainer

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        icon = {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon ?: Icons.Default.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(26.dp),
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Column(
                modifier = if (content != null) {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                },
                verticalArrangement = Arrangement.spacedBy(AppSpacing.LG),
            ) {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                content?.invoke()
            }
        },
        confirmButton = {
            if (showConfirm) {
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled && !confirmLoading,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = accent),
                modifier = Modifier.height(44.dp),
            ) {
                if (confirmLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmText, style = MaterialTheme.typography.labelLarge)
                }
            }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.height(44.dp),
                ) {
                    Text(dismissText, style = MaterialTheme.typography.labelLarge)
                }
            }
        },
    )
}

/**
 * Labelled picker: rounded anchor field + [AppExposedDropdownMenu] with a check on the
 * selected row. Keeps every selector in the app visually identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppDropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        AppExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                val isSelected = option == selected
                AppMenuItem(
                    text = optionLabel(option),
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    selected = isSelected,
                )
            }
        }
    }
}

@Composable
fun AppPickerField(    label: String,
    value: String?,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Tap to select",
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.LG, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.XS),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(AppSpacing.SM))
            Text(
                text = value ?: placeholder,
                style = MaterialTheme.typography.bodyLarge,
                color = if (value != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
