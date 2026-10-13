package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.videosource.MusicVideo

/** 源列表里的一行：那一行小灰字写的是"这一路在我们这儿管哪一栏" */
data class SourceRow(val id: String, val name: String, val sub: String)

/**
 * 在线音乐源那一屏（右上角那颗齿轮开的）。
 *
 * 能按的东西一律同一颗按钮：8dp 圆角 + [Tokens.CardBg] 底 + 1dp Divider 描边 + 14sp 中位墨。
 * 一行一个动作：顶上那颗「获取」，每一路后面那颗「删除」。
 * 开关、"停用/启用"、"恢复"三样都撤了（他点的）：这一路要么在、要么被删掉，
 * 删掉就没有回来的入口 —— 名单是单向的，只有清应用数据能复原。
 *
 * 参考那张里的「填网址装上」也没做：源是编译进来的，一条网址变不出新源。
 */
@Composable
fun MusicSourcePage(
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    sources: List<SourceRow>,
    /** 行末那颗「删除」：把这一路从列表和注册表里去掉，没有反悔的入口 */
    onDelete: (String) -> Unit,
    busy: Boolean,
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
            /* 往右补 16.2dp 让墨右沿和下面那颗按钮的右沿齐平。这一笔必须加在**这颗盒子自己**上：
               上一版加在里面的 LineIcon 上，结果墨挪了、48dp 的命中框没挪，
               他报的症状就是"按叉没反应、按叉右边一点才关"。
               余量的出处：Close 这字形只吃画框 0.6136（真机墨 13.5dp / 画框 22dp），
               48dp 盒 + 22dp 画框两层余量 = 17.25dp。 */
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(Tokens.Dimen.IconHit)
                    .offset(x = 16.2.dp)
                    .tap(onClick = onBack),
                contentAlignment = Alignment.Center
            ) { LineIcon(IconKind.Close, size = 22.dp, color = Tokens.Ink) }
        }
        Hairline()
        BasicTextField(
            value = baseUrl,
            onValueChange = onBaseUrl,
            singleLine = true,
            cursorBrush = SolidColor(Tokens.Accent),
            textStyle = TextStyle(color = Tokens.InkStrong, fontSize = 14.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 14.dp)
        )
        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 跑着的时候这颗还在原位，只是按不动（禁用不等于移除）
            ActionButton(if (busy) "获取中…" else "获取", onClick = onFetch, enabled = !busy)
        }
        if (note != null) {
            Text(
                note,
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(
                    start = Tokens.Dimen.ScreenPad, end = Tokens.Dimen.ScreenPad, top = 10.dp
                )
            )
        }
        Hairline()
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

/** 一行源：名字 + 那一行小灰字，右端一颗「删除」。行高照参考那张量的 60.5dp */
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
        ActionButton("删除", onClick = { onDelete(row.id) })
    }
}

/**
 * 这一屏唯一的按钮形状：8dp 圆角 + [Tokens.CardBg] 底 + 14sp 中位墨，内衬 16 x 9dp。
 * 禁用是**置灰不消失**（他定的那条：禁用不等于移除）—— 字色退到 [Tokens.InkFaint]、
 * 笔画退到常规，位置一格不动。
 */
@Composable
private fun ActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardBg)
            // 1dp 描边照筛选页那颗「重置」的写法：CardBg(250) 压在 ListBg(241,244,249) 上只差
            // (9,6,1)，那一档在浅色底上看不出来，得给一条 Divider 的分界
            .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.CardCorner))
            .tap(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (enabled) Tokens.InkStrong else Tokens.InkFaint,
            fontSize = 14.sp,
            fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal
        )
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
