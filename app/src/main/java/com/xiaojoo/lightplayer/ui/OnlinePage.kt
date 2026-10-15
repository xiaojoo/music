package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.MediaLibrary
import com.xiaojoo.lightplayer.data.Online
import com.xiaojoo.lightplayer.videosource.MusicVideo
import com.xiaojoo.lightplayer.videosource.SearchReport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 在线 MV 的整屏搜索页：顶栏就是输入框（照本地搜索页那一条：← + 词 + ✕，不套胶囊、不摆放大镜），
 * 结果落在下面整页。这一页是"页面"不是"抽屉"，所以底栏整条让位 —— 和筛选页、搜索页同一套规矩。
 */
@Composable
fun OnlinePage(onBack: () -> Unit, onPlay: (MusicVideo) -> Unit) {
    var query by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<SearchReport?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { fr.requestFocus() }

    // 空查询也问一次：这一页进来就该有默认列表，摆一个"请输入关键词"的空页等于没做
    fun run(q: String) {
        searching = true
        scope.launch {
            report = Online.search(q.trim())
            searching = false
        }
    }

    // 300ms  debounce：打一个字问一次源会把站点刷爆，本地也一样
    LaunchedEffect(query) {
        delay(300)
        run(query)
    }

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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Tokens.Dimen.IconHit),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索在线 MV", color = Tokens.InkFaint, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Tokens.InkStrong,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide(); run(query) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .tap { if (query.isEmpty()) onBack() else query = "" },
                    contentAlignment = Alignment.Center
                ) {
                    LineIcon(IconKind.Close, size = 19.dp, color = Tokens.Ink)
                }
            }
        }

        val r = report
        Text(
            text = when {
                searching -> "搜索中…"
                r == null -> "加载中…"
                else -> statusLine(r)
            },
            color = Tokens.InkFaint,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp)
        )

        if (r != null && !searching) {
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(r.groups, key = { it.matchKey }) { g ->
                    val first = g.items.first()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .tap { onPlay(first) }
                            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(first.title, color = Tokens.InkStrong, fontSize = 15.sp)
                            Text(
                                text = listOfNotNull(
                                    first.artist?.takeIf { it.isNotBlank() },
                                    MediaLibrary.fmtDuration(first.durationMs).takeIf { first.durationMs > 0 },
                                    // 一条歌在几个源里都有就报几路，合并这件事得看得见
                                    if (g.items.size > 1) "${g.items.size} 路源" else "本机占位源"
                                ).joinToString(" · "),
                                color = Tokens.InkFaint,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Text(
                            text = "播放",
                            color = Tokens.Accent,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(20.dp)) }
            }
        }
    }
}

/** 状态行每个数都带指标名：命中几条、几个源答了、几个慢了、几个报错、用了多少毫秒 */
private fun statusLine(r: SearchReport): String {
    val parts = listOfNotNull(
        "命中 ${r.groups.size} 条",
        "问过 ${r.sourcesTouched} 个源",
        "超时 ${r.slowSources.size} 个".takeIf { r.slowSources.isNotEmpty() },
        "失败 ${r.failures.size} 个".takeIf { r.failures.isNotEmpty() },
        "用时 ${r.tookMs} ms",
    )
    return parts.joinToString(" · ")
}
