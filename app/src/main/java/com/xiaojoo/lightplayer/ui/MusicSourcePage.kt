package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.videosource.MusicVideo

/** 源列表里的一行。[sub] 那一行小灰字写的是这一路的网址（他点的：显示成网址）。 */
data class SourceRow(val id: String, val name: String, val sub: String, val enabled: Boolean = true)

/** 这一屏底下那份预览摆多少首。整批给首页「热门音乐」那一栏，这一屏只要证明"这一路问得通" */
private const val PREVIEW = 30

/**
 * 右端那一列的公共右沿：全部按「加入播放列表」那颗的墨右沿对齐 —— 真机量到它在设备 x990
 * （热歌那六行都是 989~990），离屏幕右 = 90px = **32.7dp**。
 * 一颗 48dp 的命中盒贴着内容右沿（1080−55=1025）摆时，墨右沿落在 `1025 − (48−墨宽)/2`，
 * 所以每颗要往右补 `(48 − 墨宽)/2 − (32.7 − 20)dp`：Close 22dp（墨 13.50dp）→ 4.5dp；
 * Close 19dp（墨 11.66dp）→ 5.5dp；Refresh 19dp（墨 10.30dp）→ 6.3dp
 * （那颗弧在右边留了缺口，整颗墨中心本来就偏左 0.6px）。
 * 这一笔一律补在**带 `tap` 的那颗盒子**上，不补在里面的字形上 —— 补错层就会出现
 * "按墨没反应、按旁边才生效"。
 */
private fun rightShift(inkDp: Float): Dp = ((48f - inkDp) / 2f - 12.7f).dp

