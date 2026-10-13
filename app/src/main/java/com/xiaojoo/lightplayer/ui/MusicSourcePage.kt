package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.videosource.MusicVideo

/** 源列表里的一行。[sub] 那一行小灰字写的是这一路的网址（他点的：显示成网址）。 */
data class SourceRow(val id: String, val name: String, val sub: String)

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
private fun RightIcon(
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
 * 在线音乐源那一屏（右上角那颗齿轮开的）。
 *
 * 一行一个动作，右端一律图标、且全部右对齐到同一条墨右沿（见 [rightShift]）：
 * 地址那一格右边那颗是「获取」（刷新图标，跑着的时候置灰）；每一路右端那颗是 ✕（删掉这一路）。
 * 文字按钮、绿胶囊开关、「恢复」都撤了 —— 删掉即没了，这一屏没有回来的入口。
 * 参考那张里的「填网址装上」也没做：源是编译进来的，一条网址变不出新源。
 */
@Composable
fun MusicSourcePage(
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    sources: List<SourceRow>,
    /** 行末那颗 ✕：把这一路从列表和注册表里去掉，没有反悔的入口 */
    onDelete: (String) -> Unit,
    busy: Boolean,
    /** 只有问不通的时候才有话；"拿到 N 条"那一句他点名不要了 */
    note: String?,
    onFetch: () -> Unit,
    hot: List<MusicVideo>,
    playingId: String?,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            .then(if (bottomInset) Modifier.navigationBarsPadding() else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Text(
                "在线音乐源",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            RightIcon(
                IconKind.Close, inkDp = 13.50f, canvas = 22.dp, enabled = true,
                // 标题那格是 Box：不写这句它会掉到左上角（上一版就是这么漏的）
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = onBack,
            )
        }
        Hairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = Tokens.Dimen.ScreenPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = baseUrl,
                onValueChange = onBaseUrl,
                singleLine = true,
                cursorBrush = SolidColor(Tokens.Accent),
                textStyle = TextStyle(color = Tokens.InkStrong, fontSize = 14.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = Tokens.Dimen.ScreenPad, top = 14.dp, bottom = 14.dp)
            )
            RightIcon(IconKind.Refresh, inkDp = 10.30f, canvas = 19.dp, enabled = !busy, onClick = onFetch)
        }
        Hairline()
        if (note != null) {
            Text(
                note,
                color = Color(0xFFC0392B),
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad, top = 10.dp
                )
            )
            Hairline()
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            itemsIndexed(sources) { i, r ->
                if (i > 0) Hairline()
                SourceLine(row = r, onDelete = onDelete)
            }
            items(hot, key = { "hot-" + it.key }) { v ->
                Hairline()
                Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                    MusicRow(
                        video = v,
                        playing = "online:" + v.key == playingId,
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                    )
                }
            }
        }
    }
}

/** 一行源：名字 + 那一行网址，右端一颗 ✕。行高照参考那张量的 60.5dp */
@Composable
private fun SourceLine(row: SourceRow, onDelete: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(60.5.dp)
            .padding(horizontal = Tokens.Dimen.ScreenPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.name, color = Tokens.InkStrong, fontSize = 15.sp)
            Text(
                row.sub,
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        RightIcon(IconKind.Close, inkDp = 11.66f, canvas = 19.dp, enabled = true, onClick = { onDelete(row.id) })
    }
}

@Composable
private fun Hairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .padding(horizontal = Tokens.Dimen.ScreenPad)
            .background(Tokens.Divider)
    )
}
