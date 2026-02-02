package xyz.pearos.pearboot.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class HomeTab(
    val title: String,
    val icon: ImageVector
) {
    object Downloads : HomeTab("Downloads", Icons.Default.Download)
    object Flash : HomeTab("Flash", Icons.Default.FlashOn)
    object Settings : HomeTab("Settings", Icons.Default.Settings)
}

val homeTabs = listOf(
    HomeTab.Downloads,
    HomeTab.Flash,
    HomeTab.Settings
)