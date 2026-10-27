package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.OnlineSort

/**
 * 「视频地址源」那一屏：地址源做成选项卡 + 几行筛选，整屏，不是抽屉。
 *
 * 地址源那一行的芯片三态（他点名的那一条）：
 * - 正常 = 这一路在问；
 * - **点一下 = 禁用**：芯片变灰、右边多出一颗 X；
 * - **点 X = 删掉这一路**：芯片整个从名单里没了（连这一路带回来的条目一起收走）；
 * - 灰的那颗**再点一下 = 重新启用**。
 * 禁用和删除是两份名单：禁用不动他粘进来的那些（那是他挑好带回来的），删除才带走。
 *
 * 其余几组只摆**这一趟真读到的值**：风格只有 B 站的列表带 `tname`（抖音、快手那条不给），
 * 作者三家都给，发布时间 B 站是秒级时间戳、抖音是卡上那句「· 3天前」推的、快手是 timestamp。
 * 一组里一个值都没有就不摆那一组（空壳分组不做，和「筛选歌曲」那一屏同一条规矩）。
 */
@Composable
fun VideoSourcePage(
    sources: List<SourceRow>,
    pool: List<MusicVideo>,
    /** 你粘进来的那几条：现在和站点那些一起吃同一组筛选，所以芯片的候选值和「确定（N 条）」都要算上它们 */
    mine: List<MusicVideo>,
    start: VideoFilter,
    initialUrl: String,
    /** 黑名单那一行的条数，和点进去（0 也摆这一行：入口不能因为还没屏蔽过就没了） */
    blacklistCount: Int,
    onOpenBlacklist: () -> Unit,
    onToggleSource: (String) -> Unit,
    onDeleteSource: (String) -> Unit,
    onAddUrl: (String) -> Unit,
    /** 那条地址在解析中：那颗「+」按住不松、换成一枚转圈的弧（结果最快也要几秒，抖音那一趟 8 秒） */
    urlBusy: Boolean,
    /** 在线那一串现在按什么排。这一档不进 `draft`：它改的是**怎么排**、不是筛掉谁，
        所以点一颗立刻生效、立刻回写外面存起来，按不按「确定」都算数 */
    sort: OnlineSort,
    onSort: (OnlineSort) -> Unit,
    onApply: (VideoFilter) -> Unit,
    onCancel: () -> Unit,
) {
    val now = remember { System.currentTimeMillis() }
    var draft by remember { mutableStateOf(start) }
    var text by remember { mutableStateOf(initialUrl) }
    var stylesOpen by remember { mutableStateOf(false) }
    var authorsOpen by remember { mutableStateOf(false) }
    /** 哪一颗正在等"确认删除"：只允许一颗，点别处就换过去 */
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    /* 芯片的候选值和「确定（N 条）」数的是**这一屏筛完真会摆出来的那些**，所以把你粘进来的
       那几条也算进来（地址源那三颗不是筛选，仍只数站点拉回来的）。
       粘进来的那些没有发布时间，落到「站点没给」那一档 —— 那一档本来就是给"站点没报日期"留的。 */
    val all = pool + mine
    val styles = counted(all.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } })
    val authors = counted(all.map { it.artist }.filter { it.isNotBlank() })
    val ages = counted(all.map { VideoAge.of(it.publishedAtMs, now) })
    val lengths = counted(all.mapNotNull { VideoLen.of(it.durationMs) })
    val hit = filterVideos(all, draft, now).size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.SkinBase)
            .statusBarsPadding()
            .navigationBarsPadding()
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
                    .tap(onClick = onCancel),
                contentAlignment = Alignment.CenterStart
            ) {
                LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong)
            }
            Text(
                "视频地址源",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        // 粘地址那一行还是这一屏的事：抽屉没了，那颗「添加」的入口跟着搬到这里来。
        // 右内衬 21.8dp 不是 ScreenPad：要把加号那颗的**可见右沿**对到下面组标题那枚折叠箭头的
        // 墨右沿上。真机量的：箭头墨右沿在设备 x=1019（离屏右 60px），描边右沿先按 17dp 摆出来
        // 落在 x=1032（差 13px），补这一笔才对平。
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Tokens.Dimen.ScreenPad, end = 21.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tokens.CardSolid)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text("粘贴一条视频页地址，或一条视频直链", color = Tokens.InkFaint, fontSize = 14.sp)
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    cursorBrush = SolidColor(Tokens.Accent),
                    textStyle = TextStyle(color = Tokens.InkStrong, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        if (text.isNotBlank() && !urlBusy) { onAddUrl(text.trim()); text = "" }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            // 加号从"一枚裸图标"改成一颗按钮：和左边那条输入框同一套（38dp 高、12dp 圆角、
            // CardBg + 一道 Divider 描边）。没填东西时按不动、但不消失也不淡（置灰不消失那条规矩）。
            // 解析中把加号换成一枚转圈的弧（同一颗、同一格，盒子不涨一寸）：按下到结果回来最快几秒，
            // 中间原样不动会被当成"没按下去"（真机量的：按下到 Toast 8.6 秒）
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Tokens.CardSolid)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(12.dp))
                    .tap(enabled = text.isNotBlank() && !urlBusy) {
                        onAddUrl(text.trim()); text = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (urlBusy) SpinArc()
                else LineIcon(IconKind.Add, size = 19.dp, color = if (text.isNotBlank()) Tokens.InkStrong else Tokens.InkFaint)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            FilterGroup("地址源", collapsible = false, expanded = false, onToggle = {}, topPad = Tokens.Dimen.FilterGapFirstTop) {
                sources.forEach { r ->
                    SourceChip(
                        row = r,
                        count = pool.count { it.sourceId == r.id },
                        confirmDelete = pendingDelete == r.id,
                        onToggle = { pendingDelete = null; onToggleSource(r.id) },
                        onAskDelete = { pendingDelete = r.id },
                        onDelete = { pendingDelete = null; onDeleteSource(r.id) },
                    )
                }
            }
            /* 排序整块从「长按那一栏开的那层」搬到这里（他 10-17 点的：那一层改成设置）。
               四颗单选、各落在条目身上一个真字段上。不摆条数：这四档排的是同一批条目，
               没有"这一档里有几条"可报。 */
            FilterGroup("排序", collapsible = false, expanded = false, onToggle = {}) {
                OnlineSort.values().forEach { m ->
                    FilterChip(m.label, null, m == sort) { onSort(m) }
                }
            }
            if (styles.isNotEmpty()) {
                FilterGroup("风格", styles.size > 12, stylesOpen, { stylesOpen = !stylesOpen }) {
                    styles.forEach { (name, n) ->
                        FilterChip(name, n, name in draft.styles) {
                            draft = draft.copy(styles = draft.styles toggle name)
                        }
                    }
                }
            }
            if (authors.isNotEmpty()) {
                FilterGroup("作者", authors.size > 12, authorsOpen, { authorsOpen = !authorsOpen }) {
                    authors.forEach { (name, n) ->
                        FilterChip(name, n, name in draft.authors) {
                            draft = draft.copy(authors = draft.authors toggle name)
                        }
                    }
                }
            }
            if (ages.isNotEmpty()) {
                FilterGroup("发布时间", collapsible = false, expanded = false, onToggle = {}) {
                    VideoAge.all.forEach { b ->
                        val n = ages.firstOrNull { it.first == b }?.second ?: 0
                        if (n > 0) FilterChip(b, n, b in draft.ages) { draft = draft.copy(ages = draft.ages toggle b) }
                    }
                }
            }
            if (lengths.isNotEmpty()) {
                FilterGroup("时长", collapsible = false, expanded = false, onToggle = {}) {
                    VideoLen.all.forEach { b ->
                        val n = lengths.firstOrNull { it.first == b }?.second ?: 0
                        if (n > 0) FilterChip(b, n, b in draft.lengths) { draft = draft.copy(lengths = draft.lengths toggle b) }
                    }
                }
            }
            BlacklistEntry(blacklistCount, onOpenBlacklist)
            // 末排芯片 → 屏底补一条缝，宽度和「组标题 → 芯片」同档（FilterGapTail = 真机 35px），
            // 口径抄筛选音乐那一屏（d347744）。底部那两颗（重置/确定）摆出来时不补：
            // 那时这条缝由动作行自己的 top 内衬给，无条件加就叠成两条。
            if (draft == start && !draft.any) {
                Spacer(Modifier.height(Tokens.Dimen.FilterGapTail))
            }
        }

        if (draft != start || draft.any) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad,
                        top = 12.5.dp, bottom = 18.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(Tokens.Dimen.FilterBtnH)
                        .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .background(Tokens.CardSolid)
                        .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .tap { draft = VideoFilter() },
                    contentAlignment = Alignment.Center
                ) { Text("重置", color = Tokens.InkStrong, fontSize = 15.sp) }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .height(Tokens.Dimen.FilterBtnH)
                        .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .background(Tokens.Accent)
                        .tap { onApply(draft) },
                    contentAlignment = Alignment.Center
                ) { Text("确定（$hit 条）", color = Tokens.InkStrong, fontSize = 15.sp, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

/**
 * 「黑名单 N 条」那一行：这一屏唯一的整行入口，点进去是黑名单那一整屏。
 *
 * 几何照 [FilterHead] 那一行抄（行高 / 上下内衬 / 字号 / 右内衬 `ScreenPad - 10.dp`），
 * 所以这一行的字和上面「风格」「时长」那些组标题左对齐、右端那枚箭头和可折叠箭头那条的
 * 折叠箭头落在同一条右沿上。计数用芯片上那个 12sp 的灰字，不另发明一档。
 *
 * **整行都能按**（含左右内衬那一段），不只那一行字：这一行没有别的控件，命中区给宽一点没代价。
 */
@Composable
private fun BlacklistEntry(count: Int, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .tap(onClick = onOpen)
            .padding(
                start = Tokens.Dimen.ScreenPad,
                end = Tokens.Dimen.ScreenPad - 10.dp,
                top = Tokens.Dimen.FilterGapTop,
                bottom = Tokens.Dimen.FilterGapBot
            )
            .height(Tokens.Dimen.FilterHeadH),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "黑名单",
            color = Tokens.InkStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text("$count", color = Tokens.InkFaint, fontSize = 12.sp)
        Spacer(Modifier.width(3.dp))
        // 那枚箭头照组标题的折叠箭头同一份：34dp 的盒 + 20dp 的墨 + Tokens.Ink。
        // 盒子内不另挂 tap —— 整行已经能按，再套一层就变成两个主人。
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            LineIcon(IconKind.ChevronRight, size = 20.dp, color = Tokens.Ink)
        }
    }
}

