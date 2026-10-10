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
        private val VideoExt = setOf("mp4", "mkv", "webm", "mov", "3gp", "ts", "m2ts")

        /**
         * 库里查不到的条目（SAF 直接选进来的文件）只能从 uri 本身认：
         * MediaStore 的视频库走 /video/，其余看扩展名。
         */
        fun isVideoUri(raw: String): Boolean {
            val path = raw.substringBefore('?')
            return path.contains("/video/") ||
                path.substringAfterLast('.', "").lowercase() in VideoExt
        }
    }
}

private val RowHeight = 56.dp

/**
 * 队列面板。正在播的队列按类型分成音乐 / 视频两页，已播歌曲单独一页。
 * 两页的行完全一样：点行播、✕ 移出队列、≡ 拖动改顺序；视频页在行首多一张缩略图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueEntry>,
    history: List<Track>,
    onPlayQueueAt: (Int) -> Unit,
    onRemoveAt: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onPlayHistory: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // -1 = 还没点过页签：跟着正在播的那条走，从视频页拉开就该看见视频列表
    var picked by remember { mutableIntStateOf(-1) }

    // 带上在整条队列里的真实下标：移出、播放、换序都要按真实下标找
    val withIndex = queue.mapIndexed { i, e -> i to e }
    val audioRows = withIndex.filter { !it.second.isVideo }
    val videoRows = withIndex.filter { it.second.isVideo }
    val autoTab = queue.firstOrNull { it.current }?.let { if (it.isVideo) 1 else 0 } ?: 0
    val tab = if (picked >= 0) picked else autoTab

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
            Row(modifier = Modifier.fillMaxWidth().height(46.dp)) {
                QueueTab("音乐列表", audioRows.size, tab == 0, Modifier.weight(1f)) { picked = 0 }
                QueueTab("视频列表", videoRows.size, tab == 1, Modifier.weight(1f)) { picked = 1 }
                QueueTab("已播歌曲", history.size, tab == 2, Modifier.weight(1f)) { picked = 2 }
            }
            when (tab) {
                2 -> {
                    if (history.isEmpty()) {
                        EmptyLine("播过的歌会留在这里（重启也在）", Modifier.weight(1f))
                    } else {
                        HistoryList(history, onPlayHistory)
                    }
                }
                else -> {
                    val rows = if (tab == 0) audioRows else videoRows
                    if (rows.isEmpty()) {
                        EmptyLine(
                            if (tab == 0) "队列里没有音乐，去「本地歌曲」点「全部播放」"
                            else "队列里没有视频，去「视频」列表点「全部播放」",
                            Modifier.weight(1f)
                        )
                    } else {
                        QueueList(rows, thumbs = tab == 1, onPlayQueueAt, onRemoveAt, onMove)
                    }
                }
            }
            Spacer(Modifier.height(Tokens.Dimen.ScreenPad))
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

    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(rows, key = { _, r -> r.second.uri }) { pos, row ->
            val (realIndex, e) = row
            val stepPx = with(LocalDensity.current) { RowHeight.toPx() }
            var acc by remember(e.uri) { mutableFloatStateOf(0f) }
            val dragging = dragUri == e.uri
            Row(
                modifier = Modifier
                    // 换序时这一行滑过去、被挤开的那几行也滑过去；
                    // 只留这个动画，不给拖起的行加底色
                    .animateItem()
                    .fillMaxWidth()
                    .height(RowHeight)
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

@Composable
private fun ColumnScope.HistoryList(history: List<Track>, onPlayHistory: (Track) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
        itemsIndexed(history, key = { _, t -> "h-" + t.uri }) { _, t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .padding(horizontal = Tokens.Dimen.ScreenPad)
                    .tap { onPlayHistory(t) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = listOfNotNull(
                        t.name.takeIf { it.isNotBlank() },
                        t.artist?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ).joinToString(" - "),
                    color = Tokens.InkStrong,
                    fontSize = 15.sp,
                    maxLines = 1,
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

