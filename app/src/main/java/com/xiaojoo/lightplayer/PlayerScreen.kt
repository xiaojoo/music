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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.PlayHistory
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.ui.IconKind
import com.xiaojoo.lightplayer.ui.tap
import com.xiaojoo.lightplayer.ui.LineIcon
import com.xiaojoo.lightplayer.ui.Lrc
import com.xiaojoo.lightplayer.ui.LyricLine
import com.xiaojoo.lightplayer.ui.LyricPage
import com.xiaojoo.lightplayer.ui.LyricSource
import com.xiaojoo.lightplayer.ui.MusicOnlinePage
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
    onPicked: (List<Uri>) -> Unit,
    onOpenQueue: () -> Unit,
    // 右滑到的第三页（在线）：状态都在壳层，这一屏只画和回动作 —— 和歌词那页一样不带自己的存档
    /** 一屏四栏都从"搜当前这首歌"那一批结果里分：两族各按播放数降序，前 3 张最火、第 4 条往后相关 */
    onlineHotMusic: List<MusicVideo>,
    onlineMoreMusic: List<MusicVideo>,
    onlineHotVideo: List<MusicVideo>,
    onlineMoreVideo: List<MusicVideo>,
    onlineKeyword: String,
    onlineError: String?,
    onlinePlayingId: String?,
    onOnlinePlay: (MusicVideo) -> Unit,
    /** 音乐那一行右端那颗：挂到音频那一路的队尾 */
    onOnlineQueue: (MusicVideo) -> Unit,
    /** 滑到这一页、或换了歌，就问一次「这首歌」；壳层里按关键词去重。这一屏上没有那颗按钮 */
    onOnlineSongsFetch: () -> Unit,
    /** 那一栏里的视频：不在这播，挪到视频 tab 的在线那一栏去播 */
    onOpenVideo: (MusicVideo) -> Unit,
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
    var lyricTreeUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onPicked(uris)
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
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
                if (durationMs > 0) {
                    currentUri?.let { PlayHistory.updateDuration(context, it, durationMs) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "${error.errorCodeName}: ${error.message}"
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                errorMessage = null
                title = titleOf(audio, context)
                artist = audio?.mediaMetadata?.artist?.toString()
                currentUri = item?.localConfiguration?.uri
                trackWarning = audio?.let { unsupportedTrackOf(it) }
                val uri = item?.localConfiguration?.uri
                if (uri != null) {
                    PlayHistory.record(
                        context,
                        Track(
                            id = runCatching { android.content.ContentUris.parseId(uri) }.getOrDefault(0L),
                            uri = uri,
                            name = titleOf(audio, context) ?: "未知文件",
                            artist = artist,
                            durationMs = audio?.duration?.takeIf { it > 0 } ?: 0L,
                            sizeBytes = 0L,
                            isVideo = uri.isVideoUri()
                        )
                    )
                }
            }

            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                title = titleOf(audio, context)
                artist = metadata.artist?.toString() ?: artist
            }

            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                trackWarning = audio?.let { unsupportedTrackOf(it) }
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
        val hit = withContext(Dispatchers.IO) { LyricSource.read(context, uri, lyricTreeUri) }
        lyrics = hit.text?.let { Lrc.parse(it) }.orEmpty()
        lyricSource = hit.status
    }

    LaunchedEffect(Unit) {
    }

    LaunchedEffect(currentUri) {
        onNowPlaying(currentUri)
        val uri = currentUri ?: return@LaunchedEffect
        cover = loadCover(context, uri)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.playerBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Spacer(Modifier.height(12.dp))

            val pagerState = rememberPagerState(initialPage = startPage, pageCount = { 3 })

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
                PageDots(count = 3, index = pagerState.currentPage)
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(Tokens.Dimen.IconHit)
                            .tap { picker.launch(arrayOf("*/*")) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("选文件", color = Tokens.Ink, style = MaterialTheme.typography.labelMedium)
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
                        artist = artist
                    )
                } else if (page == 1) {
                    LyricPage(
                        lines = lyrics,
                        positionMs = positionMs,
                        source = lyricSource,
                        onSeek = { ms -> onTakeover(); audio?.seekTo(ms) },
                        onPickFolder = { folderPicker.launch(null) }
                    )
                } else {
                    MusicOnlinePage(
                        hotMusic = onlineHotMusic,
                        moreMusic = onlineMoreMusic,
                        hotVideo = onlineHotVideo,
                        moreVideo = onlineMoreVideo,
                        keyword = onlineKeyword,
                        error = onlineError,
                        playingId = onlinePlayingId,
                        onPlay = onOnlinePlay,
                        onQueue = onOnlineQueue,
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

            /* 进度条和那五个键只在封面那一页摆。歌词页点一行就能跳，在线页整页是内容 ——
               底下再挂一条控制区，两页都白占一截高度（他点名要的）。 */
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
                    }
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
                    onQueue = onOpenQueue
                )
            }

            Spacer(Modifier.height(14.dp))
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
    artist: String?
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(0.34f))

        // 参考图卡片 323x293dp（宽:高 = 1.10），铺满内容宽度
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.103f)
        ) {
            Turntable(
                cover = cover,
                playing = playing,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = title ?: "未选择文件",
            color = Tokens.InkStrong,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 22.dp)
        )
        Text(
            text = artist ?: "未知歌手",
            color = Tokens.Ink,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LyricPage(title: String?) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = if (title == null) "选择文件后显示歌词" else "暂无歌词",
            color = Tokens.InkFaint,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PageDots(count: Int, index: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == index) 14.dp else 6.dp, height = 6.dp)
                    .background(if (i == index) Tokens.Ink else Tokens.InkFaint.copy(alpha = 0.5f), CircleShape)
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
    onSpeed: (Float) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    ScrubBar(
        fraction = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f,
        enabled = enabled,
        onDragStart = onDragStart,
        onDrag = onDrag,
        onDragEnd = { f -> onSeek((f * durationMs).toLong()) }
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(fmt(positionMs), color = Tokens.InkFaint, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.weight(1f))
        Text(
            text = "倍速 ${if (speed % 1f == 0f) speed.toInt().toString() else speed}x",
            color = Tokens.Ink,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .tap { menu = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        Spacer(Modifier.weight(1f))
        Text(fmt(durationMs), color = Tokens.InkFaint, style = MaterialTheme.typography.bodySmall)
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
    onQueue: () -> Unit
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
            color = if (repeatMode == Player.REPEAT_MODE_OFF) Tokens.Ink else Tokens.Accent,
            size = Tokens.Dimen.LoopIcon,
            stroke = Tokens.Dimen.LoopStroke,
            onClick = onPickMode
        )
        HitIcon(IconKind.Prev, color = Tokens.InkStrong, size = Tokens.Dimen.PrevNextIcon, onClick = onPrev)
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
                color = Tokens.InkStrong,
                modifier = Modifier.offset(x = if (playing) 0.dp else Tokens.Dimen.PlayInkShift)
            )
        }
        HitIcon(IconKind.Next, color = Tokens.InkStrong, size = Tokens.Dimen.PrevNextIcon, onClick = onNext)
        HitIcon(
            IconKind.Queue, color = Tokens.InkStrong,
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
private fun displayName(context: Context, uri: Uri): String {
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

private fun fmt(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
