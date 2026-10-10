package com.xiaojoo.lightplayer

import android.content.ComponentName
import androidx.media3.common.AudioAttributes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // ON 而不是 PREFER：只有平台解码器不支持该音轨时才走扩展，避免软解抢硬解增加耗电
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        // 视频页一次滑动就是换一个 period：实测 state=6(SKIPPING_TO_NEXT) 中位 242ms，
        // 那段时间在重开容器、取样本。给下一条预先喂 3 秒，让滑动落在已就绪的数据上。
        player.setPreloadConfiguration(
            ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs= */ 3_000_000L)
        )

        // 播放器池：会话只认一个 Player，所以会话挂的是 HubPlayer，成员在它后面。
        // 现在池里只有一个成员（第一步只求链路通），后面加成员时队列由 Hub 广播保持同构。
        pool = listOf(player)
        mediaSession = MediaSession.Builder(this, HubPlayer(pool)).build()
    }

    private var pool: List<ExoPlayer> = emptyList()

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
