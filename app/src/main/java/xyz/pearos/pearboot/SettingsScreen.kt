package xyz.pearos.pearboot.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.pearos.pearboot.R

@Composable
fun SettingsScreen(
    onExpandChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current


    var isVisible by remember { mutableStateOf(false) }
    var isFaqExpanded by remember { mutableStateOf(false) }


    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }


    LaunchedEffect(isFaqExpanded) {
        onExpandChange(isFaqExpanded)
    }


    val logoAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 0),
        label = "logoAlpha"
    )
    val logoScale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "logoScale"
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 100),
        label = "titleAlpha"
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 20f,
        animationSpec = tween(600, delayMillis = 100),
        label = "titleOffset"
    )

    val faqAlpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(600, delayMillis = 200),
        label = "faqAlpha"
    )
    val faqOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else 30f,
        animationSpec = tween(600, delayMillis = 200),
        label = "faqOffset"
    )

    val contributorsAlpha by animateFloatAsState(
        targetValue = if (isVisible && !isFaqExpanded) 1f else 0f,
        animationSpec = tween(if (isFaqExpanded) 200 else 600, delayMillis = if (isFaqExpanded) 0 else 300),
        label = "contributorsAlpha"
    )
    val contributorsOffset by animateFloatAsState(
        targetValue = if (isVisible && !isFaqExpanded) 0f else 30f,
        animationSpec = tween(600, delayMillis = 300),
        label = "contributorsOffset"
    )

    val websiteAlpha by animateFloatAsState(
        targetValue = if (isVisible && !isFaqExpanded) 1f else 0f,
        animationSpec = tween(if (isFaqExpanded) 200 else 600, delayMillis = if (isFaqExpanded) 0 else 400),
        label = "websiteAlpha"
    )
    val websiteOffset by animateFloatAsState(
        targetValue = if (isVisible && !isFaqExpanded) 0f else 30f,
        animationSpec = tween(600, delayMillis = 400),
        label = "websiteOffset"
    )

    val footerAlpha by animateFloatAsState(
        targetValue = if (isVisible && !isFaqExpanded) 1f else 0f,
        animationSpec = tween(if (isFaqExpanded) 200 else 600, delayMillis = if (isFaqExpanded) 0 else 500),
        label = "footerAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(38.dp))


            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                        alpha = logoAlpha
                    }
            ) {
                Image(
                    painter = painterResource(R.drawable.pear_logo),
                    contentDescription = "PearBoot Logo",
                    modifier = Modifier.size(100.dp)
                )
            }

            Spacer(Modifier.height(12.dp))


            Text(
                text = "PearBoot",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .graphicsLayer { translationY = titleOffset }
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "USB Flasher for pearOS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .alpha(titleAlpha)
                    .graphicsLayer { translationY = titleOffset }
            )

            Spacer(Modifier.height(24.dp))


            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isFaqExpanded) Modifier.weight(1f)
                        else Modifier
                    )
                    .alpha(faqAlpha)
                    .graphicsLayer { translationY = faqOffset }
            ) {
                FaqTroubleshootCard(
                    isExpanded = isFaqExpanded,
                    onExpandToggle = { isFaqExpanded = !isFaqExpanded }
                )
            }


            AnimatedVisibility(
                visible = !isFaqExpanded,
                enter = fadeIn(animationSpec = tween(300)) + expandVertically(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(animationSpec = tween(200))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Spacer(Modifier.height(16.dp))


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(contributorsAlpha)
                            .graphicsLayer { translationY = contributorsOffset }
                    ) {
                        ContributorsCard()
                    }

                    Spacer(Modifier.height(16.dp))


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(websiteAlpha)
                            .graphicsLayer { translationY = websiteOffset }
                    ) {
                        GlassSurface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pearos.xyz/"))
                                    context.startActivity(intent)
                                },
                            cornerRadius = 22.dp,
                            padding = PaddingValues(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Visit pearOS Website",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .alpha(footerAlpha)
                            .padding(bottom = 16.dp)
                    ) {
                        Text(
                            text = "Version 1.0",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "© Pear Software and Services S.R.L.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqTroubleshootCard(
    isExpanded: Boolean,
    onExpandToggle: () -> Unit
) {
    val scrollState = rememberScrollState()

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = 300f
                )
            )
            .then(
                if (isExpanded) Modifier.fillMaxHeight()
                else Modifier
            ),
        cornerRadius = 28.dp,
        padding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isExpanded) Modifier.fillMaxHeight()
                    else Modifier
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandToggle() }
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Help,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "FAQ & Troubleshooting",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (isExpanded) "Tap arrow below to collapse" else "Tap to expand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedContent(
                    targetState = isExpanded,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(200)) togetherWith
                                fadeOut(animationSpec = tween(200))
                    },
                    label = "expandIcon"
                ) { expanded ->
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )


                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState)
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {

                        FaqSection(
                            icon = Icons.Default.Info,
                            title = "About PearBoot",
                            content = """
PearBoot is the official USB flashing tool for pearOS. This application allows you to create bootable USB drives directly from your Android device.

⚠️ Important: This app is designed and tested exclusively for flashing pearOS. Using it with other distributions may cause unexpected issues.
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Settings,
                            title = "How It Works",
                            content = """
1. Download: The app downloads the official pearOS ISO image from our servers.

2. Verify: The downloaded file is verified using SHA-256 checksum to ensure integrity.

3. Prepare: Before flashing, the USB drive is wiped to clear any existing filesystem (including APFS, NTFS, ext4, etc.).

4. Flash: The ISO image is written directly to the USB drive as a raw disk image.

5. Verify: The written data is verified against the source to ensure a successful flash.
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Usb,
                            title = "USB Drive Not Detected?",
                            content = """
If your USB drive is not being recognized:

• Try disconnecting and reconnecting the USB drive
• Use a different USB OTG cable or adapter
• Try a different USB port if your device has multiple
• The USB drive may be corrupted - try formatting it on a computer first
• Some USB drives may not be compatible with Android's USB Host mode
• Ensure your Android device supports USB OTG

If the drive still doesn't appear after trying these steps, the drive may be physically damaged or incompatible.
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Warning,
                            title = "Flash Failed or Stuck?",
                            content = """
If flashing fails or gets stuck:

• Do NOT move your device during flashing
• Keep the device on a flat, stable surface
• Ensure sufficient battery (>30% recommended)
• Try using a different USB drive
• Disconnect and reconnect the USB drive, then try again
• Restart the app and try again

If you see "Write failed" errors, the USB drive may be failing or have bad sectors.
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Computer,
                            title = "How to Boot from USB",
                            content = """
After successfully flashing your USB drive:

1. Safely eject the USB drive from your Android device

2. Insert the USB drive into your computer

3. Restart/Power on your computer

4. Access the boot menu:
   • Press F12, F2, F10, Del, or Esc during startup
   • The key varies by manufacturer (shown briefly on screen)

5. Select your USB drive from the boot menu

If the USB doesn't appear in the boot menu:
• Enter BIOS/UEFI settings (usually F2 or Del)
• Look for "Boot Order" or "Boot Priority"
• Enable "USB Boot" if disabled
• Try both UEFI and Legacy boot modes
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Security,
                            title = "Secure Boot Issues",
                            content = """
If your USB won't boot even though it appears in the boot menu:

Disable Secure Boot:
1. Enter BIOS/UEFI settings (F2, Del, or Esc during startup)
2. Find "Secure Boot" option (usually under Security or Boot tabs)
3. Set it to "Disabled"
4. Save and exit (usually F10)

Try Legacy/CSM Mode:
1. In BIOS/UEFI, look for "CSM" or "Legacy Support"
2. Enable Legacy boot mode
3. Some systems need both UEFI and Legacy enabled

After installing pearOS, you can re-enable Secure Boot if needed.
                            """.trimIndent()
                        )


                        FaqSection(
                            icon = Icons.Default.Lightbulb,
                            title = "Additional Tips",
                            content = """
• Use USB 3.0 drives for faster flashing and boot times
• Minimum recommended USB size: 8GB
• Keep a backup of important data before flashing
• If verification fails but flash completes, try booting anyway - it often still works
• For best results, use a high-quality USB drive from a reputable brand

Need more help? Visit our website for documentation and community support.
                            """.trimIndent()
                        )

                        Spacer(Modifier.height(60.dp))
                    }


                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledTonalButton(
                            onClick = onExpandToggle,
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Collapse")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FaqSection(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: String
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
private fun ContributorsCard() {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        padding = PaddingValues(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Contributors",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }


            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "VG",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Vikash Gathala",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Android Developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    padding: PaddingValues = PaddingValues(16.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = tint,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(Modifier.padding(padding)) {
            content()
        }
    }
}