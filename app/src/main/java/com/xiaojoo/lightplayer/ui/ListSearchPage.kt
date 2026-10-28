package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.Track

/**
 * 列表屏（音乐 / 视频 / 收藏 / 最近播放 / 歌单）顶栏那支放大镜点进来的**独立一屏**。
 *
 * 原来这一屏是复用列表组件的"搜索态"：同一屏只把顶栏换成输入框，下面还是那张列表，
 * 看着就知道自己没换页。现在单独一屏，版式抄「我的」顶上那颗点进去的 [LocalSearchPage]：
 * 顶栏（← + 词 + ✕）→ 发丝线 → 一行计数 → 结果。行还是列表那一族 [TrackLine]，
 * 命中那一段描绿也是它自己带的，这一屏不另画一套。
 *
 * 搜的只有**进来之前那一屏那一串**（在哪个歌单里就搜那个歌单），不是本机全部 ——
 * 本机全部是 [LocalSearchPage] 那一屏，两屏别混。
 */
@Composable
fun ListSearchPage(
    /** 进来之前那一屏的标题，写进那一行计数前面（「收藏音乐 · 命中 12 首」） */
    title: String,
    /** 量词：音乐是"首"、视频是"个" */
    unit: String,
    searchHint: String,
    tracks: List<Track>,
    query: String,
    onQuery: (String) -> Unit,
    /** 视频那一串带缩略图，和列表屏同一个开关 */
    thumbs: Boolean,
    favorites: Set<String>,
    onToggleFavorite: (Track) -> Unit,
    onQueueAdd: (Track) -> Unit,
    onPlayList: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来时，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    val fr = remember { FocusRequester() }
    val kbd = LocalSoftwareKeyboardController.current
    // 进来就能打字：和「我的」那一屏、歌手搜索那一屏同一套（要按一下就出键盘的搜索页等于没做完）
    LaunchedEffect(Unit) { fr.requestFocus() }

    val q = query.trim()
    // 匹配规则和列表屏的搜索态、和 LocalSearchPage 同一个：歌名或歌手含这个词就算，不分大小写
    val hit = if (q.isEmpty()) emptyList() else tracks.filter {
        it.name.contains(q, ignoreCase = true) || it.artist?.contains(q, ignoreCase = true) == true
    }

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
                    if (query.isEmpty()) {
                        Text(searchHint, color = Tokens.InkFaint, fontSize = 16.sp)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        cursorBrush = SolidColor(Tokens.InkStrong),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Tokens.InkStrong, fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // 本机这一串是现成的，边打字边出结果；键盘上那颗「搜索」只用来收起键盘看全列表
                        keyboardActions = KeyboardActions(onSearch = { kbd?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr),
                    )
                }
                // ✕ 的位置和命中盒补法跟「我的」那一屏同一套，见 Tokens 里 searchCloseShift 那段
                val hitSkew = (Tokens.Dimen.IconHit - 19.dp) / 2f
                Box(
                    modifier = Modifier
                        .size(Tokens.Dimen.IconHit)
                        .offset(x = Tokens.Dimen.searchCloseShift(Tokens.Dimen.ScreenPad) + hitSkew)
                        .tap { if (query.isEmpty()) onBack() else onQuery("") },
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
        Text(
            // 空词报"这一串一共几条可搜"，有词报命中几条 —— 空屏摆个 0 是假数（同一屏同一道理）
            text = if (q.isEmpty()) "$title · 共 ${tracks.size}$unit" else "$title · 命中 ${hit.size}$unit",
            color = Tokens.InkFaint,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 10.dp)
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                q.isEmpty() -> Text(
                    "输入歌名或歌手", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                hit.isEmpty() -> Text(
                    "没有匹配「$q」的条目", color = Tokens.InkFaint, fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(
                    state = PageScrolls.list(),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    itemsIndexed(hit, key = { _, t -> t.uri.toString() }) { i, t ->
                        TrackLine(
                            track = t, index = i, q = q, thumbs = thumbs,
                            favorite = t.uri.toString() in favorites,
                            onPlay = { onPlayList(hit, i) },
                            onToggleFavorite = { onToggleFavorite(t) },
                            onQueueAdd = { onQueueAdd(t) },
                        )
                    }
                }
            }
        }
    }
}
