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
import androidx.compose.foundation.layout.offset
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
 * 「相关视频」那一整屏：本地那一栏右侧那颗放大镜开进来的，**进去就带着眼前这一条的片名搜过一次**，
 * 结果直接摆在下面（原来这一屏进来是个空输入框，等于让他自己再报一遍刚才在看什么）。
 *
 * 词是外面从当前那条派生好再递进来的（`Online.searchKeywordOf`：括号和分隔符摊成空格、
 * 音质词和栏目名剔掉），这一屏只负责把它显示在输入框里、让他能改、改完再问一次 ——
 * 词不写死：文件名那种「VID_20261016_011203」提出不来好词，能改词是唯一出路。
 *
 * 问的是**给画面那几路**（`onlyVideo = true`）：网易云那一路也声明了 Search，不筛就会往里掺单曲。
 * 删过的（黑名单）不在结果里 —— 那道闸门在 [Online.search] 出口，不在这一屏。
 *
 * 这一页是"页面"不是"抽屉"，所以底栏整条让位 —— 和筛选页、搜索页同一套规矩。
 * 顶栏就是输入框（照本地搜索页那一条：← + 词 + ✕，不套胶囊、不摆放大镜）。
 */
@Composable
fun VideoRelatedPage(
    /** 外面按当前那一条派生好的搜索词。空串 = 那一条提不出词，进来就是个空框（状态行会说） */
    initialQuery: String,
    onBack: () -> Unit,
    onPlay: (MusicVideo) -> Unit,
) {
    var query by remember { mutableStateOf(initialQuery) }
    var report by remember { mutableStateOf<SearchReport?>(null) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) { fr.requestFocus() }

    /* 提不出词就不问。
       原来这一句是"空查询也问一次，这一屏进来就该有东西摆"（那是旧的在线搜索整屏）。
       这一屏的意思变了：它是**眼前这一条**的相关视频。真机上量到：一条文件名叫
       `5d8d8012b0ed20c089624956350a9730` 的片子提出空词，空词下去站点给回 39 条默认列表
       （快手 + B 站各摊一半）—— 一屏和这条片子毫无关系的画面，顶着「相关视频」的名字摆在那儿。
       没有词就直说没有词，让他自己打一个。 */
    fun run(q: String) {
        if (q.isBlank()) {
            report = null
            searching = false
            return
        }
        searching = true
        scope.launch {
            report = Online.search(q.trim(), onlyVideo = true)
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
                    .padding(start = Tokens.Dimen.searchTextStart(Tokens.Dimen.ScreenPad)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("搜索在线视频", color = Tokens.InkFaint, fontSize = 16.sp)
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
                // ✕ 这一行五屏同一套几何（这一屏改前压根没补 offset，墨比另外四屏往左偏出一大截）：
                // 位置见 Tokens 的 searchCloseShift，命中盒那 14.5dp 的补法见 ApiSearchPage 那段注释
                val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.searchCloseShift(Tokens.Dimen.ScreenPad) + hitSkew)
                        .tap { if (query.isEmpty()) onBack() else query = "" },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    LineIcon(
                        IconKind.Close, size = 19.dp, color = Tokens.Ink,
                        modifier = Modifier.offset(x = -hitSkew),
                    )
                }
            }
        }

        val r = report
        Text(
            text = when {
                searching -> "搜索中…"
                query.isBlank() -> "这一条提不出搜索词，打一个字再问"
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
                                    first.artist.takeIf { it.isNotBlank() },
                                    MediaLibrary.fmtDuration(first.durationMs).takeIf { first.durationMs > 0 },
                                    // 一条片子在几个源里都有就报几路；只有一路就写那一路自己报的名字
                                    if (g.items.size > 1) "${g.items.size} 路源"
                                    else Online.sourceLabel(first.sourceId) ?: first.sourceId
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
