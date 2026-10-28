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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.videosource.ApiSource
import com.xiaojoo.lightplayer.videosource.ApiSong
import com.xiaojoo.lightplayer.videosource.MusicVideo


/**
 * 首页那颗搜索框那一屏的状态：词、两条路各自的结果、在飞、报错、现在按的是哪一路。
 *
 * 摆在这一页（而不是 `App` 里八颗 `remember`）是给 `App` 那个 JVM 64KB 的方法体让位 ——
 * 搬走的只是**格子**：谁去问、按下去做什么还在 `App`（`runApiSearch` / `pickApiLane`）。
 */
class ApiSearchState {
    var keyword by mutableStateOf("")
    /** false = 问 [selected] 那一份地址源，true = 问锤子音乐那一站 */
    var chuizi by mutableStateOf(false)
    var sources by mutableStateOf<List<ApiSource>>(emptyList())
    var selected: ApiSource? by mutableStateOf(null)
    var results by mutableStateOf<List<ApiSong>>(emptyList())
    var chuiziHits by mutableStateOf<List<MusicVideo>>(emptyList())
    var busy by mutableStateOf(false)
    var error: String? by mutableStateOf(null)
}

/** 抽屉里那两块白卡片的高度，和「新建歌单」那一层同一只（Shell 里量的 51dp） */
private val FieldH = 51.dp

/**
 * 首页那颗搜索框点进来的那一屏：**问的是站点，不是本机曲库**。两条路一排芯片摆在一起：
 * 锤子音乐那一站（代码里接好的，被关掉就不摆那颗）和每一份「地址源」—— 一次只按一颗，
 * 换一颗就把上一颗的行撤干净再问（见调用点 `pickApiLane`）。
 *
 * 顶栏那一条（← + 词 + ✕，不套胶囊、不再摆放大镜）和列表页的搜索态是同一份几何 ——
 * 他要的是"在本地视频那个搜索视频的基础上改"，所以改的是**问谁**，不是长什么样。
 *
 * 底下一行是**地址源芯片**：第一颗永远是「添加地址源」，点开底部的抽屉；后面每一颗就是一个源。
 * 一颗都不预置（仓库是公开的，代码里不写任何一家域名），所以刚装上只有那第一颗。
 * 源吃什么描述见 `ApiSource` —— 路径、`act=`、表单里那几格叫什么、返回的数组在第几层，全在描述里，
 * 换地址、加参数都不用改代码。
 *
 * 结果行复用在线音乐那一族 [MusicRow]：右端那两颗（下载 / 加入播放列表）吃 [RowIcon] 那只等宽命中盒，
 * 所以每一行的墨落在同一条竖线上、两颗之间的白缝等宽 —— 这一屏不另算一套。
 */
