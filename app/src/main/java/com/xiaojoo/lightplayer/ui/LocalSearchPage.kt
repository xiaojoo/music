package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.Track
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.SourceFailureException

/**
 * 「我的」顶上那颗搜索框点进来的那一屏：**默认只问本机曲库**，音乐和视频一起筛。
 * （首页那颗框进的是问接口那一屏 [ApiSearchPage]，两屏不是一回事，别混。）
 *
 * 第三颗芯片是**可选的一路在线**（锤子音乐，他 10-27 点名要的），它**默认关着**：
 * 关着就一句都不问网，这一屏还是原来那句"只搜本机曲库"；按下去才现问那一路、
 * 单独成一段。那一路没有默认列表（空词实测回 400），所以芯片上那个数只有"问过"之后才写。
 *
 * 顶栏那一条（← + 词 + ✕）抄 [ApiSearchPage] 同一份几何，包括 ✕ 那颗命中盒压在墨中心上的补法。
 * 下面一行两颗芯片是**范围**：默认两颗都开，开着的各成一段（先音乐后视频），
 * 芯片上那个数是这一词命中的条数 —— 和筛选页那行芯片同一只 [FilterChip]，段标题同一只 [FilterHead]。
 *
 * 和接口那一屏的差别只在"问谁"：本机这一串是现成的，所以**边打字边出结果**，
 * 不等键盘那颗搜索。点一行照列表页那一套：就地起播、这一页不跳，
 * 队列 = 点的那一段（音乐、视频各走各那一路播放器，见 MainActivity.play）。
 */