/** 右端那一列的一颗图标：48dp 命中盒 + 按上面那条补过的右移；禁用时只是按不动、不消失 */
@Composable
internal fun RightIcon(
    kind: IconKind,
    inkDp: Float,
    canvas: Dp,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(Tokens.Dimen.IconHit)
            .offset(x = rightShift(inkDp))
            .tap(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { LineIcon(kind, size = canvas, color = if (enabled) Tokens.Ink else Tokens.InkFaint) }
}

/**
 * 在线音乐源那一屏（播放页右上角那颗齿轮开的）。样式照「视频地址源」那一屏（他 10-14 给的那张图）：
 * 标题行是**返回箭头 + 居中的名字**（右端那颗 ✕ 撤了 —— 箭头就是出口，两颗出口键是一件事办两遍）；
 * 地址是一枚胶囊 + 右边一颗方形按钮（那颗是「刷新」：问一次那四张榜）；
 * 下面按组分芯片，组标题用同一只 [FilterHead]，行高/内衬/字号不另抄一份。
 *
 * 胶囊那行右内衬 21.8dp 不是 ScreenPad：那是「视频地址源」那一屏量出来的一条右沿
 * （那颗加号的可见右沿落在设备 x=1019，离屏右 60px）。这一屏没有折叠箭头可以对，
 * 但两张屏是同一个设置入口的两半，右沿不同一条就看得出是两套东西。
 *
 * 这一屏只有网易云那一路。B 站那一路在「视频地址源」那一屏配，两份名单不共用（他 10-14 点的）；
 * 也不再摊歌单 —— 那一栏撤了，它的地址行跟着撤（留一行没有落点的输入框就是死控件）。
 *
 * 预览那几行右端是两颗（存盘 / 加入播放列表）：**对齐基准还是队列那颗的墨右沿**，
 * 新加的那颗往左长 —— 摆在基准右边就要把上面那一列 rightShift 全部重算。
 */
@Composable
fun MusicSourcePage(
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    sources: List<SourceRow>,
    /** 芯片上那颗红「确认删除」按下去才调这一句：名单是单向的，没有恢复的入口 */
    onDelete: (String) -> Unit,
    /** 那四张榜正在问：方形按钮按不动，但不消失 */
    busy: Boolean,
    /** 只有问不通的时候才有话；"拿到 N 条"那一句他点名不要了 */
    note: String?,
    onFetch: () -> Unit,
    /** 拉回来的整批：芯片上那个数从这儿数，底下那份预览只摆前 [PREVIEW] 首 */
    pool: List<MusicVideo>,
    playingId: String?,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    /** 预览那一行的「存盘」：和播放页右滑第三屏那两行同一颗键、同一个走法 */
    onDownload: (MusicVideo) -> Unit,
    downloadingKey: String?,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    /** 哪一颗正在等"确认删除"：只允许一颗，点别处就换过去 */
    var pendingDelete by remember { mutableStateOf<String?>(null) }

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
                "在线音乐源",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = Tokens.Dimen.ScreenPad, end = 21.8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .background(Tokens.CardBg)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (baseUrl.isEmpty()) {
                    Text("留空回到默认的网易云，要换镜像就填那台的根地址", color = Tokens.InkFaint, fontSize = 14.sp)
                }
                BasicTextField(
                    value = baseUrl,
                    onValueChange = onBaseUrl,
                    singleLine = true,
                    cursorBrush = SolidColor(Tokens.Accent),
                    textStyle = TextStyle(color = Tokens.InkStrong, fontSize = 14.sp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            // 那颗方形按钮和左边那条胶囊同一套（38dp 高、12dp 圆角、CardBg + 一道 Divider 描边）；
            // 它的活是「问一次那四张榜」，所以是刷新那枚，不是加号
            Box(
                modifier = Modifier
                    .size(width = 52.dp, height = Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .background(Tokens.CardBg)
                    .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .tap(enabled = !busy, onClick = onFetch),
                contentAlignment = Alignment.Center,
            ) { LineIcon(IconKind.Refresh, size = 19.dp, color = if (busy) Tokens.InkFaint else Tokens.InkStrong) }
        }
        if (note != null) {
            Text(
                note,
                color = Color(0xFFC0392B),
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad, top = 10.dp
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            FilterGroup(
                "地址源",
                collapsible = false,
                expanded = false,
                onToggle = {},
                topPad = Tokens.Dimen.FilterGapFirstTop,
            ) {
                sources.forEach { r ->
                    SourceChip(
                        row = r,
                        count = pool.count { it.sourceId == r.id },
                        confirmDelete = pendingDelete == r.id,
                        // 视频那一屏有三颗，点别处就收回；这一屏只有网易云一颗，没有"别处"可点，
                        // 所以再按同一颗就是收回 —— 不然出了红字只剩"删"和"退出这一屏"两条路
                        onAskDelete = { pendingDelete = if (pendingDelete == r.id) null else r.id },
                        onDelete = { pendingDelete = null; onDelete(r.id) },
                    )
                }
            }
            if (pool.isNotEmpty()) {
                FilterHead("预览")
                pool.take(PREVIEW).forEach { v ->
                    Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                        MusicRow(
                            video = v,
                            playing = "online:" + v.key == playingId,
                            onClick = { onPlay(v) },
                            onQueue = { onQueue(v) },
                            onDownload = { onDownload(v) },
                            downloading = downloadingKey == v.key,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/**
 * 一组里两行之间那道发丝线。「视频地址源」那一屏的两张抽屉里也在用，所以留在这里一份，
 * 别处不另画一条（两条线就是两个主人，迟早差 1px）。
 */
@Composable
internal fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = Tokens.Dimen.ScreenPad)
            .background(Tokens.Divider)
    )
}

/**
 * 地址源那一行的一颗：名字 + 这一屏预览里它贡献了几首 + 右端一枚勾。
 * **点一下进"待确认删除"**（那一格换成红底白字），再按才真删。
 * 高度钉死 36dp：换成那三个字不许把这一行撑高（和「视频地址源」那一屏同一条件）。
 *
 * 为什么不做视频那一屏的三态（点一下先禁用、X 才删）：视频那一路有三家，禁用一家还有别家顶上；
 * 音乐这一路只有网易云一家，"禁用"它就是整条音乐在线没了 —— 和删掉是同一件事，
 * 多一步只会让人以为还能救。所以这一颗只有"删"这一个动作，但要按两下。
 */
@Composable
private fun SourceChip(
    row: SourceRow,
    count: Int,
    confirmDelete: Boolean,
    onAskDelete: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Tokens.CardBg)
            .tap(onClick = onAskDelete)
            .padding(start = 14.dp, end = if (confirmDelete) 6.dp else 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.name, color = Tokens.InkStrong, fontSize = 14.sp, maxLines = 1)
            Spacer(Modifier.width(5.dp))
            Text("$count", color = Tokens.InkFaint, fontSize = 12.sp)
            Spacer(Modifier.width(3.dp))
            if (confirmDelete) {
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        // 那颗红照「视频地址源」那一屏同一支：参考图上取样 (150,35,35)
                        .background(Color(0xFF962323))
                        .tap(onClick = onDelete)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text("确认删除", color = Color.White, fontSize = 12.sp, maxLines = 1) }
            } else {
                // 勾住在一个 28dp 的槽里：和视频那一屏那颗同一份，两颗一比较才看得出左边间隙一样
                Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    LineIcon(IconKind.Check, size = 12.dp, color = Tokens.Accent)
                }
            }
        }
    }
}
