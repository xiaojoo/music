package com.xiaojoo.lightplayer

import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.xiaojoo.lightplayer.data.Appearance
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.ui.IconKind
import com.xiaojoo.lightplayer.ui.tap
import com.xiaojoo.lightplayer.ui.LineIcon
import com.xiaojoo.lightplayer.ui.AudioQuality
import com.xiaojoo.lightplayer.ui.clockOf
import com.xiaojoo.lightplayer.ui.Lrc
import com.xiaojoo.lightplayer.ui.LyricLine
import com.xiaojoo.lightplayer.ui.LyricPage
import com.xiaojoo.lightplayer.ui.lyricFor
import com.xiaojoo.lightplayer.ui.LyricStore
import com.xiaojoo.lightplayer.ui.MusicOnlinePage
import com.xiaojoo.lightplayer.ui.Skin
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.ui.ModeSheet
import com.xiaojoo.lightplayer.ui.ScrubBar
import com.xiaojoo.lightplayer.ui.SpeedSheet
import com.xiaojoo.lightplayer.ui.Tokens
import com.xiaojoo.lightplayer.ui.Turntable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * 音乐那一屏。**绑的是音频那一路的播放器，不是会话** —— 会话会被视频那一路借走（视频页要拿它
 * 控制出声的成员），跟着会话走的话，正在放视频时切到这一屏就会把视频的海报、名字、进度画在
 * 音乐页上（他报的「音乐界面怎么还是显示视频海报」）。
 * 这一屏不碰视频：没有渲染面、不显示视频信息。要动音频就是动音频那台。
 *
 * [onTakeover] 在每一次"真的去操作"之前调一下：把会话交给音频那台（顺带把在放的视频按停）。
 * 只是看着这一屏不会调它 —— 所以视频在放、他切到音乐 tab 浏览，视频一格不停。
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    audio: ExoPlayer?,
    onTakeover: () -> Unit,
    onSettings: () -> Unit,
    onOpenQueue: () -> Unit,
    // 右滑到的第三页（在线）：状态都在壳层，这一屏只画和回动作 —— 和歌词那页一样不带自己的存档
    /** 最火/相关那四栏都从"搜当前这首歌"那一批结果里分：两族各按播放数降序，前 3 张最火、第 4 条往后相关 */
    onlineHotMusic: List<MusicVideo>,
    onlineMoreMusic: List<MusicVideo>,
    onlineHotVideo: List<MusicVideo>,
    onlineMoreVideo: List<MusicVideo>,
    onlineError: String?,
    onlinePlayingId: String?,
    onOnlinePlay: (MusicVideo) -> Unit,
    /** 音乐那一行右端那颗：挂到音频那一路的队尾 */
    onOnlineQueue: (MusicVideo) -> Unit,
    /** 音乐那一行那颗「存盘」，和正在存盘的那一条的 key */
    onOnlineDownload: (MusicVideo) -> Unit,
    onlineDownloadingKey: String?,
    /** 滑到这一页、或换了歌，就问一次「这首歌」；壳层里按关键词去重。这一屏上没有那颗按钮 */
    onOnlineSongsFetch: () -> Unit,
    /** 那一栏里的视频：不在这播，挪到视频 tab 的在线那一栏去播 */
    onOpenVideo: (MusicVideo) -> Unit,
    /** 右上角那颗「画幅」：开的是中间那一格换什么的那一整屏（黑胶唱机 / 歌曲海报 / 自选图片） */
    onOpenArt: () -> Unit,
    /** 播放页那三页当前在哪一页：从视频 tab 按系统返回键退回来时，要落回「在线」那一页 */
    startPage: Int,
    onPage: (Int) -> Unit,
    onNowPlaying: (Uri?) -> Unit
) {
    val context = LocalContext.current

    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var dragging by remember { mutableStateOf(false) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }
    var title by remember { mutableStateOf<String?>(null) }
    var artist by remember { mutableStateOf<String?>(null) }
    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var modeSheet by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var trackWarning by remember { mutableStateOf<String?>(null) }
    var lyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var lyricSource by remember { mutableStateOf<String?>(null) }
    // 选歌词目录那一下系统把授权存了下来，但界面这份状态每次重组都是新的 —— 冷启动要读回来，
    // 不然前一晚选的文件夹早上一起来就当没选过（见 LyricStore）
    var lyricTreeUri by remember { mutableStateOf(LyricStore.load(context)) }
    // 播放页那一格音质：取播放器真在解的那一条（见 AudioQuality），不是我们向站点要的那一档
    var quality by remember { mutableStateOf<String?>(null) }


    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            // 读 + 写一起要：这一格既是歌词的来路，也是存盘歌曲的歌词的去处（只给读，写歌词就只会报"没存下"）
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            LyricStore.save(context, uri)
            lyricTreeUri = uri
        }
    }

    val playerListener = remember(audio) {
        object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                durationMs = audio?.duration?.takeIf { it > 0 } ?: 0L
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "${error.errorCodeName}: ${error.message}"
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                errorMessage = null
                quality = null
                title = titleOf(audio, context)
                artist = audio?.mediaMetadata?.artist?.toString()
                currentUri = item?.localConfiguration?.uri
                trackWarning = audio?.let { unsupportedTrackOf(it) }
                // 历史不在这里记：这一屏只有切到「音乐」页签才在组合里，记在这儿等于别的屏点的歌都不算
                // （2026-10-28 挪到 MainActivity 的壳层，见那边那条 DisposableEffect）
            }

            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                title = titleOf(audio, context)
                artist = metadata.artist?.toString() ?: artist
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                trackWarning = audio?.let { unsupportedTrackOf(it) }
                quality = audio?.let { qualityOf(it) }
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }
        }
    }

    DisposableEffect(audio) {
        val c = audio
        if (c == null) return@DisposableEffect onDispose { }
        c.addListener(playerListener)
        isPlaying = c.isPlaying
        playbackState = c.playbackState
        speed = c.playbackParameters.speed
        durationMs = c.duration.takeIf { it > 0 } ?: 0L
        repeatMode = c.repeatMode
        title = titleOf(c, context)
        artist = c.mediaMetadata.artist?.toString()
        currentUri = c.currentMediaItem?.localConfiguration?.uri
        onDispose { c.removeListener(playerListener) }
    }

    LaunchedEffect(audio, isPlaying, dragging) {
        if (dragging) return@LaunchedEffect
        while (true) {
            positionMs = audio?.currentPosition ?: 0L
            /* 音质也跟着这一趟刷，不只挂在 onTracksChanged 上：换条目时我们把那一格清过，
               而两条都是 mp3/44.1k 时 Media3 认为"轨没变"、不再发 tracks 事件 —— 只等事件的话
               在线那一条会一直空着（真机 10-14 量到：本地那条出字、点在线那条不出）。
               同一个字符串写进 mutableStateOf 不引起重组，这一趟是免费的。 */
            audio?.let { val q = qualityOf(it); if (q != quality) quality = q }
            delay(if (isPlaying) 500L else 1000L)
        }
    }

    LaunchedEffect(currentUri, lyricTreeUri) {
        val uri = currentUri
        if (uri == null) {
            lyrics = emptyList()
            lyricSource = null
            return@LaunchedEffect
        }
        val hit = withContext(Dispatchers.IO) { lyricFor(context, uri, lyricTreeUri, title, artist) }
        lyrics = hit.text?.let { Lrc.parse(it) }.orEmpty()
        lyricSource = hit.status
    }

    LaunchedEffect(Unit) {
    }

    LaunchedEffect(currentUri) {
        onNowPlaying(currentUri)
        val uri = currentUri ?: return@LaunchedEffect
        cover = loadCover(context, uri)
        // 「播放画幅」那一屏是盖在这一屏上面的整屏子页，它看不到这一格的局部状态，从镜子拿
        Skin.cover.value = cover
    }

    val pagerState = rememberPagerState(initialPage = startPage, pageCount = { 3 })

    /* 外面改了 startPage 要跟着翻：通知上那颗「歌词」落下来就是改它（播放页一直挂着时
       initialPage 只在第一次组合有用，它不会回头看那个参数）。 */
    LaunchedEffect(startPage) {
        if (pagerState.currentPage != startPage) pagerState.scrollToPage(startPage)
    }

    /* 画幅档开着：整屏背景是那张图，这一屏的字全走白的那一套。
       第三页（在线）不算：那一屏是列表，栏头那几行压在图上读不清，它照旧用页底那档灰。 */
    val onArt = Tokens.artOn && pagerState.currentPage != 2

    /* 画幅铺到状态栏和导航条底下，那两条的图标深浅就归这一屏管（和视频那一屏同一个走法）。
       不用去量那张图有多亮：图上那层纱是 InkStrong 按 ART_SCRIM_ALPHA 盖下去的，图再白，
       混完也不超过 113 —— 白字一定赢，所以 onArt 一律按白字。PlayerArtTest 扫 0..255 钉这一条。 */
    DisposableEffect(onArt, Tokens.LightSystemIcons) {
        val ctl = (context as? android.app.Activity)?.window
            ?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val light = if (onArt) false else Tokens.LightSystemIcons
        ctl?.isAppearanceLightStatusBars = light
        ctl?.isAppearanceLightNavigationBars = light
        onDispose {
            val back = Tokens.LightSystemIcons
            ctl?.isAppearanceLightStatusBars = back
            ctl?.isAppearanceLightNavigationBars = back
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val art = Tokens.artBitmap
        if (onArt && art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Tokens.ArtScrim.copy(alpha = Appearance.ART_SCRIM_ALPHA))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Tokens.playerBackground())
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Spacer(Modifier.height(12.dp))

            // 第三页是滑进来的，不给它另摆一颗键 —— 和歌词同一只入口（ dots + 右滑 ）
            /* 只在**滑到这一页**那一下问一次。这一屏上点封面、展开、暂停都不该再发搜索请求（他点名的），
               所以这里刻意不跟 currentUri —— 点封面会换歌，换了歌就重搜，等于每点一下打一次接口。
               滑走再滑回来，才按当时队列里选中的那一首重新问一次。
               摊那一串（歌手页吃的）不在这里做 —— 这一屏没有地址栏了，进来就先做（见 MainActivity）。 */
            LaunchedEffect(pagerState.currentPage) {
                onPage(pagerState.currentPage)
                if (pagerState.currentPage == 2) onOnlineSongsFetch()
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                /* 第三颗（在线）在**没读到歌名**的时候不画：那一屏是按当前这一首搜的，没歌名它就没内容，
                   摆一颗点上去只会落到一片空白（他 10-15 点的）。页表还是三页 —— 往右滑仍然滑得过去，
                   收掉的只是那颗指示。 */
                PageDots(count = if (title.isNullOrBlank()) 2 else 3, index = pagerState.currentPage, onArt = onArt)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    /* 两颗并排。命中盒从 48dp 见方收成 28×48dp：48 见方里那颗 30px 的墨两边各空 51px，
                       两颗挨着排就是 94px（34.2dp）的空 —— 他要"右边间隙小一点"，按系数 0.5 收到 47px。
                       间隙 = 盒宽 − 墨宽（和 ColBox 那一族同一条算式）：28 − 10.91 = 17.1dp。
                       尾巴那 10dp 是把整对往回挪，好让齿轮的墨右沿仍停在原来的 x=974，
                       不然收了盒它会被顶到内容边界上去。高仍 48dp（28dp 只是宽，77×132px 按得着）。
                       左右两块等宽，所以中间那三颗点仍在屏心。 */
                    Row(modifier = Modifier.padding(end = Tokens.Dimen.ArtIconEndPad)) {
                        Box(
                            modifier = Modifier
                                .size(width = Tokens.Dimen.ArtIconBox, height = Tokens.Dimen.IconHit)
                                .tap(onClick = onOpenArt),
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(IconKind.Photo, size = 22.dp, color = Tokens.ink(onArt))
                        }
                        Box(
                            modifier = Modifier
                                .size(width = Tokens.Dimen.ArtIconBox, height = Tokens.Dimen.IconHit)
                                .tap(onClick = onSettings),
                            contentAlignment = Alignment.Center
                        ) {
                            LineIcon(IconKind.Settings, size = 22.dp, color = Tokens.ink(onArt))
                        }
                    }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (page == 0) {
                    CoverPage(
                        cover = cover,
                        playing = isPlaying,
                        title = title,
                        artist = listOfNotNull(artist, quality).joinToString(" · ").ifBlank { null },
                        lines = lyrics,
                        positionMs = positionMs,
                        onArt = onArt
                    )
                } else if (page == 1) {
                    LyricPage(
                        lines = lyrics,
                        positionMs = positionMs,
                        source = lyricSource,
                        onSeek = { ms -> onTakeover(); audio?.seekTo(ms) },
                        onPickFolder = { folderPicker.launch(null) },
                        onArt = onArt
                    )
                } else {
                    MusicOnlinePage(
                        hotMusic = onlineHotMusic,
                        moreMusic = onlineMoreMusic,
                        hotVideo = onlineHotVideo,
                        moreVideo = onlineMoreVideo,
                        error = onlineError,
                        playingId = onlinePlayingId,
                        onPlay = onOnlinePlay,
                        onQueue = onOnlineQueue,
                        onDownload = onOnlineDownload,
                        downloadingKey = onlineDownloadingKey,
                        onOpenVideo = onOpenVideo
                    )
                }
            }

            if (errorMessage != null || trackWarning != null) {
                Column(Modifier.padding(bottom = 6.dp)) {
                    errorMessage?.let {
                        Text(it, color = Color(0xFFC0392B), style = MaterialTheme.typography.bodySmall)
                    }
                    trackWarning?.let {
                        Text(it, color = Color(0xFFB87A00), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            /* 进度条和那五个键只在封面那一页摆。歌词页点一行选中、再点那颗 ▶ 才跳，在线页整页是内容 ——
               底下再挂一条控制区，两页都白占一截高度（他点名要的）。
               滚动歌词那一格不在这里 —— 它挪到封面那一页里面去了，见 CoverPage：挂在外面的话，
               它上面那截空是 pager 自己的余量（真机量到 51dp），隔不到进度条这一侧来，
               "和歌名的间隙与和底部的间隙一样"这条就只能在 pager 里面用两个等权重空档给。 */
            if (pagerState.currentPage == 0) {
                ProgressRow(
                    positionMs = positionMs,
                    durationMs = durationMs,
                    enabled = audio != null && durationMs > 0,
                    onDragStart = { dragging = true },
                    onDrag = { f -> positionMs = (f * durationMs).toLong() },
                    onSeek = { ms ->
                        dragging = false
                        onTakeover()
                        audio?.seekTo(ms)
                    },
                    speed = speed,
                    onSpeed = { v ->
                        speed = v
                        onTakeover()
                        audio?.setPlaybackSpeed(v)
                    },
                    onArt = onArt
                )

                Spacer(Modifier.height(6.dp))

                ControlRow(
                    playing = isPlaying,
                    repeatMode = repeatMode,
                    onPickMode = { modeSheet = true },
                    onPrev = { onTakeover(); audio?.seekToPreviousMediaItem() },
                    onNext = { onTakeover(); audio?.seekToNextMediaItem() },
                    onPlayPause = {
                        val c = audio ?: return@ControlRow
                        onTakeover()
                        if (c.isPlaying) c.pause() else c.play()
                    },
                    onQueue = onOpenQueue,
                    onArt = onArt
                )

                Spacer(Modifier.height(14.dp))
            }
        }

        if (modeSheet) {
            ModeSheet(
                title = "播放顺序",
                modes = listOf(
                    Triple(IconKind.OrderSeq, "顺序播放", repeatMode == Player.REPEAT_MODE_OFF),
                    Triple(IconKind.Repeat, "列表循环", repeatMode == Player.REPEAT_MODE_ALL),
                    Triple(IconKind.LoopOne, "单曲循环", repeatMode == Player.REPEAT_MODE_ONE)
                ),
                onPick = { i ->
                    onTakeover()
                    audio?.repeatMode = when (i) {
                        1 -> Player.REPEAT_MODE_ALL
                        2 -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                    modeSheet = false
                },
                onDismiss = { modeSheet = false }
            )
        }

    }
}

@Composable
private fun CoverPage(
    cover: ImageBitmap?,
    playing: Boolean,
    title: String?,
    artist: String?,
    lines: List<LyricLine>,
    positionMs: Long,
    onArt: Boolean
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(Tokens.Dimen.CoverTopGap))

        // 参考图卡片 323x293dp（宽:高 = 1.10），铺满内容宽度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.103f)
        ) {
            /* 画幅档（海报/自选）这一格是**空的**：那张图已经铺在整屏底下了，再画一块卡片底
               就成了图上贴一个方框。格子照旧占位，所以换档的时候歌名和歌手一格不挪。 */
            if (!onArt) {
                Turntable(
                    cover = cover,
                    playing = playing,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = title ?: "未选择文件",
            color = Tokens.inkStrong(onArt),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 22.dp)
        )
        Text(
            text = artist ?: "未知歌手",
            color = Tokens.ink(onArt),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )

        /* 滚动歌词塞在歌手行下面那一整块余量里**居中**：到歌名的缝和到进度条的缝是同一条，
           这条是居中给的，不是我挑出来的数。
           顶上那道缝已经从 `weight(0.34f)` 改成定值 [Tokens.Dimen.CoverTopGap] —— 两个权重一起
           分余量的话，这一格从 66dp 长到 110dp 会把封面顶上去 11dp；钉死上面那一条，
           涨跌全落在这一只盒子里，封面一格不挪。
           没歌词的那首歌这一格高 0dp，这只盒子里空着 —— 和改之前逐位相同。 */
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            LyricStrip(lines = lines, positionMs = positionMs, onArt = onArt)
        }
    }
}

