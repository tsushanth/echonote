package com.kreativekoala.echonote.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kreativekoala.echonote.R
import com.kreativekoala.echonote.data.model.Recording
import com.kreativekoala.echonote.ui.theme.*
import com.kreativekoala.echonote.util.DateFormatting
import com.kreativekoala.echonote.util.TimeFormatting

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingRowItem(
    recording: Recording,
    onTap: () -> Unit,
    onFavoriteToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A colored disc carries the recording's status at a glance — replaces
        // the small inline star/sparkle icons that only showed up next to the
        // title with no consistent visual weight.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    when {
                        recording.isEnhanced -> ElectricDim
                        recording.isFavorite -> CoralDim
                        else -> SurfaceRaised
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when {
                    recording.isEnhanced -> Icons.Default.AutoAwesome
                    recording.isFavorite -> Icons.Default.Favorite
                    else -> Icons.Default.Mic
                },
                contentDescription = null,
                tint = when {
                    recording.isEnhanced -> Electric
                    recording.isFavorite -> Coral
                    else -> TextSecondary
                },
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = recording.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = DateFormatting.formatRelativeDate(recording.dateCreated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = recording.formattedDuration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                recording.locationName?.let { loc ->
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = TextTertiary
                    )
                    Text(
                        text = loc,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = TimeFormatting.formatFileSize(recording.fileSize),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextTertiary
                )
                if (recording.isStereo) {
                    Text(
                        text = stringResource(R.string.recording_row_stereo),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary
                    )
                }
                if (recording.transcript != null) {
                    Text(
                        text = stringResource(R.string.recording_row_transcript),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextTertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onFavoriteToggle) {
            Icon(
                imageVector = if (recording.isFavorite) Icons.Default.Favorite
                else Icons.Default.FavoriteBorder,
                contentDescription = if (recording.isFavorite) stringResource(R.string.recording_row_unfavorite) else stringResource(R.string.recording_row_favorite),
                tint = if (recording.isFavorite) Coral else TextTertiary
            )
        }
    }
}
