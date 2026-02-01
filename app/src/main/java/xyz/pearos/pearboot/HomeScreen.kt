package xyz.pearos.pearboot.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.pearos.pearboot.ui.downloads.DownloadsScreen
import xyz.pearos.pearboot.ui.downloads.DownloadsViewModel
import xyz.pearos.pearboot.ui.flash.FlashScreen

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun HomeScreen() {

    var selectedIndex by remember { mutableIntStateOf(0) }

    // 🔥 SINGLE, STABLE INSTANCE
    val downloadsVm: DownloadsViewModel = viewModel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) with
                        fadeOut(animationSpec = tween(180))
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 120.dp)
        ) { index ->
            when (index) {
                0 -> DownloadsScreen(vm = downloadsVm)
                1 -> FlashScreen()
                2 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Settings screen (placeholder)")
                }
            }
        }

        BottomSwitcher(
            selectedIndex = selectedIndex,
            onIndexChange = { selectedIndex = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
