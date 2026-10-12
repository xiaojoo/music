package com.xiaojoo.lightplayer.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.xiaojoo.lightplayer.videosource.AggregateSearcher
import com.xiaojoo.lightplayer.videosource.LocalStubSource
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.Playable
import com.xiaojoo.lightplayer.videosource.ResolveOutcome
import com.xiaojoo.lightplayer.videosource.RetryPolicy
import com.xiaojoo.lightplayer.videosource.SearchPolicy
import com.xiaojoo.lightplayer.videosource.SearchReport
import com.xiaojoo.lightplayer.videosource.SourceRegistry
import com.xiaojoo.lightplayer.videosource.SourceResolver

/**
 * 在线那一路的入口：源表 + 两条桥（条目 → MediaItem、MediaItem → Track）。
 *
 * 为什么要 MediaItem → Track 这一颗桥：视频页的页表、队列面板、历史、收藏全按 uri 认条目，
 * 一条不在本地扫描结果里的 http 条目会被 `queuePages` 判成「队列装的不是这批视频」，
 * 于是整页退回本地列表（真机量的：队列里混进一条外来的，页表就整个换掉）。
 * 给它造一份和库条目同形状的 Track，那几处就不用各自加特例。
 *
 * 现在的源是本机静态服务的占位源（tools/serve_online_dev.sh 起服务 + adb reverse），
 * 换真站点的写法是在 registry 里再注册一个 MusicVideoSource，别处不动。
 */
object Online {

    /** mediaId 的前缀：远端条目靠它认出自己是哪一路，不靠 uri 形状猜 */
    const val ID_PREFIX = "online:"

    private const val DEV_BASE = "http://127.0.0.1:8777"

    /** 时长是真量的（ffmpeg -i 那批样片），不是编的；标题如实写成样片，不冒充某个 MV */
    private val devCatalog = listOf(
        MusicVideo("dev", "dy", "本机样片 dy", "本机服务", 4_000, "$DEV_BASE/dy"),
        MusicVideo("dev", "f1", "本机样片 f1", "本机服务", 6_310, "$DEV_BASE/f1"),
        MusicVideo("dev", "s1", "本机样片 s1", "本机服务", 5_390, "$DEV_BASE/s1"),
        MusicVideo("dev", "t", "本机样片 t", "本机服务", 2_370, "$DEV_BASE/t"),
    )

    private val stub = LocalStubSource(catalog = devCatalog, baseUrl = DEV_BASE)

    val registry = SourceRegistry(listOf(stub))

    private val searcher = AggregateSearcher(registry)
    private val resolver = SourceResolver(registry, RetryPolicy(attempts = 2, baseDelayMs = 300))

    private val policy = SearchPolicy(defaultTimeoutMs = 6_000, firstResultWaitMs = 6_000, graceMs = 1_500)

    suspend fun search(query: String): SearchReport = searcher.search(query, policy)

    suspend fun resolve(video: MusicVideo): ResolveOutcome = resolver.resolve(video, timeoutMs = 6_000)

    fun mediaItem(video: MusicVideo, playable: Playable): MediaItem = MediaItem.Builder()
        .setMediaId(ID_PREFIX + video.key)
        .setUri(playable.url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setArtist(video.artist)
                .setDurationMs(video.durationMs)
                .build()
        )
        .build()

    /** 不是在线条目就回 null，让调用方退回原来的本地判断，别在这里兜底造一条假的 */
    fun trackOf(item: MediaItem): Track? {
        val id = item.mediaId?.takeIf { it.startsWith(ID_PREFIX) } ?: return null
        val raw = item.localConfiguration?.uri?.toString() ?: return null
        val md = item.mediaMetadata
        return Track(
            id = id.hashCode().toLong(),
            uri = Uri.parse(raw),
            name = md.title?.toString() ?: "在线条目",
            artist = md.artist?.toString(),
            durationMs = md.durationMs ?: -1L,
            // 在线条目没有"文件大小"这个数，0 会让视频页那行副标题写出 0 KB；用 -1 让它不出现在串里
            sizeBytes = -1L,
            isVideo = true
        )
    }

    fun isOnline(track: Track): Boolean {
        val s = track.uri.scheme
        return s == "http" || s == "https"
    }
}
