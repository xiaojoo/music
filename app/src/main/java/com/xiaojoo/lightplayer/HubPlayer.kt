package com.xiaojoo.lightplayer

import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.media3.common.MediaMetadata
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.exoplayer.ExoPlayer
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 会话只能挂一个 Player，播放器池要几个实例同时活着（下一条提前解码好，滑动时不必重开解码器）。
 * 这一层把每次调用转给"当前成员"，并把每个成员的回调统一转给会话。
 *
 * 下面这一大片转发不是偷懒没写完：Kotlin 的 `by` 在构造时就把代理存死了（HubDelegationTest 量过），
 * 换 var 不改转发目标，所以只能逐个写。这份实现由 tools_gen_hubplayer.py 从 Player 接口生成，
 * 升级 media3 后重跑那个脚本即可。路由只有三种：
 *   队列/配置类 setter -> 广播给每个成员（保证各成员队列同构，下标语义不变）
 *   监听器 -> 每个成员都挂（换成员时不补挂也不会漏事件）
 *   其余（状态类 getter、播放命令、画面绑定）-> 只给当前成员
 */
@OptIn(androidx.media3.common.util.UnstableApi::class)
class HubPlayer(
    private val members: List<ExoPlayer>
) : Player by members.first() {

    private var current: ExoPlayer = members.first()

    fun member(): ExoPlayer = current

    /** 换到池里的另一个成员；两边都停在各自的位置，这里只管"谁在响、谁在画" */
    fun use(next: ExoPlayer) {
        if (next === current) return
        current = next
    }

    override fun getApplicationLooper(): Looper = current.getApplicationLooper()
    override fun addListener(p0: Player.Listener) { members.forEach { it.addListener(p0) } }
    override fun removeListener(p0: Player.Listener) { members.forEach { it.removeListener(p0) } }
    override fun setMediaItems(p0: List<MediaItem>) { members.forEach { it.setMediaItems(p0) } }
    override fun setMediaItems(p0: List<MediaItem>, p1: Boolean) { members.forEach { it.setMediaItems(p0, p1) } }
    override fun setMediaItems(p0: List<MediaItem>, p1: Int, p2: Long) { members.forEach { it.setMediaItems(p0, p1, p2) } }
    override fun setMediaItem(p0: MediaItem) { members.forEach { it.setMediaItem(p0) } }
    override fun setMediaItem(p0: MediaItem, p1: Long) { members.forEach { it.setMediaItem(p0, p1) } }
    override fun setMediaItem(p0: MediaItem, p1: Boolean) { members.forEach { it.setMediaItem(p0, p1) } }
    override fun addMediaItem(p0: MediaItem) { members.forEach { it.addMediaItem(p0) } }
    override fun addMediaItem(p0: Int, p1: MediaItem) { members.forEach { it.addMediaItem(p0, p1) } }
    override fun addMediaItems(p0: List<MediaItem>) { members.forEach { it.addMediaItems(p0) } }
    override fun addMediaItems(p0: Int, p1: List<MediaItem>) { members.forEach { it.addMediaItems(p0, p1) } }
    override fun moveMediaItem(p0: Int, p1: Int) { members.forEach { it.moveMediaItem(p0, p1) } }
    override fun moveMediaItems(p0: Int, p1: Int, p2: Int) { members.forEach { it.moveMediaItems(p0, p1, p2) } }
    override fun replaceMediaItem(p0: Int, p1: MediaItem) { members.forEach { it.replaceMediaItem(p0, p1) } }
    override fun replaceMediaItems(p0: Int, p1: Int, p2: List<MediaItem>) { members.forEach { it.replaceMediaItems(p0, p1, p2) } }
    override fun removeMediaItem(p0: Int) { members.forEach { it.removeMediaItem(p0) } }
    override fun removeMediaItems(p0: Int, p1: Int) { members.forEach { it.removeMediaItems(p0, p1) } }
    override fun clearMediaItems() { members.forEach { it.clearMediaItems() } }
    override fun isCommandAvailable(p0: Int): Boolean = current.isCommandAvailable(p0)
    override fun canAdvertiseSession(): Boolean = current.canAdvertiseSession()
    override fun getAvailableCommands(): Player.Commands = current.getAvailableCommands()
    override fun prepare() = current.prepare()
    override fun getPlaybackState(): Int = current.getPlaybackState()
    override fun getPlaybackSuppressionReason(): Int = current.getPlaybackSuppressionReason()
    override fun isPlaying(): Boolean = current.isPlaying()
    override fun getPlayerError(): PlaybackException? = current.getPlayerError()
    override fun play() = current.play()
    override fun pause() = current.pause()
    override fun setPlayWhenReady(p0: Boolean) = current.setPlayWhenReady(p0)
    override fun getPlayWhenReady(): Boolean = current.getPlayWhenReady()
    override fun setRepeatMode(p0: Int) { members.forEach { it.setRepeatMode(p0) } }
    override fun getRepeatMode(): Int = current.getRepeatMode()
    override fun setShuffleModeEnabled(p0: Boolean) { members.forEach { it.setShuffleModeEnabled(p0) } }
    override fun getShuffleModeEnabled(): Boolean = current.getShuffleModeEnabled()
    override fun isLoading(): Boolean = current.isLoading()
    override fun seekToDefaultPosition() = current.seekToDefaultPosition()
    override fun seekToDefaultPosition(p0: Int) = current.seekToDefaultPosition(p0)
    override fun seekTo(p0: Long) = current.seekTo(p0)
    override fun seekTo(p0: Int, p1: Long) = current.seekTo(p0, p1)
    override fun getSeekBackIncrement(): Long = current.getSeekBackIncrement()
    override fun seekBack() = current.seekBack()
    override fun getSeekForwardIncrement(): Long = current.getSeekForwardIncrement()
    override fun seekForward() = current.seekForward()
    override fun hasPreviousMediaItem(): Boolean = current.hasPreviousMediaItem()
    override fun seekToPreviousMediaItem() = current.seekToPreviousMediaItem()
    override fun getMaxSeekToPreviousPosition(): Long = current.getMaxSeekToPreviousPosition()
    override fun seekToPrevious() = current.seekToPrevious()
    override fun hasNextMediaItem(): Boolean = current.hasNextMediaItem()
    override fun seekToNextMediaItem() = current.seekToNextMediaItem()
    override fun seekToNext() = current.seekToNext()
    override fun setPlaybackParameters(p0: PlaybackParameters) { members.forEach { it.setPlaybackParameters(p0) } }
    override fun setPlaybackSpeed(p0: Float) { members.forEach { it.setPlaybackSpeed(p0) } }
    override fun getPlaybackParameters(): PlaybackParameters = current.getPlaybackParameters()
    override fun stop() = current.stop()
    override fun release() { members.forEach { it.release() } }
    override fun getCurrentTracks(): Tracks = current.getCurrentTracks()
    override fun getTrackSelectionParameters(): TrackSelectionParameters = current.getTrackSelectionParameters()
    override fun setTrackSelectionParameters(p0: TrackSelectionParameters) { members.forEach { it.setTrackSelectionParameters(p0) } }
    override fun getMediaMetadata(): MediaMetadata = current.getMediaMetadata()
    override fun getPlaylistMetadata(): MediaMetadata = current.getPlaylistMetadata()
    override fun setPlaylistMetadata(p0: MediaMetadata) = current.setPlaylistMetadata(p0)
    override fun getCurrentManifest(): Any? = current.getCurrentManifest()
    override fun getCurrentTimeline(): Timeline = current.getCurrentTimeline()
    override fun getCurrentPeriodIndex(): Int = current.getCurrentPeriodIndex()
    override fun getCurrentWindowIndex(): Int = current.getCurrentWindowIndex()
    override fun getCurrentMediaItemIndex(): Int = current.getCurrentMediaItemIndex()
    override fun getNextWindowIndex(): Int = current.getNextWindowIndex()
    override fun getNextMediaItemIndex(): Int = current.getNextMediaItemIndex()
    override fun getPreviousWindowIndex(): Int = current.getPreviousWindowIndex()
    override fun getPreviousMediaItemIndex(): Int = current.getPreviousMediaItemIndex()
    override fun getCurrentMediaItem(): MediaItem? = current.getCurrentMediaItem()
    override fun getMediaItemCount(): Int = current.getMediaItemCount()
    override fun getMediaItemAt(p0: Int): MediaItem = current.getMediaItemAt(p0)
    override fun getDuration(): Long = current.getDuration()
    override fun getCurrentPosition(): Long = current.getCurrentPosition()
    override fun getBufferedPosition(): Long = current.getBufferedPosition()
    override fun getBufferedPercentage(): Int = current.getBufferedPercentage()
    override fun getTotalBufferedDuration(): Long = current.getTotalBufferedDuration()
    override fun isCurrentWindowDynamic(): Boolean = current.isCurrentWindowDynamic()
    override fun isCurrentMediaItemDynamic(): Boolean = current.isCurrentMediaItemDynamic()
    override fun isCurrentWindowLive(): Boolean = current.isCurrentWindowLive()
    override fun isCurrentMediaItemLive(): Boolean = current.isCurrentMediaItemLive()
    override fun getCurrentLiveOffset(): Long = current.getCurrentLiveOffset()
    override fun isCurrentWindowSeekable(): Boolean = current.isCurrentWindowSeekable()
    override fun isCurrentMediaItemSeekable(): Boolean = current.isCurrentMediaItemSeekable()
    override fun isPlayingAd(): Boolean = current.isPlayingAd()
    override fun getCurrentAdGroupIndex(): Int = current.getCurrentAdGroupIndex()
    override fun getCurrentAdIndexInAdGroup(): Int = current.getCurrentAdIndexInAdGroup()
    override fun getContentDuration(): Long = current.getContentDuration()
    override fun getContentPosition(): Long = current.getContentPosition()
    override fun getContentBufferedPosition(): Long = current.getContentBufferedPosition()
    override fun getAudioAttributes(): AudioAttributes = current.getAudioAttributes()
    override fun setVolume(p0: Float) { members.forEach { it.setVolume(p0) } }
    override fun getVolume(): Float = current.getVolume()
    override fun mute() = current.mute()
    override fun unmute() = current.unmute()
    override fun clearVideoSurface() = current.clearVideoSurface()
    override fun clearVideoSurface(p0: Surface?) = current.clearVideoSurface(p0)
    override fun setVideoSurface(p0: Surface?) = current.setVideoSurface(p0)
    override fun setVideoSurfaceHolder(p0: SurfaceHolder?) = current.setVideoSurfaceHolder(p0)
    override fun clearVideoSurfaceHolder(p0: SurfaceHolder?) = current.clearVideoSurfaceHolder(p0)
    override fun setVideoSurfaceView(p0: SurfaceView?) = current.setVideoSurfaceView(p0)
    override fun clearVideoSurfaceView(p0: SurfaceView?) = current.clearVideoSurfaceView(p0)
    override fun setVideoTextureView(p0: TextureView?) = current.setVideoTextureView(p0)
    override fun clearVideoTextureView(p0: TextureView?) = current.clearVideoTextureView(p0)
    override fun getVideoSize(): VideoSize = current.getVideoSize()
    override fun getSurfaceSize(): Size = current.getSurfaceSize()
    override fun getCurrentCues(): CueGroup = current.getCurrentCues()
    override fun getDeviceInfo(): DeviceInfo = current.getDeviceInfo()
    override fun getDeviceVolume(): Int = current.getDeviceVolume()
    override fun isDeviceMuted(): Boolean = current.isDeviceMuted()
    override fun setDeviceVolume(p0: Int) { members.forEach { it.setDeviceVolume(p0) } }
    override fun setDeviceVolume(p0: Int, p1: Int) { members.forEach { it.setDeviceVolume(p0, p1) } }
    override fun increaseDeviceVolume() = current.increaseDeviceVolume()
    override fun increaseDeviceVolume(p0: Int) = current.increaseDeviceVolume(p0)
    override fun decreaseDeviceVolume() = current.decreaseDeviceVolume()
    override fun decreaseDeviceVolume(p0: Int) = current.decreaseDeviceVolume(p0)
    override fun setDeviceMuted(p0: Boolean) { members.forEach { it.setDeviceMuted(p0) } }
    override fun setDeviceMuted(p0: Boolean, p1: Int) { members.forEach { it.setDeviceMuted(p0, p1) } }
    override fun setAudioAttributes(p0: AudioAttributes, p1: Boolean) { members.forEach { it.setAudioAttributes(p0, p1) } }
}
