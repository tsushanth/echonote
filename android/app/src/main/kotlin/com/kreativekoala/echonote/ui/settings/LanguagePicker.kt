package com.kreativekoala.echonote.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.LanguageListing
import com.kreativekoala.echonote.service.LanguageRow
import com.kreativekoala.echonote.service.LanguageSupport
import com.kreativekoala.echonote.service.SupportLevel

/**
 * The language list: native name first, a Supported or Experimental badge, the download size before anything is
 * downloaded, and a plain note when this phone has too little memory for the best model. Languages that did not reach
 * the bar are listed at the end, greyed, with what is closest.
 */
@Composable
fun LanguagePickerDialog(
    selected: TranscriptionLanguage,
    totalMemBytes: Long,
    onSelect: (TranscriptionLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val rows = remember(totalMemBytes) { LanguageListing.build(totalMemBytes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language_picker_title)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                    Text(
                        stringResource(R.string.language_picker_intro),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(rows.size) { i ->
                    val row = rows[i]
                    val section = sectionOf(row)
                    Column {
                        if (section != lastSectionOf(rows, i)) {
                            Text(
                                text = stringResource(sectionTitle(section)),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                            )
                        }
                        LanguageRowView(row, selected, onSelect)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.language_picker_close)) } }
    )
}

private fun sectionOf(row: LanguageRow): Int = when (row) {
    is LanguageRow.Available -> if (row.level == SupportLevel.SUPPORTED) 0 else 1
    is LanguageRow.NeedsBiggerPhone -> 2
    is LanguageRow.NotYet -> 3
}

/** The section of the row before [i], or -1 for the first row, so a heading appears where the section changes. */
private fun lastSectionOf(rows: List<LanguageRow>, i: Int): Int = if (i == 0) -1 else sectionOf(rows[i - 1])

private fun sectionTitle(section: Int): Int = when (section) {
    0 -> R.string.language_section_supported
    1 -> R.string.language_section_experimental
    2 -> R.string.language_section_bigger_phone
    else -> R.string.language_section_not_yet
}

@Composable
private fun LanguageRowView(row: LanguageRow, selected: TranscriptionLanguage, onSelect: (TranscriptionLanguage) -> Unit) {
    when (row) {
        is LanguageRow.Available -> {
            val lang = row.language
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.RadioButton) { onSelect(lang) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.RadioButton(selected = lang == selected, onClick = null)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lang.nativeName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (lang.nativeName != lang.displayName) {
                            Text(lang.displayName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Badge(row.level)
                    }
                    val accurate = if (row.largeDownload) R.string.language_accurate_size_large else R.string.language_accurate_size
                    Detail(stringResource(accurate, row.pick.engine.displayName, row.pick.engine.downloadMb))
                    when {
                        row.fastMb != null && row.fastUsable -> Detail(stringResource(R.string.language_fast_size, row.fastMb))
                        !lang.hasVosk -> Detail(stringResource(R.string.language_fast_unavailable))
                        !row.fastUsable -> Detail(stringResource(R.string.language_fast_poor))
                    }
                    if (row.downgraded) Detail(stringResource(R.string.language_ram_smaller))
                    if (LanguageSupport.hasDialectWarning(lang)) Detail(stringResource(R.string.language_dialect_warning, lang.displayName))
                }
            }
        }
        is LanguageRow.NeedsBiggerPhone -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
            Text(row.language.nativeName + "  " + row.language.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Detail(stringResource(R.string.language_ram_needs_bigger, row.best.engine.displayName, row.best.engine.downloadMb))
        }
        is LanguageRow.NotYet -> Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp)) {
            val e = row.entry
            Text(e.nativeName + "  " + e.displayName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val closest = e.closest
            Detail(
                if (closest != null) stringResource(R.string.language_not_yet_closest, e.bestErrorPct.toInt(), closest.displayName)
                else stringResource(R.string.language_not_yet_none, e.bestErrorPct.toInt())
            )
        }
    }
}

@Composable
private fun Detail(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Badge(level: SupportLevel) {
    val experimental = level == SupportLevel.EXPERIMENTAL
    Surface(
        color = if (experimental) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Text(
            text = stringResource(if (experimental) R.string.language_badge_experimental else R.string.language_badge_supported),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