/** 进度条上面那一格的一条：五行里的一句，单行、居中 */
@Composable
private fun StripLine(text: String, active: Boolean, onArt: Boolean) {
    Text(
        text = text,
        color = if (active) Tokens.inkStrong(onArt) else Tokens.inkFaint(onArt),
        fontSize = if (active) 18.sp else 15.sp,
        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.Dimen.LyricStripSlot)
    )
}

/**
 * 进度条上面那五行滚动歌词：当前句落在正中那一格，上下各两句。
 *
 * 走法和歌词那一屏同一套（LazyColumn + animateScrollToItem），不是自己算 translationY ——
 * 窗口自己滚的话，跨句那一下要么跳要么得再写一套插值。
 * `scrollToItem(i)` 把第 i 项对齐到**内衬之后**（真机 10-15 量的：给它 cur−1，当前句落在最下那一格），
 * 所以这里传 `current` 本身、上下各留两格的顶/底内衬，当前句才正好压在正中间。
 *
 * **没有歌词的那首歌整条不摆**（高 0dp，那一屏逐位回到改之前）：摆五行空的是把封面顶上去一截去装没东西。
 */
@Composable
private fun LyricStrip(lines: List<LyricLine>, positionMs: Long, onArt: Boolean) {
    if (lines.isEmpty()) return
    val state = rememberLazyListState()
    val current = Lrc.indexAt(lines, positionMs).coerceAtLeast(0)
    val slot = Tokens.Dimen.LyricStripSlot

    LaunchedEffect(current, lines) {
        state.animateScrollToItem(current)
    }

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .height(slot * 5),
        contentPadding = PaddingValues(top = slot * 2, bottom = slot * 2)
    ) {
        itemsIndexed(lines) { i, line ->
            StripLine(text = line.text, active = i == current, onArt = onArt)
        }
    }
}

