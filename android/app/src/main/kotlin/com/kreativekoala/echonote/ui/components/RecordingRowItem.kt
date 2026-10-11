package com.kreativekoala.echonote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.ui.theme.EchoTheme
import com.kreativekoala.echonote.ui.theme.SourceSerif
import com.kreativekoala.echonote.ui.theme.TimerTextStyle
import com.kreativekoala.echonote.util.DateFormatting
import com.kreativekoala.echonote.util.TimeFormatting

/**
 * A list row reads like a contents-page entry: the title, then the first lines of
 * what was actually said, so a recording can be recognised without opening it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingRowItem(
    recording: Recording,
    onTap: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val preview = recording.transcript?.trim()?.takeIf { it.isNotEmpty() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onTap, onLongClick = onLongPress)
            .padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recording.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (preview != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = SourceSerif,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    ),
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = recording.formattedDuration,
                    style = TimerTextStyle.copy(fontSize = 13.sp),
                    color = colors.onSurface
                )
                Text(
                    text = DateFormatting.formatRelativeDate(recording.dateCreated),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Normal,
                    color = colors.onSurfaceVariant
                )
                if (recording.isEnhanced) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.primary
                    )
                }
                recording.locationName?.let { loc ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = EchoTheme.colors.faint
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = loc,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Normal,
                            color = EchoTheme.colors.faint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (recording.isFavorite) Icons.Default.Favorite
                else Icons.Default.FavoriteBorder,
                contentDescription = if (recording.isFavorite) stringResource(R.string.recording_row_unfavorite) else stringResource(R.string.recording_row_favorite),
                tint = if (recording.isFavorite) EchoTheme.colors.record else EchoTheme.colors.faint
            )
        }
    }
}
