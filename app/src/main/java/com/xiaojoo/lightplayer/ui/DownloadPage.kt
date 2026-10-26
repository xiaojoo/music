package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.DownloadQueue
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.videosource.MagnetFile
import com.xiaojoo.lightplayer.videosource.MagnetLink
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.Rendition
import com.xiaojoo.lightplayer.videosource.magnetFileLabel

/**
 * 「视频下载」那一整屏：粘一条地址 → 摆出这一条 → 开始下载 → 下面是这一趟排着下的几条。
 *
 * 粘链接那一行照「视频地址源」那一屏那两颗盒子抄（38dp 高、12dp 圆角、CardSolid + 一道 Divider 描边、
 * 没填东西时按不动但不消失、问站点时把加号换成同一格里一枚转圈的弧）。唯一改的是右内衬：
 * 那一屏的 21.8dp 是为了把加号的墨右沿对到它下面组标题那枚折叠箭头上，这一屏底下没有组标题，
 * 所以回到和顶栏同一档 ScreenPad(20dp) —— 邻居换了就按新邻居对。
 *
 * 文案里不写「无水印」：这一屏存下去的就是播放用的那一条码流（不重新取址、不转码），
 * 站点另外发的那一条有没有台标是站点报的那一条决定的 —— 抖音、快手实测是无水印那一条，
 * B 站那一路还没量过，不能替它许愿。
 */
