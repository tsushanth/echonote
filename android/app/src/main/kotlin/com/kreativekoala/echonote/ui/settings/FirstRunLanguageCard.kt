package com.kreativekoala.echonote.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.TranscriptionEngine
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.EngineAdvisor

/** "Español (Spanish)", or just "English" when the two names are the same. */
private fun TranscriptionLanguage.label(): String =
    if (nativeName != displayName) "$nativeName ($displayName)" else nativeName

/**
 * Shown once, at the top of the recordings list, until it is confirmed: the language transcripts will be in (the
 * phone's language when supported, else English), a way to change it, one line on the one-time on-phone download, and,
 * for languages where it matters, a hint that Accurate is far better. It never blocks recording or anything else.
 */
@Composable
fun FirstRunLanguageCard(viewModel: LanguageChoiceViewModel = hiltViewModel()) {
    val show by viewModel.showCard.collectAsState()
    if (!show) return
    val language by viewModel.language.collectAsState()
    val engine by viewModel.engine.collectAsState()
    var picking by remember { mutableStateOf(false) }
    val advice = remember(language) { EngineAdvisor.adviceFor(language, viewModel.totalMemBytes) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.first_run_language_title, language.label()),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.first_run_language_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (advice != null) {
                Spacer(Modifier.height(10.dp))
                if (engine == TranscriptionEngine.ACCURATE) {
                    Text(
                        stringResource(R.string.first_run_engine_on, advice.accurateMb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        stringResource(R.string.first_run_engine_hint, language.nativeName, advice.fastErrorPct, advice.accurateErrorPct, advice.accurateMb),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = viewModel::useAccurate) { Text(stringResource(R.string.first_run_engine_use)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.first_run_language_change)) }
                Button(onClick = viewModel::confirm) { Text(stringResource(R.string.first_run_language_ok)) }
            }
        }
    }
    if (picking) {
        LanguagePickerDialog(
            selected = language,
            totalMemBytes = viewModel.totalMemBytes,
            onSelect = { viewModel.choose(it); picking = false },
            onDismiss = { picking = false }
        )
    }
}

/** One line above "Transcribe this recording": which language it will use, and a way to change it before the download starts. */
@Composable
fun TranscribeLanguageLine(viewModel: LanguageChoiceViewModel = hiltViewModel()) {
    val language by viewModel.language.collectAsState()
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.first_run_language_title, language.label()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false)
        )
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.first_run_language_change)) }
    }
    if (picking) {
        LanguagePickerDialog(
            selected = language,
            totalMemBytes = viewModel.totalMemBytes,
            onSelect = { viewModel.choose(it); picking = false },
            onDismiss = { picking = false }
        )
    }
}
