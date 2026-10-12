package com.kreativekoala.echonote.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.repository.LiveTranscriptSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiveTranscriptSettingViewModel @Inject constructor(
    private val settings: LiveTranscriptSettings
) : ViewModel() {
    val enabled: StateFlow<Boolean> = settings.enabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setEnabled(value: Boolean) {
        viewModelScope.launch { settings.setEnabled(value) }
    }
}

/** "Live transcript while recording" switch. Hidden below Android 10, where it cannot run safely. */
@Composable
fun LiveTranscriptSettingRow(viewModel: LiveTranscriptSettingViewModel = hiltViewModel()) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val enabled by viewModel.enabled.collectAsState()
    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.setEnabled(!enabled) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Subtitles,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_live_transcript_title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.settings_live_transcript_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = enabled, onCheckedChange = { viewModel.setEnabled(it) })
    }
}
