package xyz.pearos.pearboot.ui.flash

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import xyz.pearos.pearboot.R
import xyz.pearos.pearboot.data.DownloadKeys
import xyz.pearos.pearboot.data.downloadStore
import xyz.pearos.pearboot.usb.UsbHelper
import java.io.File
import kotlin.math.abs

@Composable
fun FlashScreen() {
    val ctx = LocalContext.current
    val isConnected by UsbHelper.connected.collectAsState()
    val pendriveName by UsbHelper.name.collectAsState()
    val pendriveSize by UsbHelper.size.collectAsState()
    val needsFormat by UsbHelper.needsFormat.collectAsState()
    val isFormatting by UsbHelper.isFormatting.collectAsState()
    val formatProgress by UsbHelper.formatProgress.collectAsState()

    // Flash states
    val isFlashing by UsbHelper.isFlashing.collectAsState()
    val flashProgress by UsbHelper.flashProgress.collectAsState()
    val flashLogs by UsbHelper.flashLogs.collectAsState()
    val flashComplete by UsbHelper.flashComplete.collectAsState()
    val flashError by UsbHelper.flashError.collectAsState()

    // Verification states
    val isVerifying by UsbHelper.isVerifying.collectAsState()
    val verificationProgress by UsbHelper.verificationProgress.collectAsState()

    // Preparation state
    val isPreparing by UsbHelper.isPreparing.collectAsState()

    // Dialog states
    var showFlashConfirmDialog by remember { mutableStateOf(false) }
    var showFlashErrorDialog by remember { mutableStateOf(false) }
    var flashErrorMessage by remember { mutableStateOf("") }

    // Gyro dialog state
    var showGyroDialog by remember { mutableStateOf(false) }

    // Flash button expand state
    var showDownloadWarning by remember { mutableStateOf(false) }

    // Download state
    var isDownloaded by remember { mutableStateOf(false) }
    var isoPath by remember { mutableStateOf<String?>(null) }

    // Determine current phase for UI
    val currentPhase = when {
        isPreparing -> FlashPhase.PREPARING
        isVerifying -> FlashPhase.VERIFYING
        isFlashing -> FlashPhase.WRITING
        flashComplete -> FlashPhase.COMPLETE
        else -> FlashPhase.IDLE
    }

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            while (!isConnected) {
                UsbHelper.scan(ctx)
                delay(3000)
            }
        }
    }

    // Check download status and get ISO path
    LaunchedEffect(Unit) {
        val prefs = ctx.downloadStore.data.first()
        isDownloaded = prefs[DownloadKeys.COMPLETED] == true
        prefs[DownloadKeys.PATH]?.let { path ->
            if (File(path).exists()) {
                isoPath = path
            }
        }
    }

    // Re-check download status periodically
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            val prefs = ctx.downloadStore.data.first()
            isDownloaded = prefs[DownloadKeys.COMPLETED] == true
            prefs[DownloadKeys.PATH]?.let { path ->
                if (File(path).exists()) {
                    isoPath = path
                }
            }
        }
    }

    // Auto-scroll logs
    val logListState = rememberLazyListState()
    LaunchedEffect(flashLogs.size) {
        if (flashLogs.isNotEmpty()) {
            logListState.animateScrollToItem(flashLogs.size - 1)
        }
    }

    // Handle download warning timeout
    LaunchedEffect(showDownloadWarning) {
        if (showDownloadWarning) {
            delay(5000)
            showDownloadWarning = false
        }
    }

    // Distro data
    val distroName = "pearOS NiceCore"
    val distroVersion = "2025.12"
    val distroSize = "3.51 GB"

    // Flash confirmation dialog
    if (showFlashConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFlashConfirmDialog = false },
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
                    "Flash USB Drive?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "This will ERASE ALL DATA on:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        pendriveName.ifEmpty { "USB Drive" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (pendriveSize.isNotEmpty()) {
                        Text(
                            pendriveSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "The drive will be prepared (wiped) and then pearOS will be written to it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This action cannot be undone!",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFlashConfirmDialog = false
                        showGyroDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Yes, Flash")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showFlashConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Flash error dialog
    if (showFlashErrorDialog) {
        AlertDialog(
            onDismissRequest = { showFlashErrorDialog = false },
            icon = {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Flash Failed") },
            text = { Text(flashErrorMessage) },
            confirmButton = {
                Button(onClick = {
                    showFlashErrorDialog = false
                    UsbHelper.resetFlashState()
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Gyroscope dialog for flat surface detection
    if (showGyroDialog) {
        GyroscopeDialog(
            onDismiss = {
                showGyroDialog = false
            },
            onDeviceFlat = {
                showGyroDialog = false
                // Start flashing
                isoPath?.let { path ->
                    UsbHelper.flashImage(path) { success, message ->
                        if (!success) {
                            flashErrorMessage = message
                            showFlashErrorDialog = true
                        }
                    }
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

        // Top status bar
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
            // Distro Card
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

                    Column(modifier = Modifier.weight(1f)) {
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

            // Pendrive Card (Expandable)
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
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

                    // Flash Progress Section
                    AnimatedVisibility(
                        visible = isFlashing || flashComplete || isPreparing || isVerifying,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            // Progress Header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (flashComplete) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        progress = { flashProgress },
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 3.dp,
                                        color = when (currentPhase) {
                                            FlashPhase.PREPARING -> Color(0xFFFF9800)
                                            FlashPhase.WRITING -> MaterialTheme.colorScheme.primary
                                            FlashPhase.VERIFYING -> Color(0xFF2196F3)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        when (currentPhase) {
                                            FlashPhase.PREPARING -> "Preparing Drive..."
                                            FlashPhase.WRITING -> "Writing Image..."
                                            FlashPhase.VERIFYING -> "Verifying..."
                                            FlashPhase.COMPLETE -> "Flash Complete!"
                                            FlashPhase.IDLE -> "Ready"
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = when (currentPhase) {
                                            FlashPhase.PREPARING -> Color(0xFFFF9800)
                                            FlashPhase.COMPLETE -> Color(0xFF4CAF50)
                                            FlashPhase.VERIFYING -> Color(0xFF2196F3)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    if (!flashComplete) {
                                        Text(
                                            when (currentPhase) {
                                                FlashPhase.PREPARING -> "Wiping partition table..."
                                                FlashPhase.WRITING -> "Progress: ${(flashProgress * 100).toInt()}%"
                                                FlashPhase.VERIFYING -> "Verifying: ${(verificationProgress * 100).toInt()}%"
                                                else -> ""
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isFlashing && !flashComplete) {
                                    OutlinedButton(
                                        onClick = { UsbHelper.cancelFlash() },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Cancel", fontSize = 12.sp)
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // Phase indicators
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PhaseIndicator(
                                    label = "Prepare",
                                    isActive = currentPhase == FlashPhase.PREPARING,
                                    isComplete = currentPhase.ordinal > FlashPhase.PREPARING.ordinal,
                                    color = Color(0xFFFF9800)
                                )
                                PhaseIndicator(
                                    label = "Write",
                                    isActive = currentPhase == FlashPhase.WRITING,
                                    isComplete = currentPhase.ordinal > FlashPhase.WRITING.ordinal,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                PhaseIndicator(
                                    label = "Verify",
                                    isActive = currentPhase == FlashPhase.VERIFYING,
                                    isComplete = currentPhase == FlashPhase.COMPLETE,
                                    color = Color(0xFF2196F3)
                                )
                            }

                            Spacer(Modifier.height(12.dp))

                            // Overall progress bar
                            LinearProgressIndicator(
                                progress = { flashProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = when (currentPhase) {
                                    FlashPhase.PREPARING -> Color(0xFFFF9800)
                                    FlashPhase.VERIFYING -> Color(0xFF2196F3)
                                    FlashPhase.COMPLETE -> Color(0xFF4CAF50)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )

                            // Verbose Logs
                            if (flashLogs.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Log",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    LazyColumn(
                                        state = logListState,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(flashLogs) { log ->
                                            Text(
                                                text = log,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp,
                                                color = when {
                                                    log.contains("ERROR") || log.contains("⚠️") ->
                                                        MaterialTheme.colorScheme.error
                                                    log.contains("✓") || log.contains("PASSED") ||
                                                            log.contains("COMPLETE") ->
                                                        Color(0xFF4CAF50)
                                                    log.contains("═") ->
                                                        MaterialTheme.colorScheme.primary
                                                    log.contains("Preparing") || log.contains("Wiping") ->
                                                        Color(0xFFFF9800)
                                                    else ->
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Done button when complete
                            if (flashComplete) {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { UsbHelper.resetFlashState() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Flash Button
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
        ) {
            GlassSurface(
                cornerRadius = 22.dp,
                padding = PaddingValues(0.dp),
                modifier = Modifier
                    .animateContentSize()
                    .clickable(
                        enabled = !isFlashing && !isFormatting && !flashComplete && !isPreparing && !isVerifying
                    ) {
                        when {
                            !isDownloaded || isoPath == null -> {
                                showDownloadWarning = true
                            }
                            !isConnected -> {
                                // Do nothing, device not connected
                            }
                            else -> {
                                showFlashConfirmDialog = true
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .padding(
                            horizontal = if (showDownloadWarning) 24.dp else 40.dp,
                            vertical = 12.dp
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (showDownloadWarning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "First download the pearOS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Text(
                            when {
                                isPreparing -> "Preparing..."
                                isVerifying -> "Verifying..."
                                isFlashing -> "Flashing..."
                                flashComplete -> "Complete"
                                !isConnected -> "No USB"
                                else -> "Flash"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = when {
                                !isConnected -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Flash phase enum
 */
private enum class FlashPhase {
    IDLE,
    PREPARING,
    WRITING,
    VERIFYING,
    COMPLETE
}

/**
 * Phase indicator composable
 */
@Composable
private fun PhaseIndicator(
    label: String,
    isActive: Boolean,
    isComplete: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isComplete -> Color(0xFF4CAF50)
                        isActive -> color
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isComplete) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            } else if (isActive) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = when {
                isComplete -> Color(0xFF4CAF50)
                isActive -> color
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Dialog that uses gyroscope to detect when device is placed flat on a surface
 */
@Composable
fun GyroscopeDialog(
    onDismiss: () -> Unit,
    onDeviceFlat: () -> Unit
) {
    val context = LocalContext.current
    var isFlat by remember { mutableStateOf(false) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }
    var flatCounter by remember { mutableStateOf(0) }

    val requiredFlatReadings = 10

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]

                    val gravity = 9.81f
                    tiltX = x / gravity
                    tiltY = y / gravity

                    val currentlyFlat = abs(x) < 1.5f && abs(y) < 1.5f && z > 8f

                    if (currentlyFlat) {
                        flatCounter++
                        if (flatCounter >= requiredFlatReadings && !isFlat) {
                            isFlat = true
                        }
                    } else {
                        flatCounter = 0
                        isFlat = false
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(
            listener,
            accelerometer,
            SensorManager.SENSOR_DELAY_UI
        )

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    LaunchedEffect(isFlat) {
        if (isFlat) {
            delay(500)
            onDeviceFlat()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                val animatedTiltX by animateFloatAsState(
                    targetValue = tiltX * 30f,
                    animationSpec = tween(100),
                    label = "tiltX"
                )
                val animatedTiltY by animateFloatAsState(
                    targetValue = tiltY * 30f,
                    animationSpec = tween(100),
                    label = "tiltY"
                )

                Icon(
                    Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .graphicsLayer {
                            rotationX = animatedTiltY
                            rotationY = -animatedTiltX
                        },
                    tint = if (isFlat) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                )
            }
        },
        title = {
            Text(
                "Place Device Flat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Place your device on a flat, stable surface to begin flashing.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFlat) Color(0xFF4CAF50).copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val bubbleOffsetX by animateFloatAsState(
                        targetValue = (tiltX * 40f).coerceIn(-35f, 35f),
                        animationSpec = tween(100),
                        label = "bubbleX"
                    )
                    val bubbleOffsetY by animateFloatAsState(
                        targetValue = (tiltY * 40f).coerceIn(-35f, 35f),
                        animationSpec = tween(100),
                        label = "bubbleY"
                    )

                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFlat) Color(0xFF4CAF50).copy(alpha = 0.3f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )

                    Box(
                        modifier = Modifier
                            .offset(x = bubbleOffsetX.dp, y = bubbleOffsetY.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFlat) Color(0xFF4CAF50)
                                else MaterialTheme.colorScheme.primary
                            )
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    if (isFlat) "Device is flat! Starting..." else "Waiting for flat position...",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFlat) Color(0xFF4CAF50)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isFlat) {
                    Spacer(Modifier.height(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Glass Surface composable
 */
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