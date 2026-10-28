package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.Blacklisted
import com.xiaojoo.lightplayer.data.MediaLibrary

/**
 * 黑名单那一整屏：在线那一栏删掉的视频，以后在线取回不再摆它们。
 *
 * 这一屏只管两件事 —— 看得见删过哪些、以及把某一条移出去。
 * 「清空」那一颗不做：他没点过名，而一颗能一按带走整份名单的按钮，代价要到误按那一刻才看得见。
 * 行尾那颗 ✕ 是**逐条**移出，一次只动一条。
 *
 * 样式照开它的那一屏（「视频地址源」）：标题行是返回箭头 + 居中的名字，右端不另摆出口
 * （箭头就是出口，两颗出口键是一件事办两遍）；行尾那颗图标用列表行那一族的 [RowIcon]
 * （39.5dp 盒 + 25dp 画框 + 归一墨），不在这边另配一套尺寸。
 */
@Composable
fun VideoBlacklistPage(
    rows: List<Blacklisted>,
    /** 源那一格写站点自己报的名字，不在这边抄一份对照表 */
    sourceLabel: (String) -> String?,
    onRemove: (Blacklisted) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
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
                    .tap(onClick = onBack),
                contentAlignment = Alignment.CenterStart
            ) {
                LineIcon(IconKind.ArrowLeft, size = 22.dp, color = Tokens.InkStrong)
            }
            Text(
                "黑名单",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = if (rows.isEmpty()) "还没有屏蔽过视频" else "${rows.size} 条 · 移出一条就不再屏蔽它",
            color = Tokens.InkFaint,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp)
        )

        if (rows.isEmpty()) {
            // 空屏也得说清这一屏是从哪儿攒来的，不然站在这儿不知道少了哪一步
            Text(
                "在「视频 → 在线」那一栏按右下角那颗垃圾桶删掉的，会进这一份名单",
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)
            )
            return
        }

        LazyColumn(state = PageScrolls.list(), modifier = Modifier.fillMaxWidth().weight(1f)) {
            items(rows, key = { it.key + "|" + it.pageUrl }) { row ->
                Box(modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad)) {
                    BlacklistRow(row, sourceLabel) { onRemove(row) }
                }
            }
        }
    }
}

@Composable
private fun BlacklistRow(
    row: Blacklisted,
    sourceLabel: (String) -> String?,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.title, color = Tokens.InkStrong, fontSize = 15.sp, maxLines = 2)
            Text(
                text = listOfNotNull(
                    row.artist.takeIf { it.isNotBlank() },
                    MediaLibrary.fmtDuration(row.durationMs).takeIf { row.durationMs > 0 },
                    sourceLabel(row.sourceId) ?: row.sourceId.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                color = Tokens.InkFaint,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        RowIcon(IconKind.Close, Tokens.InkFaint, onClick = onRemove)
    }
}
