package xyz.pearos.pearboot.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DownloadsScreen(vm: DownloadsViewModel) {

    val ctx = LocalContext.current

    val meta by vm.metadata.collectAsState()
    val checking by vm.checking.collectAsState()
    val downloading by vm.downloading.collectAsState()
    val paused by vm.paused.collectAsState()
    val downloaded by vm.downloadedBytes.collectAsState()
    val progress by vm.progress.collectAsState()
    val speed by vm.speedMbps.collectAsState()

    val animatedSpeed by animateFloatAsState(
        targetValue = speed,
        label = "speedAnim"
    )

    var warningExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 36.dp) // ✅ system clock safe spacing
    ) {

        /* ===== STATUS PILL ===== */
        GlassSurface(
            cornerRadius = 22.dp,
            padding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Checking")
                } else {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Active")
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            meta?.let {
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 32.dp,
                    padding = PaddingValues(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        Text("Release", style = MaterialTheme.typography.titleMedium)

                        Text("Distro: ${it.distro.name}")
                        Text("Arch: ${it.distro.arch}")
                        Text("Version: ${it.version.name}")
                        Text("Size: %.2f GB".format(it.file.size_bytes / 1e9))

                        Text("SHA256", style = MaterialTheme.typography.labelMedium)
                        Text(it.file.sha256, style = MaterialTheme.typography.bodySmall)

                        Spacer(Modifier.height(14.dp))

                        if (downloading || paused) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                            )

                            Text(
                                "%.2f / %.2f GB".format(
                                    downloaded / 1e9,
                                    it.file.size_bytes / 1e9
                                )
                            )

                            AnimatedVisibility(downloading) {
                                Text(
                                    String.format(
                                        "Speed: %.2f MB/s",
                                        animatedSpeed
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        when {
                            downloading -> {
                                OutlinedButton(
                                    onClick = vm::pause,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Pause") }
                            }

                            paused -> {
                                Button(
                                    onClick = { vm.startOrResume(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Resume") }
                            }

                            else -> {
                                Button(
                                    onClick = { vm.startOrResume(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Download") }
                            }
                        }

                        if (downloading || paused) {
                            OutlinedButton(
                                onClick = vm::cancel,
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Cancel") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(90.dp))
        }

        /* ===== WARNING (BOTTOM CENTER) ===== */
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
        ) {
            GlassSurface(
                cornerRadius = 22.dp,
                padding = PaddingValues(14.dp),
                tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                onClick = { warningExpanded = !warningExpanded }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Warning",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    AnimatedVisibility(warningExpanded) {
                        Text(
                            "This app is built only for flashing pearOS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
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
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = RoundedCornerShape(cornerRadius),
        color = tint,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 6.dp
    ) {
        Box(Modifier.padding(padding)) { content() }
    }
}