@Composable
private fun PageDots(count: Int, index: Int, onArt: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == index) 14.dp else 6.dp, height = 6.dp)
                    .background(
                        if (i == index) Tokens.ink(onArt) else Tokens.inkFaint(onArt).copy(alpha = 0.5f),
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ProgressRow(
    positionMs: Long,
    durationMs: Long,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    speed: Float,
    onSpeed: (Float) -> Unit,
    onArt: Boolean
) {
    var menu by remember { mutableStateOf(false) }
    ScrubBar(
        fraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
        enabled = enabled,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = { f -> onSeek((f * durationMs).toLong()) },
        idleColor = if (onArt) Tokens.ArtTrackIdle else Tokens.TrackIdle,
        playedColor = if (onArt) Tokens.ArtTrackPlayed else Tokens.TrackPlayed,
        thumbColor = Tokens.inkStrong(onArt)
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(clockOf(positionMs), color = Tokens.inkFaint(onArt), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(
            text = "倍速 ${if (speed % 1f == 0f) speed.toInt().toString() else speed}x",
            color = Tokens.ink(onArt),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .tap { menu = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(clockOf(durationMs), color = Tokens.inkFaint(onArt), style = MaterialTheme.typography.bodySmall)
    }
    // 原来是贴在文字下面的一个小下拉菜单，够不着也不好点，改成整块底部抽屉
    if (menu) {
        SpeedSheet(
            speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f),
            current = speed,
            onPick = { v -> menu = false; onSpeed(v) },
            onDismiss = { menu = false }
        )
    }
}

@Composable
private fun ControlRow(
    playing: Boolean,
    repeatMode: Int,
    onPickMode: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    onQueue: () -> Unit,
    onArt: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        HitIcon(
            // 三档一枚自己的图形，按钮跟着当前档走；开着循环时再上主色
            kind = when (repeatMode) {
                Player.REPEAT_MODE_ALL -> IconKind.Repeat
                Player.REPEAT_MODE_ONE -> IconKind.LoopOne
                else -> IconKind.OrderSeq
            },
            color = if (repeatMode == Player.REPEAT_MODE_OFF) Tokens.ink(onArt) else Tokens.Accent,
            size = Tokens.Dimen.LoopIcon,
            stroke = Tokens.Dimen.LoopStroke,
            onClick = onPickMode
        )
        HitIcon(IconKind.Prev, color = Tokens.inkStrong(onArt), size = Tokens.Dimen.PrevNextIcon, onClick = onPrev)
        // 圆底删掉后这颗就是枚裸三角，按参考图三颗同色同量级；命中区还是原来那块 64dp 的方
        Box(
            modifier = Modifier
                .size(Tokens.Dimen.PlayButton)
                .tap(onClick = onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            LineIcon(
                kind = if (playing) IconKind.Pause else IconKind.Play,
                size = if (playing) Tokens.Dimen.PauseIcon else Tokens.Dimen.PlayIcon,
                color = Tokens.inkStrong(onArt),
                modifier = Modifier.offset(x = if (playing) 0.dp else Tokens.Dimen.PlayInkShift)
            )
        }
        HitIcon(IconKind.Next, color = Tokens.inkStrong(onArt), size = Tokens.Dimen.PrevNextIcon, onClick = onNext)
        HitIcon(
            IconKind.Queue, color = Tokens.inkStrong(onArt),
            size = Tokens.Dimen.QueueIcon, stroke = Tokens.Dimen.QueueStroke, onClick = onQueue
        )
    }
}

@Composable
private fun HitIcon(
    kind: IconKind, color: Color, onClick: () -> Unit,
    size: Dp = Tokens.Dimen.IconSize, stroke: Dp? = null
) {
    Box(
        modifier = Modifier
            .size(Tokens.Dimen.IconHit)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        LineIcon(kind, color = color, size = size, stroke = stroke)
    }
}


@OptIn(UnstableApi::class)
@Composable
fun rememberPlaybackController(): MediaController? {
    val context = LocalContext.current
    val holder = remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener(
            { holder.value = runCatching { future.get() }.getOrNull() },
            Executors.newSingleThreadExecutor()
        )
        onDispose {
            MediaController.releaseFuture(future)
            holder.value = null
        }
    }
    return holder.value
}

@OptIn(UnstableApi::class)
private fun titleOf(player: Player?, context: Context): String? {
    if (player == null) return null
    val md = player.mediaMetadata
    return (md.title ?: md.displayTitle)?.toString()
        ?: player.currentMediaItem?.localConfiguration?.uri?.let { displayName(context, it) }
}

@OptIn(UnstableApi::class)
internal fun displayName(context: Context, uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) return c.getString(0) }
        }
    }
    return uri.lastPathSegment?.substringAfterLast('/') ?: "未知文件"
}

