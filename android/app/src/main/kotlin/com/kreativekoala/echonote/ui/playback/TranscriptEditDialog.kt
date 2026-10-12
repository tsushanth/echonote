package com.kreativekoala.echonote.ui.playback

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kreativekoala.echonote.R

/**
 * What is being corrected: words [first] until [end], or the whole plain text when [first] is -1
 * (older recordings that have no word timings).
 */
data class EditTarget(val first: Int, val end: Int, val text: String)

/** Dialog for correcting one paragraph. Save stays off while the text is blank so a paragraph is never emptied silently. */
@Composable
fun TranscriptEditDialog(target: EditTarget, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember(target) { mutableStateOf(target.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                isError = text.isBlank(),
                supportingText = if (text.isBlank()) ({ Text(stringResource(R.string.edit_blank)) }) else null,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.edit_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) }
        }
    )
}

/** Asks before re-transcribing over text the user corrected by hand. */
@Composable
fun RetranscribeEditsDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_retranscribe_title)) },
        text = { Text(stringResource(R.string.edit_retranscribe_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.edit_retranscribe_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel)) } }
    )
}
