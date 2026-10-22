package com.pashurakshak.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.pashurakshak.app.navigation.Screen

@Composable
fun FarmerBottomBar(navController: NavHostController) {
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
                selected = currentRoute == Screen.FarmerHome.route,
                onClick = { navController.navigateToFarmerTab(Screen.FarmerHome.route) },
                icon = Icons.Default.Home,
                label = "Home",
            )
            BottomBarTab(
                selected = currentRoute == Screen.AiChat.route,
                onClick = { navController.navigateToFarmerTab(Screen.AiChat.route) },
                icon = Icons.Default.SmartToy,
                label = "Chat",
            )
            BottomBarTab(
                selected = currentRoute == Screen.ReportSickAnimal.route,
                onClick = { navController.navigateToFarmerTab(Screen.ReportSickAnimal.route) },
                icon = Icons.Default.Warning,
                label = "Report",
            )
            BottomBarTab(
                selected = currentRoute == Screen.Profile.route,
                onClick = { navController.navigateToFarmerTab(Screen.Profile.route) },
                icon = Icons.Default.Person,
                label = "Profile",
            )
        }
    }
}

private fun NavHostController.navigateToFarmerTab(route: String) {
    if (currentBackStackEntry?.destination?.route == route) return
    navigate(route) {
        popUpTo(Screen.FarmerHome.route) {
            saveState = true
            inclusive = false
        }
        launchSingleTop = true
        restoreState = route != Screen.FarmerHome.route
    }
}