@Composable
fun DownloadPage(
    url: String,
    onUrl: (String) -> Unit,
    /** 问站点那一段在跑 */
    busy: Boolean,
    error: String?,
    /** 站点认这条地址时摊出来的那一条；直链（这一路没有站点认这个域名）时为 null */
    entry: MusicVideo?,
    /** 这一条按直链收下 */
    direct: Boolean,
    /**
     * 框里粘的是一条磁力（`magnet:`）。这一条不走站点那一路：＋ 那颗去问它里面有什么，
     * 问出来是一列文件，挑其中一个下 —— 下载器就是这个 app 自己（libtorrent），不跳别的软件。
     */
    magnet: MagnetLink?,
    /** 读出来的那一份文件表。空 = 还没读 */
    magnetFiles: List<MagnetFile>,
    magnetPick: Int,
    onPickFile: (Int) -> Unit,
    /** 这一条能挑的几档。空或只有一档就不摆芯片 —— 只有一档还弹一层等于白按一次 */
    renditions: List<Rendition>,
    picked: String?,
    onPick: (Rendition) -> Unit,
    /** 条目上那一格写的源名：拿源自己报的那个，不在界面这边抄对照表 */
    sourceLabel: (String) -> String?,
    /** 那颗「开始下载」现在按得动不：问站点没回来时按下去只会拿到上一条的地址 */
    canStart: Boolean,
    tasks: List<DownloadQueue.Task>,
    /** 行上那一颗：按状态分别是 暂停 / 继续 / 移除 */
    onAct: (DownloadQueue.Task) -> Unit,
    /** 长按那一行：问一句要不要把这一条连已经下好的部分一起撤掉 */
    onRemoveAsk: (DownloadQueue.Task) -> Unit,
    /** 加号那颗 / 键盘上那枚 Go：认这条地址 */
    onAsk: () -> Unit,
    /** 输入框右边那颗 ✕：清掉这一格（地址 + 认出来的一切），队列里那几条不动 */
    onClearUrl: () -> Unit,
    /** 开始下载那颗 */
    onStart: () -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    val kbd = LocalSoftwareKeyboardController.current
    val unfocus = LocalFocusManager.current
    /* 按下去之后把光标从框里撤掉：认完地址、开始下载之后要看的是下面长出来的那一块，
       框里那根线一直闪等于这一格还在等他打字。✕ 那颗不清 —— 清完就是要重新粘，光标留着。 */
    fun commit(action: () -> Unit) {
        kbd?.hide()
        unfocus.clearFocus()
        action()
    }
    val title = entry?.title?.takeIf { it.isNotBlank() }
        ?: url.substringAfterLast('/').substringBefore('?').ifBlank { url }
    val meta = listOfNotNull(
        entry?.let { sourceLabel(it.sourceId) },
        entry?.durationMs?.takeIf { it > 0 }?.let { MediaLibrary.fmtDuration(it) },
        renditions.firstOrNull { it.label == picked }?.approxBytes?.takeIf { it > 0 }
            ?.let { MediaLibrary.fmtSize(it) },
    ).joinToString(" · ").ifBlank { null }

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
            Text(
                "视频下载",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .background(Tokens.CardSolid)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (url.isEmpty()) {
                    Text("粘贴一段分享文案或视频页地址，链接自己挑出来", color = Tokens.InkFaint, fontSize = 14.sp)
                }
                BasicTextField(
                    value = url,
                    onValueChange = onUrl,
                    singleLine = true,
                    cursorBrush = SolidColor(Tokens.Accent),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.InkStrong),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { commit(onAsk) }),
                    // 有字的时候给右端那颗 ✕ 让出 34dp：没有字时那 34dp 还给文字，
                    // 不然占位那句会被一个看不见的盒子截掉尾巴
                    modifier = Modifier.fillMaxWidth().padding(end = if (url.isBlank()) 0.dp else 34.dp),
                )
                if (url.isNotBlank()) {
                    // 嵌在框里、有字才存在（他要的这一种）。字形 19dp / Tokens.Ink 和搜索框那一族
                    // 同一只；命中盒 34dp 是这一格 38dp 高里能给的最大值，墨贴右沿、盒子包住它
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(34.dp)
                            .tap(onClick = onClearUrl),
                        contentAlignment = Alignment.Center,
                    ) { LineIcon(IconKind.Close, size = 19.dp, color = Tokens.Ink) }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .background(Tokens.CardSolid)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .tap(enabled = url.isNotBlank() && !busy) { commit(onAsk) },
                contentAlignment = Alignment.Center,
            ) {
                if (busy) SpinArc()
                else LineIcon(IconKind.Add, size = 19.dp, color = if (url.isNotBlank()) Tokens.InkStrong else Tokens.InkFaint)
            }
        }
        if (error != null) {
            Text(error, color = FailRed, fontSize = 12.sp, modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad))
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (entry == null && !direct && magnet == null && tasks.isEmpty()) {
                Text(
                    "粘进来一整段分享文案就行，链接自己挑；只有一条地址时点右边那颗＋",
                    color = Tokens.InkFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Tokens.Dimen.ScreenPad)
                        .padding(bottom = 20.dp)
                ) {
                    if (entry != null || direct) {
                        PickedCard(title = title, meta = meta, cover = entry?.artworkUrl)
                        if (renditions.size > 1) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                renditions.forEach { r ->
                                    QualityChip(r.label, on = r.label == picked) { onPick(r) }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        BigButton("开始下载", enabled = canStart, onClick = { commit(onStart) })
                        Spacer(Modifier.height(8.dp))
                        Text("存到 视频/LightPlayer", color = Tokens.InkFaint, fontSize = 12.sp)
                        if (tasks.isNotEmpty()) Spacer(Modifier.height(20.dp))
                    }
                    if (magnet != null) {
                        // 两颗共用同一格位置：没读出来之前那颗叫「读出里面的文件」，读出来才叫「开始下载」。
                        // 同一屏两个互斥动作长一样、只换字，是这一屏既有的规矩（BigButton）
                        val reading = magnetFiles.isEmpty()
                        PickedCard(
                            title = magnet.name,
                            meta = when {
                                !magnet.valid -> "磁力链接 · 指纹读不出"
                                reading -> "磁力链接"
                                else -> "磁力链接 · ${magnetFiles.size} 个文件"
                            },
                            cover = null,
                        )
                        if (!reading) {
                            // 文件表是一列整行的行，不是横着滑的芯片：芯片一格装不下一个片名，
                            // 而这一屏有的是竖向地方，滑动去找一个看不见的尾巴是倒过来省钱
                            Spacer(Modifier.height(10.dp))
                            magnetFiles.forEachIndexed { i, f ->
                                if (i > 0) Hairline()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .tap { onPickFile(i) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        magnetFileLabel(f),
                                        color = if (i == magnetPick) Tokens.Accent else Tokens.InkStrong,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(MediaLibrary.fmtSize(f.size), color = Tokens.InkFaint, fontSize = 12.sp)
                                    // 没选中的那几行照样把勾的位置留着，不然右边那一列大小会一深一浅
                                    Spacer(Modifier.width(8.dp))
                                    if (i == magnetPick) {
                                        LineIcon(IconKind.Check, size = 16.dp, color = Tokens.Accent)
                                    } else {
                                        Spacer(Modifier.width(16.dp))
                                    }
                                }
                            }
                        }
                        // 没读出来之前这一屏只有那一格卡片，什么都不摆：问 DHT 是 ＋ 那颗的事，
                        // 正在问的时候 ＋ 自己会变成一枚转圈的弧，和站点那一路同一个样子
                        if (!reading) {
                            Spacer(Modifier.height(14.dp))
                            BigButton("开始下载", enabled = canStart, onClick = { commit(onStart) })
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "存到 视频/LightPlayer · 下完就收走，不给别人传",
                                color = Tokens.InkFaint,
                                fontSize = 12.sp,
                            )
                        }
                        if (tasks.isNotEmpty()) Spacer(Modifier.height(20.dp))
                    }
                    if (tasks.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("本次下载", color = Tokens.Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                "清除已完成",
                                color = Tokens.InkFaint,
                                fontSize = 12.sp,
                                modifier = Modifier.tap(onClick = onClear),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        tasks.forEachIndexed { i, t ->
                            if (i > 0) Hairline()
                            TaskRow(t, onAct, onRemoveAsk)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 长按那一行问的那一框。形状照「删除」那一框（内容左对齐、两颗压在右下、40dp 高、15sp 字、
 * 左右各 4dp 内衬、两颗之间明着垫 8dp），不另起一套。
 *
 * 内容里报的是**已经下了多少**，不是整条的大小 —— 按下去要从盘上没掉的是那一个数，
 * 说"6.25 GB"会让人以为删的是整条的额度。
 */
@Composable
fun DownloadRemoveDialog(t: DownloadQueue.Task, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    InputSheet("移除这一条", onDismiss) {
        Text(
            if (t.done > 0) "已经下好的 ${MediaLibrary.fmtSize(t.done)} 会一起删掉，这一条从列表里撤走。"
            else "这一条还没下到东西，直接从列表里撤走。",
            color = Tokens.Ink, fontSize = 15.sp,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .tap(onClick = onDismiss)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Text("取消", color = Tokens.InkFaint, fontSize = 15.sp) }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .tap(onClick = onConfirm)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterEnd
            ) { Text("确认", color = Tokens.InkStrong, fontSize = 15.sp) }
        }
    }
}

/** 粘出来的那一条：封面 + 两行字。这一屏不摆播放/队列那三颗图标 —— 这里的动作只有一个 */
@Composable
private fun PickedCard(title: String, meta: String?, cover: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardBg)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var edge by remember { mutableIntStateOf(0) }
        val bmp = remoteCover(cover, edge)
        Box(
            modifier = Modifier
                .size(56.dp)
                .onSizeChanged { edge = minOf(it.width, it.height) }
                .clip(RoundedCornerShape(Tokens.Dimen.ThumbCorner))
                .background(Tokens.Track),
            contentAlignment = Alignment.Center,
        ) {
            if (bmp != null) {
                Image(bmp, null, modifier = Modifier.fillMaxSize())
            } else {
                Text(title.take(1), color = Color.White, fontSize = 18.sp)
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                title,
                color = Tokens.InkStrong,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(meta, color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/**
 * 那一整行按下去的大按钮。开始下载和交出去这两颗共用它 —— 同一屏里两个互斥动作 * 该长得一模一样，差别只在字，否则看着像一档高一档低。按不动时置灰不消失。
 */
@Composable
private fun BigButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Tokens.Dimen.FilterBtnH)
            .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
            .background(if (enabled) Tokens.Accent else Tokens.TrackIdle)
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) Color.White else Tokens.InkFaint, fontSize = 15.sp)
    }
}

