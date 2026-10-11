package com.xiaojoo.lightplayer

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * 会话只把命令交给"当前那一个"播放器 —— 用 `MediaSession.setPlayer` 换成员之后，
 * 另外两个成员不会跟着变。这一层盯着每个成员的队列和配置事件，把它们同步给其余成员，
 * 保证三边的下标语义一致：**视频页"第 p 页 = 队列第 p 条"这条约定全靠它**。
 *
 * 幂等靠"比过再写"，不靠一次性标志：ExoPlayer 的事件是 post 到应用线程的，
 * 用 busy 标志挡住再放开，挡不住"事件在我们放开之后才到"这一种 ——
 * 那一档实测会让复制动作自己再触发一轮复制（同族坑：预备动作的幂等键必须是"已提交的目标"）。
 * 比过再写则天然收敛：复制过去那份和源头已经相等，源那头回来时一条都不动。
 */
@OptIn(UnstableApi::class)
class QueueMirror(private val members: List<ExoPlayer>) {

    fun install() {
        members.forEach { m -> m.addListener(listenerFor(m)) }
    }

    private fun listenerFor(src: ExoPlayer) = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) copyQueueFrom(src)
        }

        override fun onPlaybackParametersChanged(params: PlaybackParameters) {
            others(src) { if (it.playbackParameters != params) it.setPlaybackParameters(params) }
        }

        override fun onRepeatModeChanged(mode: Int) {
            others(src) { if (it.repeatMode != mode) it.setRepeatMode(mode) }
        }

        override fun onShuffleModeEnabledChanged(on: Boolean) {
            others(src) { if (it.shuffleModeEnabled != on) it.setShuffleModeEnabled(on) }
        }

        override fun onVolumeChanged(volume: Float) {
            others(src) { if (it.volume != volume) it.setVolume(volume) }
        }

        override fun onTrackSelectionParametersChanged(p: TrackSelectionParameters) {
            others(src) { if (it.trackSelectionParameters != p) it.setTrackSelectionParameters(p) }
        }
    }

    private inline fun others(src: ExoPlayer, block: (ExoPlayer) -> Unit) {
        members.forEach { if (it !== src) runCatching { block(it) } }
    }

    private fun copyQueueFrom(src: ExoPlayer) {
        val items = ArrayList<MediaItem>(src.mediaItemCount)
        for (i in 0 until src.mediaItemCount) items.add(src.getMediaItemAt(i))
        others(src) { m ->
            if (hasQueueAs(m, items)) return@others
            /* 保住的是"它正在放的那一条"，不是那个下标：删一条会让它后面的整体前移一位，
               照旧下标写会把两个成员摆到同一条上（删第 0 条那回实测落成 183/183/185，
               上一条那一格和当前格画的是同一支片子）。新队列里找不着它（就是被删的那条）
               才退回按夹过的下标落位。 */
            val mine = m.currentMediaItem?.localConfiguration?.uri
            val at = if (mine == null) -1 else items.indexOfFirst { it.localConfiguration?.uri == mine }
            val keep = if (at >= 0) at
                else m.currentMediaItemIndex.coerceAtMost((items.size - 1).coerceAtLeast(0))
            m.setMediaItems(items, keep, 0L)
            m.prepare()
        }
    }
}

/**
 * 这个播放器手里是不是已经逐条同 uri 的这一串队列。
 *
 * 镜像和 [PlayerPool.setQueue] 共用这一个判据：`setMediaItems` 会把时间线整个换掉，
 * 已经拿着同一串的成员再写一次等于白拆一次已解码的状态（视频页进来那一下实测就有两个成员
 * 被这样重写了两次 —— 会话侧的事件是同步嵌着往下传的，镜像的级写会抢在 setQueue 的循环之前
 * 把另外两个填好，循环再挨个写就是第二遍）。
 */
internal fun hasQueueAs(p: Player, items: List<MediaItem>): Boolean {
    if (p.mediaItemCount != items.size) return false
    for (i in items.indices) {
        if (p.getMediaItemAt(i).localConfiguration?.uri != items[i].localConfiguration?.uri) return false
    }
    return true
}