private infix fun Set<String>.toggle(v: String): Set<String> = if (v in this) this - v else this + v

private fun counted(items: List<String>): List<Pair<String, Int>> =
    items.groupingBy { it }.eachCount().entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { it.key to it.value }

/**
 * 发布时间分档。`0` 是"这一路没给日期"（抖音卡上那句认不出来的时候），单独一档，
 * 不跟"更早"混 —— 混了就等于把没数据的那些算成老片子。
 */
object VideoAge {
    val all = listOf("一天内", "一周内", "一月内", "半年内", "更早", "站点没给")
    private const val DAY = 86_400_000L

    fun of(ms: Long, now: Long): String = when {
        ms <= 0L -> "站点没给"
        now - ms <= DAY -> "一天内"
        now - ms <= 7 * DAY -> "一周内"
        now - ms <= 30 * DAY -> "一月内"
        now - ms <= 183 * DAY -> "半年内"
        else -> "更早"
    }
}

/** 时长分档。站点没报时长（0）的不进任何一档：那一档摆出来是骗人的 */
object VideoLen {
    val all = listOf("1分内", "1~5分", "5~15分", "15~30分", "30分以上")

    fun of(ms: Long): String? = when {
        ms <= 0L -> null
        ms < 60_000L -> all[0]
        ms < 300_000L -> all[1]
        ms < 900_000L -> all[2]
        ms < 1_800_000L -> all[3]
        else -> all[4]
    }
}

