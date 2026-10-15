package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.data.Appearance

/**
 * 「我的」卡片右端那颗「装扮」点进来的整屏页。三组：主题色、背景色、本地图片。
 *
 * 顶栏、组标题、内衬全部复用 [MusicSourcePage] / [LocalSearchPage] 那一套：
 * 48dp 顶栏 + [Hairline] + [FilterHead] + ScreenPad 20dp，不另起一套数。
 *
 * **点一颗就立刻生效并落盘**，没有「确定 / 取消」：这一屏改的是看得见的东西，
 * 按下去下一帧就是结果，比先选后看再确认少一趟。
 *
 * 色板每颗的对比度在 AppearanceTest 里量过：主题色压白字不低于今天那支绿，
 * 背景色压三档墨色不低于今天的默认档，所以这里挑不出"选了就读不清字"的那颗。
 */
@Composable
fun AppearancePage(
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
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
                "装扮",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()

        FilterHead("主题色", topPad = 12.dp)
        SwatchRow(
            Appearance.ACCENTS.map { it.first to Color(it.second) },
            selectedAt = { Skin.accentNow() == Appearance.ACCENTS[it].second },
        ) { Skin.setAccent(context, Appearance.ACCENTS[it].second) }

        FilterHead("背景", topPad = 12.dp)
        SwatchRow(
            // 「默认」那颗预览的是列表页那档灰，不是"没有颜色"：点它回到各页自己的实测档
            Appearance.BACKGROUNDS.map {
                it.first to if (it.second == Appearance.UNSET) Tokens.ListBg else Color(it.second)
            },
            selectedAt = { Skin.bg.value == Appearance.BACKGROUNDS[it].second },
        ) { Skin.setBg(context, Appearance.BACKGROUNDS[it].second) }

        FilterHead("本地图片", topPad = 12.dp)
        ImageCell(Skin.image.value, "选图片", "未铺图", onPickImage, onClearImage)
        Text(
            "整屏铺图，压淡一档让字读得清",
            color = Tokens.InkFaint,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = Tokens.Dimen.ScreenPad, top = 8.dp, bottom = 12.dp)
        )
    }
}

/**
 * 一排色板：每颗摊满等宽一份（7 颗在 360dp 宽的机子上也不溢出），缝由格子自己吃，不留死间隙。
 * 命中区就是这一整格（宽 ~46dp、高 32+5+字 = 56dp），色面只有 26dp 但按哪儿都算这一颗。
 */
@Composable
private fun SwatchRow(
    items: List<Pair<String, Color>>,
    selectedAt: (Int) -> Boolean,
    onPick: (Int) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Tokens.Dimen.ScreenPad)) {
        items.forEachIndexed { i, (label, color) ->
            val on = selectedAt(i)
            Column(
                modifier = Modifier.weight(1f).tap { onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Swatch(color, on)
                Text(
                    label,
                    color = if (on) Tokens.InkStrong else Tokens.InkFaint,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 5.dp)
                )
            }
        }
    }
}

/**
 * 一颗色板：外面 32dp 是环位，里面 26dp 才是那块色。
 * 选中只往外加一圈环、不动色面尺寸 —— 用描边表示选中的话那 2dp 会从色面上吃掉，
 * 选中前后看到的就不是同一块色了。
 */
@Composable
private fun Swatch(color: Color, selected: Boolean) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .then(if (selected) Modifier.border(2.dp, Tokens.InkStrong, CircleShape) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, Tokens.Divider, CircleShape)
        )
    }
}

/**
 * 本地图片那一格：左边 88dp 方格既是图也是入口，右边那颗「清除」没图时置灰不消失。
 *
 * 装扮页和「播放画幅」那一屏共用这一份 —— 同一族五项（方格尺寸/圆角/描边/那颗清除的置灰态）
 * 抄第二份一定漂，而这两格在同一台机上会上下挨着看到。
 */
@Composable
internal fun ImageCell(
    img: ImageBitmap?,
    emptyLabel: String,
    offLabel: String,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
) {
    val corner = RoundedCornerShape(Tokens.Dimen.CardCorner)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Tokens.Dimen.ScreenPad),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(corner)
                .background(if (img == null) Tokens.CardBg else Tokens.SkinBase)
                .border(1.dp, Tokens.Divider, corner)
                .tap(onClick = onPickImage),
            contentAlignment = Alignment.Center
        ) {
            if (img == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LineIcon(IconKind.Add, size = 26.dp, color = Tokens.InkFaint)
                    Text(
                        emptyLabel,
                        color = Tokens.InkFaint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            } else {
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Box(
            modifier = Modifier
                .padding(start = 14.dp)
                .size(width = 56.dp, height = 48.dp)
                .tap(enabled = img != null, onClick = onClearImage),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (img == null) offLabel else "清除",
                color = if (img == null) Tokens.InkFaint.copy(alpha = 0.45f) else Tokens.InkStrong,
                fontSize = 14.sp
            )
        }
    }
}