@Composable
fun ApiSearchPage(
    /** 这一屏自己的格子（见 [ApiSearchState]）：词、两路的结果、在飞、报错 */
    api: ApiSearchState,
    /** 选一份地址源（换过去就把锤子那一路的行撤掉，见调用点 `pickApiLane`） */
    onSelect: (ApiSource) -> Unit,
    /** 交回来的那份描述名字撞了就说 false，抽屉自己把话写在卡片下面 */
    onAddSource: (ApiSource) -> Boolean,
    onSelectChuizi: () -> Unit,
    /** 键盘那颗「搜索」：按下去才问，不跟着击键发 */
    onSubmit: () -> Unit,
    playingKey: String?,
    downloadingKey: String?,
    onPlay: (ApiSong) -> Unit,
    onQueue: (ApiSong) -> Unit,
    onDownload: (ApiSong) -> Unit,
    /**
     * 锤子音乐那一路的名字：和「我的」那一屏第三颗芯片同一个来路（`Online.sourceLabel("chuizi")`）——
     * 那一路被关掉/删掉时是 null，这颗芯片整颗不出现（不摆一颗按下去永远空白的路）。
     */
    chuiziLabel: String?,
    chuiziPlayingId: String?,
    chuiziDownloadingKey: String?,
    onPlayChuizi: (MusicVideo) -> Unit,
    onQueueChuizi: (MusicVideo) -> Unit,
    onDownloadChuizi: (MusicVideo) -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current
    var addOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { fr.requestFocus() }

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
                    if (api.keyword.isEmpty()) {
                        Text("搜歌名或歌手", color = Tokens.InkFaint, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = api.keyword,
                        onValueChange = { api.keyword = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Tokens.InkStrong, fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // 这一屏问接口，不实时搜：每敲一个字打一次接口既慢、结果还会在字打一半时跳空
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide(); onSubmit() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    )
                }
                /* ✕ 一直占位：有词先清词，词空了再退这一页（和列表页搜索态同一只手势）。
                   这一行的三笔间隙一律从 pathData 反解，算式在 Tokens 的 searchCloseShift 那一组里：
                   ① 屏幕左 → 箭头墨 24.015dp，② 箭头墨 → 文字 和①同宽（改前 ② 是 30.895，文字起点抄的是
                   IconHit=48），③ ✕ 墨右沿离屏幕右 = ①×1.2 = 28.818dp（他 10-22 要"右边间隙大一点"，没给数）。
                   命中盒要**压在墨的中心上**：字形贴右（CenterEnd）时，48dp 盒的中心落在墨中心左边
                   `(48−19)/2 = 14.5dp` —— 真机量到墨中心 x=1006.5、盒中心 x=967，偏 39.5px，
                   所以点在 ✕ 上不算、往左一点才关得掉（他 10-15 点的）。
                   补法：整盒往右挪 14.5dp，再把字形往左挪同样多 —— 墨停在③那一条线上，只有命中带跟着墨走了。 */
                val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.searchCloseShift(Tokens.Dimen.ScreenPad) + hitSkew)
                        .tap { if (api.keyword.isEmpty()) api.keyword = "" else onBack() },
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
            // 第一颗永远是「添加地址源」：它不是选项，点它是开抽屉，不参与选中变色
            SourceChip("添加地址源", on = false) { addOpen = true }
            // 锤子音乐那一颗排在地址源前面：它是代码里接好的那一路，不用用户去抽屉里添
            val cz = chuiziLabel
            if (cz != null) SourceChip(cz, on = api.chuizi) { onSelectChuizi() }
            api.sources.forEach { s ->
                SourceChip(s.name, on = !api.chuizi && s.name == api.selected?.name) { onSelect(s) }
            }
        }
        /* 两路共用下面这一块：换一路的时候另一路的行整列撤干净（见调用点 `pickApiLane`），
           所以这里只管"现在这一路有几条、空的时候说什么"。 */
        val hits = if (api.chuizi) api.chuiziHits.size else api.results.size
        val err = api.error
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                // 还没回来的那一段：屏幕正中一枚转圈的弧，和音乐那一屏的在线同一件东西
                api.busy && hits == 0 -> SpinArc(Modifier.align(Alignment.Center))
                err != null && hits == 0 -> Text(
                    err, color = FailRed, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center)
                )
                hits == 0 -> Text(
                    // 一句真话：这一路没得问才说"先去添"。锤子那颗芯片在的时候不能只说添地址源——
                    // 上面明明摆着一颗按得动的芯片，那句话就成了假话（他 10-28 对空屏摆 0 是同一条口径）
                    when {
                        api.chuizi || api.selected != null -> "输入歌名，按键盘上那颗搜索"
                        chuiziLabel != null -> "这一路还没有地址源：点上面「$chuiziLabel」，或点「添加地址源」"
                        else -> "先点「添加地址源」"
                    },
                    color = Tokens.InkFaint,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                /* 锤子音乐那一路：行、两颗、口径全部照「我的」那一屏搜到的在线那一段（同一只 MusicRow），
                   第二行也是"歌手 · 时长 · 站点名"——站点没给的格子不写，不拿"未知"占位。 */
                api.chuizi -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(api.chuiziHits, key = { _, v -> "cz-" + v.key }) { i, v ->
                        if (i > 0) Hairline()
                        Row(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                            MusicRow(
                                video = v,
                                playing = "online:" + v.key == chuiziPlayingId,
                                onClick = { onPlayChuizi(v) },
                                onQueue = { onQueueChuizi(v) },
                                onDownload = { onDownloadChuizi(v) },
                                downloading = chuiziDownloadingKey == v.key,
                                sub = listOfNotNull(
                                    v.artist.takeIf { it.isNotBlank() },
                                    MediaLibrary.fmtDuration(v.durationMs).takeIf { v.durationMs > 0 },
                                    chuiziLabel,
                                ).joinToString(" · ").ifBlank { null },
                            )
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(api.results, key = { _, s -> s.key }) { i, s ->
                        if (i > 0) Hairline()
                        Row(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                            MusicRow(
                                video = s.asMusicVideo(),
                                playing = s.key == playingKey,
                                onClick = { onPlay(s) },
                                onQueue = { onQueue(s) },
                                onDownload = { onDownload(s) },
                                downloading = downloadingKey == s.key,
                                // 歌手 · 时长 · 站点报的那一档大小：下载的人真正在比的是"多大"
                                sub = listOfNotNull(
                                    s.artist.takeIf { it.isNotBlank() },
                                    s.durationSec.takeIf { it > 0 }?.let { MediaLibrary.fmtDuration(it * 1000L) },
                                    s.qualities.firstOrNull()?.size?.takeIf { it.isNotBlank() },
                                ).joinToString(" · ").ifBlank { null },
                            )
                        }
                    }
                }
            }
        }
    }

    if (addOpen) {
        AddSourceSheet(
            onDismiss = { addOpen = false },
            onAdd = { src ->
                if (onAddSource(src)) {
                    addOpen = false
                    null
                } else {
                    "已经有同名地址源了"
                }
            },
        )
    }
}

