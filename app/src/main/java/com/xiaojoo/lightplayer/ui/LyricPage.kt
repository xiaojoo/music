package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LyricPage(
    lines: List<LyricLine>,
    positionMs: Long,
    source: String?,
    onSeek: (Long) -> Unit,
    onPickFolder: () -> Unit
) {
    if (lines.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("暂无歌词", color = Tokens.InkFaint, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "把和歌曲同名的 .lrc 放在一起，再选中那个文件夹",
                    color = Tokens.InkFaint.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .padding(top = 6.dp)
                )
                Text(
                    text = "选择歌词目录",
                    color = Tokens.Ink,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .tap(onClick = onPickFolder)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                source?.let {
                    Text(
                        text = it,
                        color = Tokens.InkFaint.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .padding(horizontal = 28.dp)
                            .padding(top = 12.dp)
                    )
                }
            }
        }
        return
    }

    val state = rememberLazyListState()
    var current by remember { mutableIntStateOf(-1) }
    LaunchedEffect(positionMs, lines) {
        current = Lrc.indexAt(lines, positionMs)
        if (current >= 0) state.animateScrollToItem(current)
    }

    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 120.dp)
    ) {
        itemsIndexed(lines) { i, line ->
            val active = i == current
            Text(
                text = line.text,
                color = if (active) Tokens.InkStrong else Tokens.Ink.copy(alpha = 0.55f),
                fontSize = if (active) 19.sp else 16.sp,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Tokens.Dimen.LyricLineHeight)
                    .tap { onSeek(line.timeMs) }
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
    }
}
