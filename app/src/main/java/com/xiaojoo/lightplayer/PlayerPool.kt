package com.xiaojoo.lightplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * 视频页的播放器池：正在看的那一条 + 上一条 + 下一条各占一个实例，每条片子一块自己的画面。
 *
 * 为什么非要三个实例：单实例时整屏只有一块共用 surface，下一条还没出第一帧的那一段
 * （实测换条目 130–390ms）里，surface 上留的就是上一条的最后一帧 —— 那一下"闪"是这套架构
 * 自带的，藏不掉（要么黑要么串画面，两头他都驳回过）。每页一块画面之后，滑出去的那一页
 * 继续露它自己那一格的活画面，滑进来的那一页由提前解码好的实例直接给第一帧。
 *
 * 会话只认一个 Player，所以成员由 HubPlayer 包住挂上会话；UI 侧拿到的是 MediaController
 * （会话的代理），够不到成员本身 —— 每页绑自己的 TextureView 必须拿到真 ExoPlayer，
 * 成员表因此在同进程这份单例里留一份给 UI 直接读。
 */
@OptIn(UnstableApi::class)
object PlayerPool {

    /** 3 = 眼前 + 上一条 + 下一条。再多这台机的并发硬解不一定给，也没页可摆 */
    const val SIZE = 3

    private var localHub: HubPlayer? = null
    private var localMembers: List<ExoPlayer> = emptyList()

    fun hub(): HubPlayer? = localHub

    fun members(): List<ExoPlayer> = localMembers

    /** 服务起来时调；已经建好就原样交回（切后台/前台会重建服务，池不能跟着重建） */
    fun ensure(context: Context): HubPlayer {
        localHub?.let { return it }
        localMembers = (1..SIZE).map { newPlayer(context) }
        val h = HubPlayer(localMembers)
        localHub = h
        return h
    }

    private fun newPlayer(context: Context): ExoPlayer {
        // ON 而不是 PREFER：只有平台解码器不支持该音轨时才走 FFmpeg，避免软解抢硬解增加耗电
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // 三个实例同时活着，缓冲得收紧 —— DefaultLoadControl 的默认时长是给"一个实例吃满整机"定的。
        // 本地文件走 localPlayback 那一档（这库里已经把本地和在线分开计了）
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMsForLocalPlayback(
                /* minBufferMs = */ 3_000,
                /* maxBufferMs = */ 15_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterSeekMs = */ 2_000
            )
            .build()
        return ExoPlayer.Builder(context)
            .setRenderersFactory(renderersFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                // 队列在三个成员之间是广播的，只给自己后面那一条喂 2 秒；
                // 预览成员本来就不出声，喂多了只是三份缓冲
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs = */ 2_000_000L))
            }
    }

    /** 服务销毁时先释放成员再清空：UI 那边读到空表就退回"正在连接播放服务…" */
    fun release() {
        localHub = null
        val ms = localMembers
        localMembers = emptyList()
        ms.forEach { runCatching { it.release() } }
    }
}
