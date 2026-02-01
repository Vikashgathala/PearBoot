package xyz.pearos.pearboot.ui.flash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import xyz.pearos.pearboot.R
import xyz.pearos.pearboot.data.DownloadKeys
import xyz.pearos.pearboot.data.downloadStore
import xyz.pearos.pearboot.usb.UsbHelper

@Composable
fun FlashScreen() {

    val ctx = LocalContext.current
    val isConnected by UsbHelper.connected.collectAsState()
    val pendriveName by UsbHelper.name.collectAsState()
    val pendriveSize by UsbHelper.size.collectAsState()
    val needsFormat by UsbHelper.needsFormat.collectAsState()
    val isFormatting by UsbHelper.isFormatting.collectAsState()
    val formatProgress by UsbHelper.formatProgress.collectAsState()

    // Format dialog state
    var showFormatDialog by remember { mutableStateOf(false) }
    var formatError by remember { mutableStateOf<String?>(null) }
    var showFormatErrorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            while (!isConnected) {
                UsbHelper.scan(ctx)
                delay(3000)
            }
        }
    }

    // Download verified state
    var isDownloaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = ctx.downloadStore.data.first()
        isDownloaded = prefs[DownloadKeys.COMPLETED] == true
    }

    // Dummy distro data
    val distroName = "pearOS NiceCore"
    val distroVersion = "2025.12"
    val distroSize = "3.51 GB"

    // Format confirmation dialog
    if (showFormatDialog) {
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    "Format Drive?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Formatting will erase all data on this drive:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pendriveName.ifEmpty { "USB Drive" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "This action cannot be undone. Are you sure you want to continue?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFormatDialog = false
                        UsbHelper.formatDevice { success, message ->
                            if (!success) {
                                formatError = message
                                showFormatErrorDialog = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, Format")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showFormatDialog = false }
                ) {
                    Text("No, Cancel")
                }
            }
        )
    }

    // Format error dialog
    if (showFormatErrorDialog) {
        AlertDialog(
            onDismissRequest = { showFormatErrorDialog = false },
            icon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Format Failed") },
            text = { Text(formatError ?: "Unknown error occurred") },
            confirmButton = {
                Button(onClick = { showFormatErrorDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {

        Spacer(Modifier.height(28.dp))

        /* ===== TOP LEFT AREA ===== */
        Row(verticalAlignment = Alignment.CenterVertically) {

            GlassSurface(
                cornerRadius = 50.dp,
                padding = PaddingValues(0.dp),
                modifier = Modifier.size(44.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Usb, null, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.width(10.dp))

            GlassSurface(
                cornerRadius = 22.dp,
                padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(
                        Modifier
                            .size(8.dp)
                            .background(
                                if (isConnected) Color(0xFF4CAF50)
                                else Color(0xFFF44336),
                                CircleShape
                            )
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(if (isConnected) "Connected" else "Disconnected")
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            /* ===== DISTRO CARD ===== */
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                cornerRadius = 28.dp,
                padding = PaddingValues(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize()
                ) {

                    Image(
                        painter = painterResource(R.drawable.pear_logo),
                        contentDescription = null,
                        modifier = Modifier.size(34.dp)
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(distroName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Version $distroVersion",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            distroSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    /* ===== DOWNLOAD STATUS ===== */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = Color(0xFF4CAF50)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Downloaded", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Icon(
                                Icons.Default.Close,
                                null,
                                tint = Color(0xFFF44336)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Not downloaded", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Icon(
                Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            /* ===== PENDRIVE CARD ===== */
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 28.dp,
                padding = PaddingValues(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        Icon(Icons.Default.Usb, null, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (pendriveName.isNotEmpty()) pendriveName else "No device",
                                style = MaterialTheme.typography.titleMedium
                            )

                            Text(
                                if (pendriveSize.isNotEmpty()) pendriveSize else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    /* ===== FORMAT SECTION (Expandable) ===== */
                    AnimatedVisibility(
                        visible = isConnected && (needsFormat || isFormatting),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(
                            modifier = Modifier.padding(top = 16.dp)
                        ) {
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (isFormatting) {
                                /* ===== FORMATTING IN PROGRESS ===== */
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    CircularProgressIndicator(
                                        progress = if (formatProgress > 0) formatProgress else 0f,
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Formatting...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${(formatProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            } else {
                                /* ===== NEEDS FORMAT MESSAGE ===== */
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Unsupported filesystem",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            "Format to FAT32 to continue",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Button(
                                        onClick = { showFormatDialog = true },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(
                                            horizontal = 16.dp,
                                            vertical = 8.dp
                                        )
                                    ) {
                                        Text("Format")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        /* ===== FLASH BUTTON ===== */
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
        ) {
            GlassSurface(
                cornerRadius = 22.dp,
                padding = PaddingValues(horizontal = 40.dp, vertical = 12.dp)
            ) {
                Text("Flash", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/* ===== GLASS SURFACE ===== */
@Composable
fun GlassSurface(
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