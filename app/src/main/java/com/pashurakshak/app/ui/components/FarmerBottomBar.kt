package com.pashurakshak.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun FarmerBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Screen.MyAnimals.route,
            onClick = { navController.navigateToFarmerTab(Screen.MyAnimals.route) },
            icon = { Icon(Icons.Default.Pets, contentDescription = "My Animals") },
            label = { Text("My Animals") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.ReportSickAnimal.route,
            onClick = { navController.navigateToFarmerTab(Screen.ReportSickAnimal.route) },
            icon = { Icon(Icons.Default.Warning, contentDescription = "Report") },
            label = { Text("Report") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.MyReports.route,
            onClick = { navController.navigateToFarmerTab(Screen.MyReports.route) },
            icon = { Icon(Icons.Default.Receipt, contentDescription = "Reports") },
            label = { Text("Reports") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.VaccinationStatus.route,
            onClick = { navController.navigateToFarmerTab(Screen.VaccinationStatus.route) },
            icon = { Icon(Icons.Default.Vaccines, contentDescription = "Vaccination") },
            label = { Text("Vaccination") },
        )
        NavigationBarItem(
            selected = currentRoute == Screen.Alerts.route,
            onClick = { navController.navigateToFarmerTab(Screen.Alerts.route) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
            label = { Text("Alerts") },
        )
    }
}

private fun NavHostController.navigateToFarmerTab(route: String) {
    navigate(route) {
        popUpTo(Screen.FarmerHome.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
