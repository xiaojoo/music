package com.xiaojoo.lightplayer

import android.content.ComponentName
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 池由 PlayerPool 建（成员表要给视频页直接读），会话只认一个 Player，挂的是包好的门面
        mediaSession = MediaSession.Builder(this, PlayerPool.ensure(this)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 会话先放，成员后放：反过来会话那侧的监听会打到已经 release 的播放器上
        mediaSession?.run {
            release()
            mediaSession = null
        }
        PlayerPool.release()
        super.onDestroy()
    }
}