@Composable
fun LocalSearchPage(
    keyword: String,
    onKeyword: (String) -> Unit,
    songs: List<Track>,
    videos: List<Track>,
    onPlayList: (List<Track>, Int) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (Track) -> Unit,
    onQueueAdd: (Track) -> Unit,
    /** 第三颗芯片的名字（源自己报的那个，不在这边另抄一份）。
     *  给 null = 那一路已经被禁用/删接了：**整颗芯片不出现** —— 留着它只会永远报 0，是死控件 */
    onlineLabel: String?,
    /** 现问那一路：只在那颗芯片开着、词不空的时候调 */
    askOnline: suspend (String) -> List<MusicVideo>,
    onPlayOnline: (MusicVideo) -> Unit,
    onQueueOnline: (MusicVideo) -> Unit,
    onDownloadOnline: (MusicVideo) -> Unit,
    /** 存盘正在走的那一条：行上那颗从此刻起变主色（和播放页右滑那一屏同一样） */
    downloadingKey: String?,
    /** 在线条目按**页地址**收：直链带有效期：存地址两天就解不开 */
    onToggleOnlineFavorite: (MusicVideo) -> Unit,
    onlinePlayingId: String?,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current
    var songOn by remember { mutableStateOf(true) }
    var videoOn by remember { mutableStateOf(true) }
    /** 默认关：关着就不发这一趟网：这一屏的默认行为还是"只搜本机曲库" */
    var onlineOn by remember { mutableStateOf(false) }
    var onlineHits by remember { mutableStateOf<List<MusicVideo>>(emptyList()) }
    var onlineNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { fr.requestFocus() }

    val q = keyword.trim()
    // 和列表页的搜索态同一个匹配：歌名或歌手含这个词就算，不分大小写
    fun matched(list: List<Track>): List<Track> = if (q.isEmpty()) emptyList() else list.filter {
        it.name.contains(q, ignoreCase = true) || it.artist?.contains(q, ignoreCase = true) == true
    }
    val hitSongs = matched(songs)
    val hitVideos = matched(videos)
    val sList = if (songOn) hitSongs else emptyList()
    val vList = if (videoOn) hitVideos else emptyList()
    // 芯片上那个数：有词是"这一词命中几条"，还没打词就是"这一路一共有几条可搜" ——
    // 空屏摆个 0 是假数（接口那一屏的同一条道理，见 ApiSearchPage）
    val cSong = if (q.isEmpty()) songs.size else hitSongs.size
    val cVideo = if (q.isEmpty()) videos.size else hitVideos.size
    /* 第三路是现问的：本机那两段是内存里现成的：边打字边出这一路要等站点回：所以它只在
       芯片开着、词不空的时候问一次：词变了重问（取消上一趟由 LaunchedEffect 自己做：
       快速改字不会挤出好几次回来的旧结果）。问不通就把站点那一句写在芯片下面：不替它编一句。 */
    LaunchedEffect(onlineOn, q, onlineLabel) {
        if (!onlineOn || q.isBlank() || onlineLabel == null) {
            onlineHits = emptyList()
            onlineNote = null
            return@LaunchedEffect
        }
        try {
            onlineHits = askOnline(q)
            onlineNote = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e        // 真被取消：不能当成"这一路没内容"
        } catch (e: SourceFailureException) {
            onlineHits = emptyList()
            onlineNote = e.failure.message
        } catch (e: Exception) {
            onlineHits = emptyList()
            // 兜底那句要带异常名：message 为空时写"这一路问不通"会把我们自己的 bug 说成站点的错
            // （第一版就是这么撞上一条 NetworkOnMainThreadException 的）
            onlineNote = e.message ?: e.javaClass.simpleName
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Tokens.Dimen.IconHit)
                    .tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) { LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Tokens.Dimen.searchTextStart(Tokens.Dimen.ScreenPad)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (keyword.isEmpty()) {
                        Text("搜歌名或歌手", color = Tokens.InkFaint, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = keyword,
                        onValueChange = onKeyword,
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Tokens.InkStrong, fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    )
                }
                // ✕ 那颗的位置和命中盒补法都跟接口搜索那一屏同一套，见 Tokens 里 searchCloseShift 那段
                val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.searchCloseShift(Tokens.Dimen.ScreenPad) + hitSkew)
                        .tap { if (keyword.isEmpty()) onBack() else onKeyword("") },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(
                        IconKind.Close, size = 19.dp, color = Tokens.Ink,
                        modifier = Modifier.offset(x = -hitSkew),
                    )
                }
            }
        }
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip("音乐", cSong, songOn) { songOn = !songOn }
            FilterChip("视频", cVideo, videoOn) { videoOn = !videoOn }
            // 没问过之前不写数：这一路没有"一共多少条可搜"那个数：编一个就是假数
            if (onlineLabel != null) {
                FilterChip(onlineLabel, if (onlineOn && q.isNotBlank()) onlineHits.size else null, onlineOn) {
                    onlineOn = !onlineOn
                }
            }
        }
        // onlineNote 是委托属性，智能转换用不上，先自己拆一次（和 MainActivity 里 `page` 同一句规矩）
        val note = onlineNote
        if (note != null) {
            Text(
                note, color = Tokens.InkFaint, fontSize = 12.sp, maxLines = 2,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 2.dp)
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                q.isEmpty() -> Text(
                    "输入歌名或歌手", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                !songOn && !videoOn && !(onlineOn && onlineLabel != null) -> Text(
                    text = if (onlineLabel == null) "先选「音乐」或「视频」"
                        else "先选「音乐」「视频」或「$onlineLabel」",
                    color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                sList.isEmpty() && vList.isEmpty() && onlineHits.isEmpty() -> Text(
                    "没有匹配「$q」的条目", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    state = PageScrolls.list(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    if (sList.isNotEmpty()) {
                        item(key = "s:head") { FilterHead("音乐") }
                        itemsIndexed(sList, key = { _, t -> "s-" + t.uri }) { i, t ->
                            TrackLine(
                                track = t, index = i, q = q, thumbs = false,
                                favorite = t.uri.toString() in favorites,
                                onPlay = { onPlayList(sList, i) },
                                onToggleFavorite = { onToggleFavorite(t) },
                                onQueueAdd = { onQueueAdd(t) },
                            )
                        }
                    }
                    // 名字给 null（那一路被关掉/删了）就整段不摆：上面那一趟已经把它清空了，这里只是让编译器也认
                    val label = onlineLabel
                    if (label != null && onlineHits.isNotEmpty()) {
                        item(key = "o:head") { FilterHead(label) }
                        itemsIndexed(onlineHits, key = { _, v -> "o-" + v.key }) { _, v ->
                            MusicRow(
                                video = v, playing = "online:" + v.key == onlinePlayingId,
                                onClick = { onPlayOnline(v) },
                                onQueue = { onQueueOnline(v) },
                                onDownload = { onDownloadOnline(v) },
                                downloading = downloadingKey == v.key,
                                // 左右内衬和上面「音乐」那一段同一对：那一段是 TrackLine 自己带的
                                // ScreenPad / ColEndPad，这一族的行本身不带内衬（源页那一屏由外层给）
                                pad = Modifier.padding(
                                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ColEndPad
                                ),
                            )
                        }
                    }
                    if (vList.isNotEmpty()) {
                        item(key = "v:head") { FilterHead("视频") }
                        itemsIndexed(vList, key = { _, t -> "v-" + t.uri }) { i, t ->
                            TrackLine(
                                track = t, index = i, q = q, thumbs = true,
                                favorite = t.uri.toString() in favorites,
                                onPlay = { onPlayList(vList, i) },
                                onToggleFavorite = { onToggleFavorite(t) },
                                onQueueAdd = { onQueueAdd(t) },
                            )
                        }
                    }
                }
            }
        }
    }
}