/**
 * 一档清晰度。样式照「自定义接口」那一屏的地址源芯片那五项（底色 / 圆角 / 内衬 / 字色 / 字号）抄，
 * 这一屏不另起一套。
 */
@Composable
private fun QualityChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Tokens.Accent else Tokens.CardBg)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = if (on) Color.White else Tokens.InkStrong, fontSize = 14.sp, maxLines = 1)
    }
}

/**
 * 一行任务。右边那一颗**按状态换字形**：正在下=暂停、停了=继续、成了=移除。
 * 一行只留一个动作命中盒（40dp），不是并排摆三四颗 —— 那一族的行本来就不高，多一颗就多一次按错。
 *
 * 代价是"停住的那一条没有移除的口"（那颗给了继续）。那个口开在**长按整行**上：
 * 行的 `onClick` 留空，所以按行本身什么都不做（还是只有那一颗是动作），
 * 长按才问那一句 —— 加一颗而不改已经定稿的那一颗。
 */
@Composable
private fun TaskRow(
    t: DownloadQueue.Task,
    onAct: (DownloadQueue.Task) -> Unit,
    onRemoveAsk: (DownloadQueue.Task) -> Unit,
) {
    val pct = if (t.total > 0) (t.done * 100 / t.total).toInt() else -1
    val status = when (t.state) {
        DownloadQueue.State.Waiting -> "排队中"
        DownloadQueue.State.Running -> if (pct >= 0) "$pct%" else "下载中"
        DownloadQueue.State.Paused -> "已暂停"
        DownloadQueue.State.Done -> "已存好"
    }
    val second = when (t.state) {
        DownloadQueue.State.Running -> listOfNotNull(
            // 磁力那一路才有：0 个人和"下得慢"是两件事，界面上必须能分开 —— 一个要换一条，一个再等会
            if (t.peers >= 0) "${t.peers} 人" else null,
            fmtSpeed(t.bytesPerSec),
            if (t.remainingSec >= 0) "剩 ${fmtEta(t.remainingSec)}" else null,
            if (t.total > 0) "${MediaLibrary.fmtSize(t.done)} / ${MediaLibrary.fmtSize(t.total)}" else null,
        ).joinToString(" · ").ifBlank { null }
        else -> listOfNotNull(
            t.meta,
            // 停了的那一档也要看得见已经下了多少：重启回来那一行只剩"上次没下完"，
            // 底下是 200 MB 还是 2 MB，差的是要不要按下去等它
            if (t.total > 0 && t.done > 0) "已下 ${MediaLibrary.fmtSize(t.done)} / ${MediaLibrary.fmtSize(t.total)}"
            else t.sizeText(),
        ).joinToString(" · ").ifBlank { null }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // onClick 给空的是因为这一族的 `combinedClickable` 那个参数不许 null：行本身不该有
            // "按一下"这件事（动作在右边那一颗，它是子节点、先吃掉点击），这里只借它的长按
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
                onLongClick = { onRemoveAsk(t) },
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t.name,
                    color = Tokens.InkStrong,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    status,
                    color = when (t.state) {
                        DownloadQueue.State.Done -> Tokens.Accent
                        else -> Tokens.InkFaint
                    },
                    fontSize = 12.sp,
                )
            }
            if (second != null) {
                Text(second, color = Tokens.InkFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
            }
            if (t.note != null) {
                Text(
                    t.note!!,
                    color = if (t.state == DownloadQueue.State.Paused) FailRed else Tokens.InkFaint,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (t.state == DownloadQueue.State.Running) {
                // 3dp 厚、2dp 圆角：和播放页那根进度线（4.9dp）不是一个东西，这一屏没有要拇指去按的地方
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Tokens.TrackIdle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (pct >= 0) pct / 100f else 0f)
                            .height(3.dp)
                            .background(Tokens.Accent)
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                .background(Tokens.CardBg)
                .tap(onClick = { onAct(t) }),
            contentAlignment = Alignment.Center,
        ) {
            LineIcon(
                when (t.state) {
                    DownloadQueue.State.Running -> IconKind.Pause
                    DownloadQueue.State.Done -> IconKind.Close
                    else -> IconKind.Play
                },
                size = if (t.state == DownloadQueue.State.Running) 14.dp else 16.dp,
                color = Tokens.InkStrong,
            )
        }
    }
}

private fun DownloadQueue.Task.sizeText(): String? =
    if (total > 0) MediaLibrary.fmtSize(total) else null

/** 字节/秒 → 「320 KB/s」。1024 进制：下载工具都这么写，跟着它们才不用解释 */
private fun fmtSpeed(bps: Long): String? = when {
    bps <= 0 -> null
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000.0)
    bps >= 1_000 -> "%d KB/s".format(bps / 1000)
    else -> "%d B/s".format(bps)
}

/** 剩余秒数 → 「58 秒」「4 分 12 秒」「1 小时 05 分」。超过一小时就不报秒，那一格放不下 */
private fun fmtEta(sec: Long): String = when {
    sec < 60 -> "$sec 秒"
    sec < 3600 -> "%d 分 %02d 秒".format(sec / 60, sec % 60)
    else -> "%d 小时 %02d 分".format(sec / 3600, sec % 3600 / 60)
}
