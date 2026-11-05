package com.kreativekoala.echonote.ui.playback

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.service.summary.SummaryKind
import com.kreativekoala.echonote.service.summary.SummaryUiState

/**
 * Adds the collapsible summary card to the playback list. Adds nothing for [SummaryUiState.None].
 * Callers that scroll to list rows by index must count one extra row when this adds one
 * (see [summaryItemCount]).
 */
fun LazyListScope.summaryItem(
    state: SummaryUiState,
    onRefresh: () -> Unit,
    onListen: () -> Unit = {},
    /** Index of the key point being read aloud right now, or -1. */
    spokenPoint: Int = -1
) {
    if (state == SummaryUiState.None) return
    item(key = "summary") { SummaryCard(state, onRefresh, onListen, spokenPoint) }
}

fun summaryItemCount(state: SummaryUiState): Int = if (state == SummaryUiState.None) 0 else 1

@Composable
private fun SummaryCard(state: SummaryUiState, onRefresh: () -> Unit, onListen: () -> Unit, spokenPoint: Int) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    val ready = state as? SummaryUiState.Ready
    val ai = ready?.result?.kind == SummaryKind.ON_DEVICE_AI

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (ai) R.string.summary_title_ai else R.string.summary_title_key_points),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(if (ai) R.string.summary_note_ai else R.string.summary_note_extracted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ready != null) {
                    IconButton(onClick = onListen) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = stringResource(R.string.listen_summary_button))
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Key points", ready.result.asPlainText()))
                        Toast.makeText(context, context.getString(R.string.summary_copied), Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.summary_copy))
                    }
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(if (expanded) R.string.summary_hide else R.string.summary_show)
                    )
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                when (state) {
                    SummaryUiState.Working -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.summary_working), style = MaterialTheme.typography.bodyMedium)
                    }
                    SummaryUiState.TooShort -> Text(
                        stringResource(R.string.summary_too_short),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    is SummaryUiState.Ready -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.result.points.forEachIndexed { index, point ->
                                val spoken = index == spokenPoint
                                val spokenLabel = stringResource(R.string.listen_summary_row_spoken, index + 1)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (spoken) Modifier
                                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                                .semantics { stateDescription = spokenLabel }
                                            else Modifier
                                        )
                                ) {
                                    Text("•", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        point,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (spoken) FontWeight.Bold else FontWeight.Normal,
                                        color = if (spoken) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                        if (state.stale) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.summary_stale),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = onRefresh) { Text(stringResource(R.string.summary_update)) }
                        }
                    }
                    SummaryUiState.None -> Unit
                }
            }
        }
    }
}
