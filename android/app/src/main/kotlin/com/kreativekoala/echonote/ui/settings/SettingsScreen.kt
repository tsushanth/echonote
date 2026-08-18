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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.AudioFormat
import com.kreativekoala.echonote.data.model.RecordingQuality
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.util.Constants
import androidx.compose.ui.graphics.Color
import com.kreativekoala.crosspromokit.models.AppId
import com.kreativekoala.crosspromokit.view.CrossPromoSection
import com.kreativekoala.paywallkit.models.PaywallFeature
import com.kreativekoala.paywallkit.models.PaywallTheme
import com.kreativekoala.paywallkit.view.PaywallPreview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedFormat by viewModel.defaultFormat.collectAsState()
    val selectedQuality by viewModel.defaultQuality.collectAsState()
    val autoLocation by viewModel.autoLocation.collectAsState()
    val stereoDefault by viewModel.stereoDefault.collectAsState()
    val transcriptionLanguage by viewModel.transcriptionLanguage.collectAsState()
    val storageUsed by viewModel.storageUsed.collectAsState()
    val totalRecordingsCount by viewModel.totalRecordingsCount.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val availableSpace by viewModel.availableSpace.collectAsState()
    val autoCopyTranscript by viewModel.autoCopyTranscript.collectAsState()
    val autoDeleteAudio by viewModel.autoDeleteAudioAfterTranscription.collectAsState()
    val soundEffectsEnabled by viewModel.soundEffectsEnabled.collectAsState()
    val hapticFeedbackEnabled by viewModel.hapticFeedbackEnabled.collectAsState()
    val isPremium by viewModel.isPremium.collectAsState()
    val context = LocalContext.current
    var showAcknowledgments by remember { mutableStateOf(false) }
    var showPaywall by remember { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var showPaywallPreview by remember { mutableStateOf(false) }

    if (showPaywallPreview) {
        PaywallPreview(
            appId = "clearvoice",
            appName = "ClearVoice",
            features = listOf(
                PaywallFeature("\uD83C\uDFA4", "Unlimited Recordings", "Record without limits"),
                PaywallFeature("\uD83D\uDCDD", "Transcription", "Convert speech to text"),
                PaywallFeature("✂\uFE0F", "Audio Editing", "Trim and enhance recordings"),
                PaywallFeature("⚡", "Playback Controls", "Speed adjustment & skip silence"),
                PaywallFeature("\uD83D\uDCC1", "Unlimited Folders", "Organize everything")
            ),
            theme = PaywallTheme(accent = Color(0xFF6C63FF), accent2 = Color(0xFF9C27B0)),
            onDone = { showPaywallPreview = false }
        )
        return
    }

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
            TopAppBar(title = { Text(stringResource(R.string.settings_title)) })
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
                                stringResource(R.string.settings_go_premium),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                stringResource(R.string.settings_premium_subtitle),
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

            SettingsSectionHeader(stringResource(R.string.settings_section_recording_defaults))

            SettingsRow(
                icon = Icons.Default.AudioFile,
                title = stringResource(R.string.settings_default_format),
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
                title = stringResource(R.string.settings_default_quality),
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
                    Text(stringResource(R.string.settings_stereo_recording), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_stereo_subtitle),
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
                    Text(stringResource(R.string.settings_auto_tag_location), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_auto_tag_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoLocation, onCheckedChange = { viewModel.setAutoLocation(it) })
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            SettingsRow(
                icon = Icons.Default.Translate,
                title = "Transcription Language",
                subtitle = transcriptionLanguage.displayName
            ) {
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TranscriptionLanguage.entries.size) { i ->
                        val lang = TranscriptionLanguage.entries[i]
                        FilterChip(
                            selected = transcriptionLanguage == lang,
                            onClick = { viewModel.setTranscriptionLanguage(lang) },
                            label = { Text(lang.displayName) }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoCopyTranscript(!autoCopyTranscript) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-copy Transcript", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Automatically copy transcripts to clipboard",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoCopyTranscript, onCheckedChange = { viewModel.setAutoCopyTranscript(it) })
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoDeleteAudioAfterTranscription(!autoDeleteAudio) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-delete Audio", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Delete audio file after successful transcription to save space",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoDeleteAudio, onCheckedChange = { viewModel.setAutoDeleteAudioAfterTranscription(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader(stringResource(R.string.settings_section_storage))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(storageUsed, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Used", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MicNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$totalRecordingsCount", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Recordings", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(totalDuration, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Total Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (availableSpace.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SdCard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(availableSpace, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                            Text("Free", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader("Feedback")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setSoundEffectsEnabled(!soundEffectsEnabled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Sound Effects", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Play sounds on recording start/stop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = soundEffectsEnabled, onCheckedChange = { viewModel.setSoundEffectsEnabled(it) })
            }

            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setHapticFeedbackEnabled(!hapticFeedbackEnabled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Vibration,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Vibrate on recording start/stop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = hapticFeedbackEnabled, onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) })
            }

            Spacer(modifier = Modifier.height(16.dp))

            SettingsSectionHeader(stringResource(R.string.settings_section_about))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { tapCount++ }
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
                    Text(stringResource(R.string.settings_app_name), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.settings_version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tapCount >= 5) {
                Button(
                    onClick = { showPaywallPreview = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Preview Paywalls")
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
                Text(stringResource(R.string.settings_privacy_policy), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.settings_terms_of_service), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
                Text(stringResource(R.string.settings_acknowledgments), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CrossPromoSection(currentApp = AppId.CLEARVOICE)

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
                title = { Text(stringResource(R.string.acknowledgments_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.acknowledgments_back))
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
                text = stringResource(R.string.acknowledgments_app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.acknowledgments_built_with),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.acknowledgments_frameworks_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            FrameworkRow(stringResource(R.string.acknowledgments_jetpack_compose), stringResource(R.string.acknowledgments_jetpack_compose_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_media3), stringResource(R.string.acknowledgments_media3_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_room), stringResource(R.string.acknowledgments_room_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_hilt), stringResource(R.string.acknowledgments_hilt_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_datastore), stringResource(R.string.acknowledgments_datastore_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_vosk), stringResource(R.string.acknowledgments_vosk_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_location), stringResource(R.string.acknowledgments_location_desc))
            FrameworkRow(stringResource(R.string.acknowledgments_mediacodec), stringResource(R.string.acknowledgments_mediacodec_desc))
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
