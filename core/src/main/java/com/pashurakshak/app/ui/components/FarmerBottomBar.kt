package com.pashurakshak.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun FarmerBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        FarmerTab(
            selected = currentRoute == Screen.FarmerHome.route,
            onClick = { navController.navigateToFarmerTab(Screen.FarmerHome.route) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = "Home",
        )
        FarmerTab(
            selected = currentRoute == Screen.MyAnimals.route,
            onClick = { navController.navigateToFarmerTab(Screen.MyAnimals.route) },
            icon = { Icon(Icons.Default.Pets, contentDescription = null) },
            label = "Animals",
        )
        FarmerTab(
            selected = currentRoute == Screen.ReportSickAnimal.route,
            onClick = { navController.navigateToFarmerTab(Screen.ReportSickAnimal.route) },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            label = "Report",
        )
        FarmerTab(
            selected = currentRoute == Screen.MyReports.route,
            onClick = { navController.navigateToFarmerTab(Screen.MyReports.route) },
            icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
            label = "Reports",
        )
        FarmerTab(
            selected = currentRoute == Screen.Alerts.route,
            onClick = { navController.navigateToFarmerTab(Screen.Alerts.route) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = null) },
            label = "Alerts",
        )
    }
}

@Composable
private fun RowScope.FarmerTab(
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

private fun NavHostController.navigateToFarmerTab(route: String) {
    navigate(route) {
        popUpTo(Screen.FarmerHome.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
