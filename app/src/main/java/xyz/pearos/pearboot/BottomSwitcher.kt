package xyz.pearos.pearboot.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun BottomSwitcher(
    selectedIndex: Int,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme

    val tabWidth = 100.dp
    val tabWidthPx = with(density) { tabWidth.toPx() }

    /* ---------- Bar scale animation ---------- */
    val barScale = remember { Animatable(1f) }

    LaunchedEffect(selectedIndex) {
        barScale.animateTo(
            1.06f,
            animationSpec = spring(stiffness = 420f, dampingRatio = 0.85f)
        )
        delay(90)
        barScale.animateTo(
            1f,
            animationSpec = spring(stiffness = 380f, dampingRatio = 0.9f)
        )
    }

    /* ---------- Pill position ---------- */
    val pillOffsetDp by animateDpAsState(
        targetValue = with(density) {
            (selectedIndex * tabWidthPx).toDp()
        },
        animationSpec = spring(
            stiffness = 280f,
            dampingRatio = 0.75f
        ),
        label = "pillOffset"
    )

    Box(
        modifier = modifier
            .padding(bottom = 28.dp)
            .graphicsLayer {
                scaleX = barScale.value
                scaleY = barScale.value
            }
            .height(70.dp)
            .width(tabWidth * homeTabs.size)
            .clip(RoundedCornerShape(35.dp))
            .background(colors.surfaceContainerHigh)
            .border(
                1.dp,
                colors.outlineVariant,
                RoundedCornerShape(35.dp)
            )
    ) {

        /* ---------- Highlight pill ---------- */
        Box(
            modifier = Modifier
                .offset(x = pillOffsetDp)
                .padding(6.dp)
                .width(tabWidth - 12.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.secondaryContainer)
        )

        /* ---------- Tabs ---------- */
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            homeTabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex

                val contentColor =
                    if (selected) colors.primary
                    else colors.onSurfaceVariant

                Column(
                    modifier = Modifier
                        .width(tabWidth)
                        .clickable(
                            indication = null, // no ripple
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            if (!selected) {
                                view.performHapticFeedback(
                                    HapticFeedbackConstants.KEYBOARD_TAP
                                )
                                onIndexChange(index)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.title,
                        tint = contentColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.title,
                        fontSize = 11.sp,
                        color = contentColor
                    )
                }
            }
        }
    }
}
