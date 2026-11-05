package com.kreativekoala.echonote.tts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kreativekoala.echonote.MainActivity
import com.kreativekoala.echonote.R

/**
 * Media session and notification for Listen, so headset buttons, the notification shade and the lock screen can
 * pause, resume, skip and stop. Deliberately shows only generic text ("Reading aloud", "Summary"): never a recording
 * title or transcript text on a lock screen. It is not a foreground service (that would add a Play foreground-service
 * type), so Android may stop it if the app is killed in the background.
 */
internal class ListenMediaSession(
    private val context: Context,
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onStop: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
) {
    private val session = MediaSessionCompat(context, "ClearVoiceListen").apply {
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onPlay() = this@ListenMediaSession.onPlay()
            override fun onPause() = this@ListenMediaSession.onPause()
            override fun onStop() = this@ListenMediaSession.onStop()
            override fun onSkipToNext() = this@ListenMediaSession.onNext()
            override fun onSkipToPrevious() = this@ListenMediaSession.onPrevious()
        })
    }

    init {
        instance = this
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.listen_channel_name), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false); lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC }
        )
    }

    fun show(phase: ListenPhase, source: ListenSource) {
        val playing = phase == ListenPhase.Speaking
        val title = context.getString(R.string.listen_media_title)
        val subtitle = context.getString(sourceLabel(source))
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, context.getString(R.string.app_name))
                .build()
        )
        val actions = PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    when (phase) {
                        ListenPhase.Speaking -> PlaybackStateCompat.STATE_PLAYING
                        ListenPhase.Paused -> PlaybackStateCompat.STATE_PAUSED
                        ListenPhase.Preparing -> PlaybackStateCompat.STATE_BUFFERING
                        else -> PlaybackStateCompat.STATE_STOPPED
                    },
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, if (playing) 1f else 0f
                )
                .build()
        )
        session.isActive = true

        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val toggle = if (playing) action(ACTION_PAUSE, R.string.listen_pause, android.R.drawable.ic_media_pause)
        else action(ACTION_PLAY, R.string.listen_resume, android.R.drawable.ic_media_play)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setSilent(true)
            .addAction(action(ACTION_PREVIOUS, R.string.listen_previous, android.R.drawable.ic_media_previous))
            .addAction(toggle)
            .addAction(action(ACTION_NEXT, R.string.listen_next, android.R.drawable.ic_media_next))
            .addAction(action(ACTION_STOP, R.string.listen_stop, android.R.drawable.ic_menu_close_clear_cancel))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) { /* notification permission denied: the media session still works */ }
    }

    fun hide() {
        session.setPlaybackState(
            PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_STOPPED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 0f).build()
        )
        session.isActive = false
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun action(name: String, label: Int, icon: Int): NotificationCompat.Action {
        val intent = Intent(context, ListenActionReceiver::class.java).setAction(name)
        val pi = PendingIntent.getBroadcast(context, name.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Action.Builder(icon, context.getString(label), pi).build()
    }

    private fun sourceLabel(source: ListenSource) = when (source) {
        ListenSource.SUMMARY -> R.string.listen_source_summary
        ListenSource.SEGMENT -> R.string.listen_source_segment
        ListenSource.TRANSCRIPT -> R.string.listen_source_transcript
        ListenSource.PASSAGE -> R.string.listen_source_passage
    }

    fun handle(action: String?) {
        when (action) {
            ACTION_PLAY -> onPlay()
            ACTION_PAUSE -> onPause()
            ACTION_NEXT -> onNext()
            ACTION_PREVIOUS -> onPrevious()
            ACTION_STOP -> onStop()
        }
    }

    companion object {
        const val CHANNEL = "listen_playback"
        const val NOTIFICATION_ID = 7421
        const val ACTION_PLAY = "com.kreativekoala.echonote.listen.PLAY"
        const val ACTION_PAUSE = "com.kreativekoala.echonote.listen.PAUSE"
        const val ACTION_NEXT = "com.kreativekoala.echonote.listen.NEXT"
        const val ACTION_PREVIOUS = "com.kreativekoala.echonote.listen.PREVIOUS"
        const val ACTION_STOP = "com.kreativekoala.echonote.listen.STOP"

        @Volatile var instance: ListenMediaSession? = null
    }
}

/** Receives the notification buttons. Not exported: only our own PendingIntents reach it. */
class ListenActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ListenMediaSession.instance?.handle(intent.action)
    }
}
