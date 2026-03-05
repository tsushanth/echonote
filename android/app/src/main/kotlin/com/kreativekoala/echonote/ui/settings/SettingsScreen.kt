package com.kreativekoala.echonote.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.util.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedFormat by viewModel.defaultFormat.collectAsState()
    val selectedQuality by viewModel.defaultQuality.collectAsState()
    val autoLocation by viewModel.autoLocation.collectAsState()
    val stereoDefault by viewModel.stereoDefault.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    var showAcknowledgments by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }

    if (showPaywall) {
        PaywallScreen(onDismiss = { showPaywall = false })
        return
    }

    if (showAcknowledgments) {
        AcknowledgmentsScreen(onBack = { showAcknowledgments = false })
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            if (!isPremium) {
                // Go Premium button
                Card(
                    onClick = { showPaywall = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Go Premium",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "Unlimited recordings, transcription & more",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            SettingsSectionHeader("Recording Defaults")

            SettingsRow(
                icon = Icons.Default.AudioFile,
                title = "Default Format",
                subtitle = selectedFormat.displayName
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioFormat.entries.forEach { format ->
                        FilterChip(
                            selected = selectedFormat == format,
                            onClick = { viewModel.setFormat(format) },
                            label = { Text(format.extension.uppercase()) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsRow(
                icon = Icons.Default.HighQuality,
                title = "Default Quality",
                subtitle = selectedQuality.displayName
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RecordingQuality.entries.forEach { quality ->
                        FilterChip(
                            selected = selectedQuality == quality,
                            onClick = { viewModel.setQuality(quality) },
                            label = { Text(quality.displayName) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setStereo(!stereoDefault) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stereo Recording", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Record in stereo when available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = stereoDefault, onCheckedChange = { viewModel.setStereo(it) })
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoLocation(!autoLocation) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-tag Location", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automatically name recordings by location",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoLocation, onCheckedChange = { viewModel.setAutoLocation(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader("Storage")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Recordings Storage", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        storageUsed,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader("About")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("ClearVoice Recorder", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Premium.PRIVACY_POLICY_URL)))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PrivacyTip,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Privacy Policy", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Constants.Premium.TERMS_OF_SERVICE_URL)))
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Gavel,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Terms of Service", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAcknowledgments = true }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text("Acknowledgments", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AcknowledgmentsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acknowledgments") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "ClearVoice Recorder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Built with Jetpack Compose, Media3, Room, and Vosk.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Frameworks Used",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            FrameworkRow("Jetpack Compose", "Modern declarative UI toolkit")
            FrameworkRow("Media3 ExoPlayer", "Audio playback engine")
            FrameworkRow("Room", "SQLite database persistence")
            FrameworkRow("Hilt", "Dependency injection framework")
            FrameworkRow("DataStore", "Preferences persistence")
            FrameworkRow("Vosk", "Offline speech recognition")
            FrameworkRow("Google Play Location", "Location-based naming")
            FrameworkRow("MediaCodec", "Audio decoding and editing")
        }
    }
}

@Composable
private fun FrameworkRow(name: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.padding(start = 40.dp)) {
            content()
        }
    }
}
