package com.pashurakshak.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun VetBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        VetTab(
            selected = currentRoute == Screen.VetHome.route,
            onClick = { navController.navigateToVetTab(Screen.VetHome.route) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = "Home",
        )
        VetTab(
            selected = currentRoute == Screen.VetCaseQueue.route,
            onClick = { navController.navigateToVetTab(Screen.VetCaseQueue.route) },
            icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = null) },
            label = "Cases",
        )
        VetTab(
            selected = currentRoute == Screen.VetAlerts.route,
            onClick = { navController.navigateToVetTab(Screen.VetAlerts.route) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            label = "Alerts",
        )
    }
}

@Composable
private fun RowScope.VetTab(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
            )
        },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
            selectedTextColor = MaterialTheme.colorScheme.primary,
        ),
        modifier = Modifier.weight(1f),
    )
}

private fun NavHostController.navigateToVetTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo(Screen.VetHome.route) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = route != Screen.VetHome.route
    }
}
