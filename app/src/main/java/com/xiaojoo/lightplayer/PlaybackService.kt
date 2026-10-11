package com.xiaojoo.lightplayer

import android.content.ComponentName
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // 池由 PlayerPool 建；会话先挂在第一个成员上，换"眼前这一页"时由 PlayerPool.use() 调
        // MediaSession.setPlayer 转过去（media3 官方给的就是这条转移入口）
        PlayerPool.ensure(this)
        val s = MediaSession.Builder(this, PlayerPool.sessionPlayer()).build()
        mediaSession = s
        PlayerPool.attach(s)
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
