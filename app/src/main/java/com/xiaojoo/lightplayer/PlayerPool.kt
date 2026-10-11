package com.xiaojoo.lightplayer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/**
 * 视频页的播放器池：正在看的那一条 + 上一条 + 下一条各占一个实例，每条片子一块自己的画面。
 *
 * 为什么三个实例：单实例时整屏只有一块共用 surface，下一条还没出第一帧的那一段
 * （实测换条目 130–390ms）里，surface 上留的就是上一条的最后一帧 —— 那一下"闪"是架构自带的，
 * 藏不掉（藏了就黑，露了就串）。每页一块画面之后，滑出去那页继续露它自己那一格的活画面，
 * 滑进来那条由提前解码好的实例直接给第一帧。
 *
 * 会话侧不再包门面：`MediaSession` 只认一个 Player，这里在换"眼前这一页"时直接
 * `session.setPlayer(成员)`（media3 官方给的就是这条，用于把播放从一个实例转移到另一个实例）。
 * 代价是会话只会把命令交给当前那一个播放器，其余成员的队列/配置得靠 [QueueMirror] 跟着同步。
 */
@OptIn(UnstableApi::class)
object PlayerPool {

    /** 3 = 眼前 + 上一条 + 下一条。再多这台机的并发硬解不一定给，也没页可摆 */
    const val SIZE = 3

    private var localMembers: List<ExoPlayer> = emptyList()
    private var session: MediaSession? = null
    private var current: ExoPlayer? = null
    private var mirror: QueueMirror? = null

    fun members(): List<ExoPlayer> = localMembers

    /** 当前"出声 + 被会话控制"的那一个。UI 换它要走 [use]，别自己改 */
    fun current(): ExoPlayer? = current

    /** 给会话用的初始播放器。刻意只暴露 Player，让服务侧不必碰 ExoPlayer 这个非稳定类型 */
    fun sessionPlayer(): Player = localMembers.first()

    /** 服务起来时调；已经建好就原样复用（切后台/前台会重建服务，池不能跟着重建） */
    fun ensure(context: Context) {
        if (localMembers.isNotEmpty()) return
        localMembers = (1..SIZE).map { newPlayer(context) }
    }

    /** 会话建好后调一次：认下会话，并装上队列镜像 */
    fun attach(s: MediaSession) {
        session = s
        current = localMembers.firstOrNull()
        mirror = QueueMirror(localMembers).also { it.install() }
    }

    /**
     * 把会话转到池里的另一个成员。顺序是**先放旧的再换**：ExoPlayer 在 pause 时交回音频焦点，
     * 反过来就会有两个实例同时抢焦点（抢输的那个会被压成"被抑制"，表现是按了没声）。
     * 会话自己会从新播放器重建 PlayerInfo，所以不需要额外补发状态。
     */
    fun use(next: ExoPlayer) {
        val cur = current
        if (cur === next) return
        cur?.pause()
        current = next
        session?.setPlayer(next)
    }

    /**
     * 整串换队列（视频页进来时那一下）。**同步**打到每个成员：
     * 走 MediaController 是异步的，上一轮就因此踩坑 —— 落位逻辑在"成员手里还没队列"时去 seek，
     * 那一下被夹到第 0 条，于是邻页把第 0 条的画面画了出来。
     * 已经拿着这一串的成员跳过不写，理由见 [hasQueueAs]。
     */
    fun setQueue(items: List<MediaItem>) {
        localMembers.forEach { p ->
            if (hasQueueAs(p, items)) return@forEach
            /* 和 QueueMirror 同一条规矩：换队列不换掉它手里正拿着的那一条，
               否则队列少一条之后按旧下标落位，两格会拿到同一支片子。找不到才按夹过的下标落。 */
            val mine = p.currentMediaItem?.localConfiguration?.uri
            val at = if (mine == null) -1 else items.indexOfFirst { it.localConfiguration?.uri == mine }
            val keep = if (at >= 0) at
                else p.currentMediaItemIndex.coerceAtMost((items.size - 1).coerceAtLeast(0))
            p.setMediaItems(items, keep, 0L)
            p.prepare()
        }
    }

    /** 服务销毁时调：成员表清空，UI 读到空列表就退回"正在连接播放服务…" */
    fun release() {
        mirror = null
        session = null
        current = null
        val ms = localMembers
        localMembers = emptyList()
        ms.forEach { runCatching { it.release() } }
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
                // 队列在三个成员之间是镜像同步的，只给自己后面那一条喂 2 秒；
                // 预览成员本来就不出声，喂多了只是三份缓冲
                setPreloadConfiguration(ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs = */ 2_000_000L))
            }
    }
}
