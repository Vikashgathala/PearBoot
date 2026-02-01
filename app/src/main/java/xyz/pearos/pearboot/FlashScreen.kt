package xyz.pearos.pearboot.ui.flash

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    LaunchedEffect(isConnected) {
        if (!isConnected) {
            while (!isConnected) {
                UsbHelper.scan(ctx)
                delay(3000)
            }
        }
    }

    /* ---------- USB STATE ---------- */
    val pendriveName by UsbHelper.name.collectAsState()
    val pendriveSize by UsbHelper.size.collectAsState()

    /* ---------- DOWNLOAD VERIFIED STATE ---------- */
    var isDownloaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = ctx.downloadStore.data.first()
        isDownloaded = prefs[DownloadKeys.COMPLETED] == true
    }

    /* ---------- DUMMY DISTRO DATA ---------- */
    val distroName = "pearOS NiceCore"
    val distroVersion = "2025.12"
    val distroSize = "3.51 GB"

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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                cornerRadius = 28.dp,
                padding = PaddingValues(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Icon(Icons.Default.Usb, null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(14.dp))

                    Column {
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
