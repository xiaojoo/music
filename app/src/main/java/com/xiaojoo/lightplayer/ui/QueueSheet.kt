package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Track

data class QueueEntry(
    val uri: String,
    val name: String,
    val artist: String?,
    val durationMs: Long,
    val current: Boolean,
    val isVideo: Boolean
) {
    /** 参考图那一行就是「歌名 - 歌手」一行显示 */
    val line: String get() = listOfNotNull(
        name.takeIf { it.isNotBlank() },
        artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
    ).joinToString(" - ")

    companion object {
        /**
         * 库里查不到的条目（SAF 直接选进来的文件）只能从 uri 本身认。
         * 判据本身在 data 层（`MediaLibrary.kt` 的 isVideoUri）—— 历史存档读的时候也要过同一条，
         * 放两份会走岔。
         */
        fun isVideoUri(raw: String): Boolean = com.xiaojoo.lightplayer.data.isVideoUri(raw)
    }
}

/* 行高按这一页最高的那个东西分档。视频页行首是 42dp 缩略图、歌名两行 15sp（约 41dp），
   56dp 只剩上下各 7dp，再收就顶到边；音乐页和两支「已播」只有一行 15sp（行高约 20.3dp），
   56dp 上下各摊 17.9dp —— 这两档收到 48dp：上下各 13.9dp，比原来小 4dp（0.857 倍）。 */
private val RowHeightThumb = 56.dp
private val RowHeightFlat = 48.dp

/**
 * 队列面板。**按正在播的那一路分家**：放歌就只摆「音乐列表 / 已播音乐」，放视频只摆
 * 「视频列表 / 已播视频」。以前一支面板里三页并存，看着视频页在放、点进去先要挑哪一页，
 * 而另一路的那一串在本路里根本没有可操作的对象。
 * 两路的行是同一份代码：点行播、✕ 移出队列、≡ 拖动改顺序；视频那一路行首带缩略图。
 * 行里的 `realIndex` 一直是整条队列的真实下标，过滤只影响"看得见哪几行"，不影响移出/换序的寻址。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueEntry>,
    historyAudio: List<Track>,
    historyVideo: List<Track>,
    videoRoute: Boolean,
    onPlayQueueAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayHistory: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 换路由（放完一首歌接着刷视频）时回到「列表」那一页：上一路选中的页在这一路没有对应物
    var picked by remember(videoRoute) { mutableIntStateOf(0) }

    // 带上在整条队列里的真实下标：移出、播放、换序都要按真实下标找
    val withIndex = queue.mapIndexed { i, e -> i to e }
    val rows = withIndex.filter { it.second.isVideo == videoRoute }
    val history = if (videoRoute) historyVideo else historyAudio

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(
            topStart = Tokens.Dimen.SheetCorner,
            topEnd = Tokens.Dimen.SheetCorner
        ),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().height(540.dp).padding(top = Tokens.Dimen.ScreenPad)) {
            QueueBody(videoRoute, rows, history, picked, { picked = it }, onPlayQueueAt, onRemoveAt, onMove, onPlayHistory)
        }
    }
}

/**
 * 全屏那一档的队列：**同一份内容换形态** —— 从右边拉一列，不再从下面拉一大片。
 * 全屏里底下那按页签是收着的，从下面拉会把整幅画面盖掉一半；右边这一条正好让画面还在动。
 * 点面板外面那层遮罩收回（和原来横屏那套左侧抽屉同一句规矩）。
 */
@Composable
fun QueueDrawer(
    queue: List<QueueEntry>,
    historyAudio: List<Track>,
    historyVideo: List<Track>,
    videoRoute: Boolean,
    onPlayQueueAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayHistory: (Track) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(videoRoute) { mutableIntStateOf(0) }
    val withIndex = queue.mapIndexed { i, e -> i to e }
    val rows = withIndex.filter { it.second.isVideo == videoRoute }
    val history = if (videoRoute) historyVideo else historyAudio
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(340.dp)
                .background(Tokens.ListBg)
                .padding(top = Tokens.Dimen.ScreenPad, bottom = Tokens.Dimen.ScreenPad)
        ) {
            QueueBody(
                videoRoute, rows, history, picked, { picked = it },
                onPlayQueueAt, onRemoveAt, onMove, onPlayHistory
            )
        }
    }
}

/** 面板那一列：两支页签 + 队列/已播，底部抽屉和右侧抽屉共用这一份 */
@Composable
private fun ColumnScope.QueueBody(
    videoRoute: Boolean,
    rows: List<Pair<Int, QueueEntry>>,
    history: List<Track>,
    picked: Int,
    onPick: (Int) -> Unit,
    onPlayQueueAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayHistory: (Track) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
        QueueTab(
            if (videoRoute) "视频列表" else "音乐列表", rows.size, picked == 0, Modifier.weight(1f)
        ) { onPick(0) }
        QueueTab(
            if (videoRoute) "已播视频" else "已播音乐", history.size, picked == 1, Modifier.weight(1f)
        ) { onPick(1) }
    }
    if (picked == 1) {
        if (history.isEmpty()) {
            EmptyLine(
                if (videoRoute) "播过的视频会留在这里（重启也在）"
                else "播过的歌会留在这里（重启也在）",
                Modifier.weight(1f)
            )
        } else {
            HistoryList(history, thumbs = videoRoute, onPlayHistory)
        }
    } else {
        if (rows.isEmpty()) {
            EmptyLine(
                if (videoRoute) "队列里没有视频，去「视频」页点「全部播放」"
                else "队列里没有音乐，去「音乐」页签点「全部播放」",
                Modifier.weight(1f)
            )
        } else {
            QueueList(rows, thumbs = videoRoute, onPlayQueueAt, onRemoveAt, onMove)
        }
    }
}

