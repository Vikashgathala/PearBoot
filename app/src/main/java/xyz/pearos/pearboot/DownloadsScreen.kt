package xyz.pearos.pearboot.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DownloadsScreen(
    vm: DownloadsViewModel = viewModel()
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { vm.attachContext(ctx) }

    val metadata by vm.metadata.collectAsState()
    val checking by vm.checking.collectAsState()
    val state by vm.state.collectAsState()
    val downloaded by vm.downloadedBytes.collectAsState()
    val progress by vm.progress.collectAsState()
    val speed by vm.speedMbps.collectAsState()

    var warningExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {

        Spacer(Modifier.height(28.dp))

        /* ===== SERVER STATUS PILL (UNCHANGED) ===== */
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

        Spacer(Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {

            metadata?.let { meta ->
                GlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 32.dp,
                    padding = PaddingValues(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        Text("Releases", style = MaterialTheme.typography.titleMedium, fontSize = 43.sp)
                        Text(meta.distro.name)
                        Text("Version ${meta.version.name}")
                        Text("%.2f GB".format(meta.file.size_bytes / 1e9))

                        /* ===== CHECKSUM (ADDED, NO OTHER CHANGE) ===== */
                        Text(
                            "SHA256",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            meta.file.sha256,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (state == DownloadState.DOWNLOADING || state == DownloadState.VERIFYING) {
                            LinearProgressIndicator(
                                progress = progress,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                "${"%.2f".format(downloaded / 1e9)} / " +
                                        "${"%.2f".format(meta.file.size_bytes / 1e9)} GB"
                            )
                        }

                        AnimatedVisibility(state == DownloadState.DOWNLOADING) {
                            Text("${"%.2f".format(speed)} MB/s")
                        }

                        when (state) {
                            DownloadState.NOT_DOWNLOADED ->
                                Button(
                                    onClick = vm::startOrResume,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Download") }

                            DownloadState.PAUSED ->
                                Button(
                                    onClick = vm::startOrResume,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Resume") }

                            DownloadState.DOWNLOADING ->
                                OutlinedButton(
                                    onClick = vm::pause,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Pause") }

                            DownloadState.VERIFYING ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Verifying")
                                }

                            DownloadState.VERIFIED ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF4CAF50)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Verified")
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(onClick = vm::delete) {
                                        Text("Delete")
                                    }
                                }

                            DownloadState.CORRUPT ->
                                Button(
                                    onClick = vm::delete,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Delete") }
                        }
                    }
                }
            }
        }

        /* ===== WARNING PILL (CENTER BOTTOM, EXPANDABLE) ===== */
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 14.dp)
        ) {
            GlassSurface(
                cornerRadius = 22.dp,
                padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                tint = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                modifier = Modifier.clickable {
                    warningExpanded = !warningExpanded
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Warning",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    AnimatedVisibility(
                        visible = warningExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            "This app is built only for flashing pearOS. Using other distros may cause issues.",
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/* ===== GLASS SURFACE (UNCHANGED) ===== */
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
