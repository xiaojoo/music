package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.Appearance

/**
 * 播放页右上角那颗「画幅」点进来的整屏页：中间那一格换什么。
 *
 * 顶栏、组标题、内衬和 [AppearancePage] 同一套（48dp 顶栏 + [Hairline] + [FilterHead] + ScreenPad 20dp），
 * 形态也是"整屏页不是抽屉" —— 和「在线音乐源」「视频地址源」同一族（他 10-14 定过：设置类的清单走整屏页）。
 *
 * **点一颗立刻生效并落盘**，和装扮那一屏同一条规则。这一屏盖在播放页上面、看不到身后的结果，
 * 所以中间给一格 [ArtPreview]：那一块就是换完之后的样子，不用退出去再确认一次。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerArtPage(
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和装扮那一屏同一个开关） */
    bottomInset: Boolean,
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.Backdrop)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
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
                "播放画幅",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()

        FilterHead("中间那一格", topPad = 12.dp)
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Appearance.ART_MODES.forEach { (mode, label) ->
                FilterChip(label = label, on = Skin.artMode.value == mode) {
                    Skin.setArtMode(context, mode)
                }
            }
        }

        FilterHead("预览", topPad = 12.dp)
        ArtPreview()
        Text(
            "海报档吃的是这一条自带的封面；没带封面的那一条铺不出图，会自动退回唱机",
            color = Tokens.InkFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 8.dp)
        )

        FilterHead("本地图片", topPad = 12.dp)
        ImageCell(Skin.artImage.value, "选图片", "未选图", onPickImage, onClearImage)
        Text(
            "选完直接切到「自选图片」那一档；那张图会拷进应用目录，相册里删掉也还在",
            color = Tokens.InkFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 8.dp)
        )

        FilterHead("歌词回位", topPad = 12.dp)
        LyricBackRow()
        Text(
            "歌词那一屏手动滑走之后，过这么多秒才滚回当前播放那一句",
            color = Tokens.InkFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 8.dp, bottom = 12.dp)
        )
    }
}

/**
 * 「歌词回位」那一行四颗：5 / 10 / 15 / 30 秒。
 *
 * 摆在这一屏而不是「我的」→装扮那一屏 —— 这一屏才是音乐那一屏右上角那颗点进来的地方，
 * 回位管的是这一屏第二页的歌词（他 10-16 点的：我第一版读成装扮那一屏了）。
 *
 * 用的是 [FilterChip] 本尊，不另画一份 —— 这一族五项（底色/圆角/内衬/字号/选中变主色）
 * 抄第二份一定会漂。点一颗立刻生效并落盘，和这一屏别处同一条规则。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricBackRow() {
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Dimen.ScreenPad),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Appearance.LYRIC_BACKS.forEach { sec ->
            FilterChip(label = "$sec 秒", on = Skin.lyricBackSec.value == sec) {
                Skin.setLyricBack(context, sec)
            }
        }
    }
}

/** 预览那一格多高：和上面色板那一档不共用，它要装得下一块"能看出是什么"的图 */
private val PreviewH = 200.dp

/**
 * 那一格换完之后的样子，按播放页真实那两层画：图 Crop 铺满 + 一层 [Tokens.ArtScrim]。
 *
 * 唱机档就画现在那块唱机 —— 三档都得能预览，只画两档等于"点了不知道会变成什么"。
 */
@Composable
private fun ArtPreview() {
    val corner = RoundedCornerShape(Tokens.Dimen.CardCorner)
    val mode = Skin.artMode.value
    val art = Tokens.artBitmap
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewH)
            .padding(horizontal = Tokens.Dimen.ScreenPad)
            .clip(corner)
            .background(if (mode == Appearance.ART_VINYL) Tokens.CardBg else Tokens.PlayerBg)
            .border(1.dp, Tokens.Divider, corner)
    ) {
        when {
            mode == Appearance.ART_VINYL -> Turntable(
                cover = Skin.cover.value,
                playing = false,
                modifier = Modifier.fillMaxSize()
            )
            art != null -> {
                Image(
                    bitmap = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Tokens.ArtScrim.copy(alpha = Appearance.ART_SCRIM_ALPHA))
                )
                Text(
                    "这一屏的字压在图上",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                )
            }
            // 海报档但这一条没带封面：把话说在这儿，别让他退出去对着一块唱机猜为什么没换
            else -> Text(
                "这一条没带封面",
                color = Tokens.InkFaint,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