/** 队列的一页：rows 是 (真实下标 to 条目)，音乐页和视频页共用这一份行代码 */
@Composable
private fun ColumnScope.QueueList(
    rows: List<Pair<Int, QueueEntry>>,
    thumbs: Boolean,
    onPlayQueueAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit
) {
    // 拖动按 uri 跟住那一行：队列一改顺序，下标就会变，按下标跟会跟丢
    var dragUri by remember { mutableStateOf<String?>(null) }
    // pointerInput 只在 key 变时重建，闭包里的 rows 是按下那一刻的旧顺序；
    // 起始位置和邻居都从这份最新值取，不然第一次之后就会拖错行
    val rowsNow by rememberUpdatedState(rows)
    val rowH = if (thumbs) RowHeightThumb else RowHeightFlat

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(rows, key = { _, r -> r.second.uri }) { pos, row ->
            val (realIndex, e) = row
            val stepPx = with(LocalDensity.current) { rowH.toPx() }
            var acc by remember(e.uri) { mutableFloatStateOf(0f) }
            val dragging = dragUri == e.uri
            Row(
                modifier = Modifier
                    // 换序时这一行滑过去、被挤开的那几行也滑过去；
                    // 只留这个动画，不给拖起的行加底色
                    .animateItem()
                    .fillMaxWidth()
                    .height(rowH)
                    .padding(horizontal = Tokens.Dimen.ScreenPad),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thumbs) {
                    CoverOfUri(e.uri, e.name, 42.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .tap { onPlayQueueAt(realIndex) }
                ) {
                    Text(
                        text = e.line,
                        color = if (e.current) Tokens.Accent else Tokens.InkStrong,
                        fontSize = 15.sp,
                        // 视频名是 uuid.mp4，一行 591px 装不下 36 个字符（量到截掉 5 个字符，
                        // 连扩展名都没了）；缩略图占掉 54dp 的这页给两行，行高不变
                        maxLines = if (thumbs) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (e.current) {
                    NowPlayingBars()
                    Spacer(Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .tap { onRemoveAt(realIndex) },
                    contentAlignment = Alignment.Center
                ) { LineIcon(IconKind.Close, size = 19.dp, color = Tokens.Ink) }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .pointerInput(e.uri) {
                            var dragPos = -1
                            detectVerticalDragGestures(
                                onDragStart = {
                                    dragUri = e.uri
                                    acc = 0f
                                    dragPos = rowsNow.indexOfFirst { it.second.uri == e.uri }
                                },
                                onDragEnd = { dragUri = null },
                                onDragCancel = { dragUri = null },
                                onVerticalDrag = { _, dy ->
                                    // dy 累加用 Float：按 Int 截断会丢掉每段的小数。
                                    // 触发线取半行：Compose 会先吃掉 touchSlop（这台机 ~10px），
                                    // 按整行 154px 判的话"拖一行高"永远差那一截、一次都不换
                                    acc += dy
                                    while (kotlin.math.abs(acc) >= stepPx / 2f) {
                                        val dir = if (acc > 0) 1 else -1
                                        acc -= dir * stepPx
                                        // 邻居按本页取，跨过被页签藏起来的另一类条目；
                                        // 拖到的位置自己记着，换序后重组不一定赶得上下一个拖动事件
                                        val to = dragPos + dir
                                        if (dragPos >= 0 && to in rowsNow.indices) {
                                            onMove(rowsNow[dragPos].first, rowsNow[to].first)
                                            dragPos = to
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(
                        IconKind.Drag,
                        size = 19.dp,
                        color = if (dragging) Tokens.Accent else Tokens.Ink
                    )
                }
            }
        }
    }
}

/** 已播那一页：和队列页同一档行高、同一套缩略图规则（视频那一路行名是 36 字符的 uuid，一行装不下） */
@Composable
private fun ColumnScope.HistoryList(
    history: List<Track>,
    thumbs: Boolean,
    onPlayHistory: (Track) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(history, key = { _, t -> "h-" + t.uri }) { _, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (thumbs) RowHeightThumb else RowHeightFlat)
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .tap { onPlayHistory(t) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (thumbs) {
                    CoverOfUri(t.uri.toString(), t.name, 42.dp)
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = listOfNotNull(
                        t.name.takeIf { it.isNotBlank() },
                        t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ).joinToString(" - "),
                    color = Tokens.InkStrong,
                    fontSize = 15.sp,
                    maxLines = if (thumbs) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = MediaLibrary.fmtDuration(t.durationMs).takeIf { t.durationMs > 0 }
                        ?: "",
                    color = Tokens.InkFaint,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun QueueTab(text: String, count: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier = modifier.tap(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text,
                color = if (selected) Tokens.InkStrong else Tokens.InkFaint,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
            Spacer(Modifier.width(5.dp))
            Text("$count", color = Tokens.InkFaint, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .width(44.dp)
                .height(2.dp)
                .background(if (selected) Tokens.InkStrong else Tokens.ListBg)
        )
    }
}

@Composable
private fun EmptyLine(text: String, modifier: Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().fillMaxHeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Tokens.InkFaint, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

// 三根竖条，表示"正在播放这一行"
@Composable
private fun NowPlayingBars() {
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(16.dp)) {
        listOf(10.dp, 16.dp, 7.dp).forEach { h ->
            Box(
                Modifier
                    .padding(end = 2.dp)
                    .width(3.dp)
                    .height(h)
                    .background(Tokens.Accent, RoundedCornerShape(1.dp))
            )
        }
    }
}

