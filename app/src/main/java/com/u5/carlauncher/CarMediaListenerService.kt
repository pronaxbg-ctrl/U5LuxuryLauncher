package com.u5.carlauncher

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LiveMediaState(
    val title: String = "Ready for Music",
    val artist: String = "Spotify / YouTube Music",
    val sourceApp: String = "OttoAibox-P3 Pro",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 180_000L,
    val albumArt: Bitmap? = null,
    val hasListenerAccess: Boolean = false,
    val isDemoPlaying: Boolean = false
)

object MediaRepository {
    private val _state = MutableStateFlow(LiveMediaState())
    val state: StateFlow<LiveMediaState> = _state.asStateFlow()

    private var activeController: MediaController? = null

    fun hasNotificationAccess(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun refreshSessions(context: Context) {
        val hasAccess = hasNotificationAccess(context)
        if (!hasAccess) {
            _state.value = _state.value.copy(hasListenerAccess = false)
            return
        }

        try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val component = ComponentName(context, CarMediaListenerService::class.java)
            val controllers = manager.getActiveSessions(component)

            val preferred = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()

            activeController = preferred
            updateFromController(preferred, hasAccess = true)
        } catch (_: SecurityException) {
            _state.value = _state.value.copy(hasListenerAccess = false)
        }
    }

    fun updateFromController(controller: MediaController?, hasAccess: Boolean) {
        if (controller == null) {
            if (_state.value.isDemoPlaying) {
                val nextPos = (_state.value.positionMs + 1000L) % _state.value.durationMs.coerceAtLeast(1L)
                _state.value = _state.value.copy(positionMs = nextPos, hasListenerAccess = hasAccess)
            } else {
                _state.value = _state.value.copy(hasListenerAccess = hasAccess)
            }
            return
        }

        val meta = controller.metadata
        val pb = controller.playbackState
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Now Playing"
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: "Unknown Artist"
        val duration = (meta?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L)
            .takeIf { it > 0L } ?: 180_000L

        val playing = pb?.state == PlaybackState.STATE_PLAYING
        val basePos = pb?.position ?: 0L
        val elapsedSinceUpdate = if (playing && pb != null && pb.lastPositionUpdateTime > 0L) {
            ((SystemClock.elapsedRealtime() - pb.lastPositionUpdateTime) * pb.playbackSpeed).toLong()
        } else {
            0L
        }
        val livePos = (basePos + elapsedSinceUpdate).coerceIn(0L, duration)

        val art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val pkg = controller.packageName
        val sourceLabel = when {
            pkg.contains("spotify", ignoreCase = true) -> "OttoAibox • Spotify"
            pkg.contains("youtube.music", ignoreCase = true) -> "OttoAibox • YT Music"
            pkg.contains("youtube", ignoreCase = true) -> "OttoAibox • YouTube"
            pkg.contains("samsung", ignoreCase = true) -> "OttoAibox • Samsung Music"
            else -> "OttoAibox-P3 Pro"
        }

        _state.value = LiveMediaState(
            title = title,
            artist = artist,
            sourceApp = sourceLabel,
            isPlaying = playing,
            positionMs = livePos,
            durationMs = duration,
            albumArt = art,
            hasListenerAccess = hasAccess,
            isDemoPlaying = false
        )
    }

    fun togglePlayPause(context: Context) {
        val controller = activeController
        if (controller != null) {
            val isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (isPlaying) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
            refreshSessions(context)
        } else {
            val current = _state.value
            val nextDemo = !current.isDemoPlaying
            _state.value = current.copy(
                isDemoPlaying = nextDemo,
                isPlaying = nextDemo,
                positionMs = if (nextDemo) 64_000L else 0L,
                durationMs = 158_000L,
                title = if (nextDemo) "Wave Preview Active" else "Ready for Music",
                artist = if (nextDemo) "Play Spotify or YT Music" else "Spotify / YouTube Music"
            )
        }
    }

    fun seekToFraction(context: Context, fraction: Float) {
        val duration = _state.value.durationMs.coerceAtLeast(1L)
        val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
        activeController?.transportControls?.seekTo(targetMs)
        _state.value = _state.value.copy(positionMs = targetMs)
        refreshSessions(context)
    }

    fun skipNext(context: Context) {
        activeController?.transportControls?.skipToNext()
        refreshSessions(context)
    }

    fun skipPrevious(context: Context) {
        activeController?.transportControls?.skipToPrevious()
        refreshSessions(context)
    }
}

class CarMediaListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        MediaRepository.refreshSessions(this)
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        MediaRepository.refreshSessions(this)
    }

    override fun onNotificationRemoved(sbn: android.service.notification.StatusBarNotification?) {
        MediaRepository.refreshSessions(this)
    }
}
