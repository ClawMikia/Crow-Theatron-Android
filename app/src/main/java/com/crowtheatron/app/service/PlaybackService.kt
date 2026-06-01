package com.crowtheatron.app.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.crowtheatron.app.player.PlayerActivity

/**
 * Media3 session service that owns the ExoPlayer instance.
 * Provides media controls in notifications and lock screen automatically.
 */
class PlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private val binder = LocalBinder()

    inner class LocalBinder : android.os.Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        return if (intent?.action == "com.crowtheatron.BIND_LOCAL") binder
        else super.onBind(intent)
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        
        val intent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player!!)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    fun getPlayer(): ExoPlayer? = player

    fun updateNotification(title: String, state: String) {
        // MediaSessionService handles notification automatically.
        // We can use this method later if manual metadata updates are needed.
    }

    companion object {
        const val ACTION_PAUSE = "com.crowtheatron.PAUSE"
        const val ACTION_STOP  = "com.crowtheatron.STOP"
    }
}