/** 那一屏的筛选草稿：四组，空集 = 那一组不筛 */
data class VideoFilter(
    val styles: Set<String> = emptySet(),
    val authors: Set<String> = emptySet(),
    val ages: Set<String> = emptySet(),
    val lengths: Set<String> = emptySet(),
) {
    val any: Boolean get() = styles.isNotEmpty() || authors.isNotEmpty() || ages.isNotEmpty() || lengths.isNotEmpty()

    /**
     * 这一条过不过这一组筛选。单独抽出来是因为**粘进来的那几条也要问同一句**：
     * 它们以前不参与筛选、又永远排在最前，于是"筛到 0 条"屏上照样是原来那条（他 10-15 报的"没更新"）。
     */
    fun matches(v: MusicVideo, now: Long): Boolean = !any ||
        (styles.isEmpty() || v.category.orEmpty() in styles) &&
        (authors.isEmpty() || v.artist in authors) &&
        (ages.isEmpty() || VideoAge.of(v.publishedAtMs, now) in ages) &&
        (lengths.isEmpty() || VideoLen.of(v.durationMs) in lengths)
}

/**
 * 池子里筛得下的那些。粘进来的那几条（`onlineItems`）不走这一句 —— 那是他挑好带回来的，
 * 和「筛选歌曲」那一屏同一口径。认不出时长的条目在选了时长那一组时会被筛掉，这是筛选本来的意思。
 */
fun filterVideos(items: List<MusicVideo>, f: VideoFilter, now: Long): List<MusicVideo> =
    if (!f.any) items else items.filter { f.matches(it, now) }
