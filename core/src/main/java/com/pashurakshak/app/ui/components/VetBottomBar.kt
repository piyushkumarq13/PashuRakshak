package com.pashurakshak.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun VetBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    NavigationBar(
        modifier = Modifier.fillMaxWidth(),
    ) {
        NavigationBarItem(
            selected = currentRoute == Screen.VetCaseQueue.route,
            onClick = { navController.navigateToVetTab(Screen.VetCaseQueue.route) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = "Case Queue",
                )
            },
            label = { Text("Case Queue") },
            modifier = Modifier.weight(1f),
        )
        NavigationBarItem(
            selected = currentRoute == Screen.VetAlerts.route,
            onClick = { navController.navigateToVetTab(Screen.VetAlerts.route) },
            icon = { Icon(Icons.Default.Notifications, contentDescription = "Alerts") },
            label = { Text("Alerts") },
            modifier = Modifier.weight(1f),
        )
    }
}

private fun NavHostController.navigateToVetTab(route: String) {
    navigate(route) {
        popUpTo(Screen.VetHome.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