private fun Uri?.isVideoUri(): Boolean =
    this != null && com.xiaojoo.lightplayer.data.isVideoUri(toString())

@OptIn(UnstableApi::class)
private fun unsupportedTrackOf(player: Player): String? {
    val blocked = player.currentTracks.groups.firstOrNull {
        !it.isSupported && (it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_VIDEO)
    } ?: return null
    val mime = (0 until blocked.length)
        .mapNotNull { blocked.getTrackFormat(it).sampleMimeType }
        .firstOrNull()
    return if (blocked.type == C.TRACK_TYPE_AUDIO) {
        "本机无对应音频解码器，该音轨不会发声：${mime ?: "未知格式"}"
    } else {
        "本机无对应视频解码器，画面不会输出：${mime ?: "未知格式"}"
    }
}

/**
 * 眼前这一条**真在解**的那一份：选中的那组音轨拿它的 `Format`，没选中就退到第一条音轨组。
 * 文案规则都在 `AudioQuality` 里，这一处只负责从 `Format` 取数，不产文案。
 */
private fun qualityOf(player: Player): String? {
    val groups = player.currentTracks.groups
    val group = groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
        ?: groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO }
        ?: return null
    val f = (0 until group.length)
        .map { group.getTrackFormat(it) }
        .firstOrNull { it.sampleMimeType != null } ?: return null
    val bits = when (f.pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT -> 16
        C.ENCODING_PCM_24BIT -> 24
        C.ENCODING_PCM_32BIT -> 32
        else -> 0
    }
    return AudioQuality.label(f.sampleMimeType, f.bitrate.toLong(), f.sampleRate, bits, f.channelCount)
}

private suspend fun loadCover(context: Context, uri: Uri): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val r = MediaMetadataRetriever()
            r.setDataSource(context, uri)
            val bytes = r.embeddedPicture
            r.release()
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
        }.getOrNull()
    }
