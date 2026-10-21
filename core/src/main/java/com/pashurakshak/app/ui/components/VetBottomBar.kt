package com.pashurakshak.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun VetBottomBar(navController: NavHostController) {
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.75f)),
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            BottomBarTab(
                selected = currentRoute == Screen.VetHome.route,
                onClick = { navController.navigateToVetTab(Screen.VetHome.route) },
                icon = Icons.Default.Home,
                label = "Home",
            )
            BottomBarTab(
                selected = currentRoute?.startsWith(Screen.VetCaseQueue.route) == true,
                onClick = { navController.navigateToVetTab("${Screen.VetCaseQueue.route}?filter=ongoing") },
                icon = Icons.AutoMirrored.Filled.Assignment,
                label = "Cases",
            )
            BottomBarTab(
                selected = currentRoute == Screen.VetAlerts.route,
                onClick = { navController.navigateToVetTab(Screen.VetAlerts.route) },
                icon = Icons.Default.Notifications,
                label = "Alerts",
            )
        }
    }
}

private fun NavHostController.navigateToVetTab(route: String) {
    val current = currentBackStackEntry?.destination?.route ?: ""
    if (current == route) return
    val currentBase = current.substringBefore('?')
    val targetBase = route.substringBefore('?')
    if (currentBase == targetBase && currentBase == Screen.VetCaseQueue.route &&
        route.endsWith("ongoing") && current.contains("ongoing")
    ) return
    navigate(route) {
        popUpTo(Screen.VetHome.route) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = route != Screen.VetHome.route
    }
}