/**
 * 一颗地址源芯片。样式照 Shell 的 `FilterChip` 那五项（底色 / 圆角 / 内衬 / 字色 / 字号）抄，
 * **只把计数那一格去掉** —— 这一屏没有"几首"可数，摆个 0 是假数。
 */
@Composable
private fun SourceChip(label: String, on: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Tokens.Accent else Tokens.CardBg)
            .tap(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (on) Color.White else Tokens.InkStrong,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

/** 抽屉里那两格白卡片：一格一行，标签在卡片里当占位字（和「新建歌单」那一层同一只卡片） */
@Composable
private fun FieldCard(
    hint: String,
    value: String,
    onValue: (String) -> Unit,
    focus: FocusRequester? = null,
    singleLine: Boolean = true,
    lines: Int = 1,
    keyboard: KeyboardType = KeyboardType.Text,
    onDone: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (singleLine) FieldH else (FieldH * lines).coerceAtLeast(120.dp))
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            if (value.isEmpty()) {
                Text(hint, color = Tokens.InkFaint, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValue,
                maxLines = if (singleLine) 1 else lines,
                cursorBrush = SolidColor(Tokens.InkStrong),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.InkStrong),
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Done,
                    keyboardType = keyboard,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier.fillMaxWidth().then(
                    if (focus != null) Modifier.focusRequester(focus) else Modifier
                ),
            )
        }
    }
}

/**
 * 「添加地址源」那一层：照「新建歌单」那一层的骨架（取消 / 标题 / 创建 + 白卡片），
 * 顶上多一颗模式开关 —— **三格和 JSON 是或关系，只能选一个**（他 10-15 点的）。
 *
 * 三格是快填：只有名字 / 地址 / 平台，其余全用默认形状。JSON 那一格交的是 `ApiSource` 吃的那份描述，
 * 参数名、返回在第几层、字段叫什么都能改 —— 换一家不用改代码。认不出来的时候创建那颗是灰的，
 * 话写在卡片下面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSourceSheet(onDismiss: () -> Unit, onAdd: (ApiSource) -> String?) {
    var jsonMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(TextFieldValue("", TextRange(0))) }
    var base by remember { mutableStateOf("") }
    var platform by remember { mutableStateOf("") }
    var json by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }

    val parsed: ApiSource? = if (jsonMode) {
        json.takeIf { it.isNotBlank() }?.let { ApiSource.fromJson(it) }
    } else if (name.text.isNotBlank() && base.isNotBlank()) {
        ApiSource(name = name.text.trim(), base = base.trim(), platform = platform.trim())
    } else {
        null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Tokens.ListBg,
        shape = RoundedCornerShape(topStart = Tokens.Dimen.SheetCorner, topEnd = Tokens.Dimen.SheetCorner),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Dimen.ScreenPad)
                .padding(top = 14.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "取消", color = Tokens.InkFaint, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).tap(onClick = onDismiss)
                )
                Text(
                    "添加地址源", color = Tokens.InkStrong, fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "创建",
                    color = if (parsed != null) Tokens.Accent else Tokens.InkFaint,
                    fontSize = 15.sp,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f).tap(enabled = parsed != null) {
                        val p = parsed
                        if (p != null) error = onAdd(p)
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceChip("填三格", on = !jsonMode) { jsonMode = false; error = null }
                SourceChip("粘 JSON", on = jsonMode) { jsonMode = true; error = null }
            }
            Spacer(Modifier.height(14.dp))
            if (jsonMode) {
                Text(
                    "只有 name 和 base 必填，其余每一格都可以不写（不写用默认形状）；" +
                        "认不出的键一律忽略。可写的格：platform、size、search{path,act}、link{path,act}、" +
                        "keywordParam/pageParam/sizeParam/idParam/timeParam/signParam、extra{…}、" +
                        "fields{list,name,artist,album,pic,duration,link,time,sign,quality}。",
                    color = Tokens.InkFaint, fontSize = 11.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                FieldCard(
                    hint = """{"name":"网易云","base":"https://…","platform":"wyy"}""",
                    value = json,
                    onValue = { json = it; error = null },
                    singleLine = false,
                    lines = 7,
                    keyboard = KeyboardType.Uri,
                )
            } else {
                FieldCard("名字", name.text, { name = TextFieldValue(it, TextRange(it.length)) }, focus = fr)
                Spacer(Modifier.height(10.dp))
                FieldCard(
                    "地址（https://…）", base, { base = it; error = null },
                    keyboard = KeyboardType.Uri,
                )
                Spacer(Modifier.height(10.dp))
                FieldCard("平台值（可留空）", platform, { platform = it; error = null })
            }
            if (error != null) {
                Text(error!!, color = FailRed, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}
