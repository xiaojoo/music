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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xiaojoo.lightplayer.videosource.MusicVideo

/**
 * 「听歌识曲」那一整屏（首页那颗点进来）。
 *
 * 一条链四步：**收声 → 问模型要词 → 拿词去曲库里对 → 摆成能播能下的行**。
 * 界面按这四步的进度说话，不写"识别中"那种一句话盖住三件事的文案 —— 卡在哪一步是不一样的账。
 *
 * 为什么中间那步是"要词"而不是"问它这是什么歌"：模型报歌名靠训练记忆，**会编、不带置信度**，
 * 错了这边量不出来。对曲库那一步有真条数、真封面、点下去能播能下，那才是可验的结果。
 * 它自己猜的那个名照实摆，但标成"未核对"，和对到的不混排。
 *
 * 麦克风那一截的硬限制写在按钮下面，不藏：对面戴耳机或用听筒时这一路**收不到**。
 */
@Composable
fun RecognizePage(
    /** 走到哪一步了，界面上那一行灰字。null = 还没跑过 */
    stage: String?,
    busy: Boolean,
    error: String?,
    /** 录完那一段的量到的数：几秒 · 几字节 · 音量。全零要能当场看出来 */
    clipStats: String?,
    /** 模型转写出来的词 */
    lyrics: String?,
    /** 它自己猜的名（未核对） */
    guess: String?,
    /** 拿词对到的那几条 */
    hits: List<MusicVideo>,
    playingId: String?,
    downloadingKey: String?,
    sourceLabel: (String) -> String?,
    micGranted: Boolean,
    onRun: () -> Unit,
    onAskMic: () -> Unit,
    onPlay: (MusicVideo) -> Unit,
    onQueue: (MusicVideo) -> Unit,
    onDownload: (MusicVideo) -> Unit,
    baseUrl: String,
    onBaseUrl: (String) -> Unit,
    model: String,
    onModel: (String) -> Unit,
    key: String,
    onKey: (String) -> Unit,
    onSaveSettings: () -> Unit,
    onBack: () -> Unit,
    /** 迷你条没露出来的时候，导航条那一段由这一屏自己留（和别的子页同一个开关） */
    bottomInset: Boolean,
) {
    val settingsOk = baseUrl.isNotBlank() && model.isNotBlank() && key.isNotBlank()
    /* 权限没给时这颗**照样按得动**：按下去就是去要权限。
       原来写成"没权限就置灰"，结果下面那句提示叫他去按一颗按不动的键 —— 死指令。
       实心绿才代表"按下去真会开始跑"，所以实心还要再叠一层 micGranted。 */
    val canTap = !busy && settingsOk
    val solid = canTap && micGranted

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
                "听歌识曲",
                color = Tokens.InkStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Hairline()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Tokens.Dimen.ScreenPad)
                .padding(bottom = 20.dp)
        ) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Tokens.Dimen.FilterBtnH)
                    .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                    .background(if (solid) Tokens.Accent else Tokens.TrackIdle)
                    .tap(enabled = canTap) { if (micGranted) onRun() else onAskMic() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (busy) stage ?: "处理中" else "开始识别",
                    color = if (solid) Color.White else Tokens.InkFaint,
                    fontSize = 15.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            // 没给权限时这一行就是那颗按钮的说明书：按它先去要权限，不用另找入口
            Text(
                when {
                    !settingsOk -> "下面那三格还没填完，填完才按得动"
                    !micGranted -> "按上面那颗，允许麦克风"
                    else -> "把手机外放对着声源；对面戴耳机或用听筒时这一路收不到"
                },
                color = Tokens.InkFaint, fontSize = 12.sp,
            )
            if (error != null) {
                Text(error, color = FailRed, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (clipStats != null) {
                Text("录到 $clipStats", color = Tokens.InkFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            if (guess != null) {
                Spacer(Modifier.height(14.dp))
                Text("模型猜的（未核对）", color = Tokens.Ink, fontSize = 13.sp)
                Text(guess, color = Tokens.InkStrong, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
            if (lyrics != null) {
                Spacer(Modifier.height(14.dp))
                Text("转写到的词", color = Tokens.Ink, fontSize = 13.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                        .background(Tokens.CardBg)
                        .padding(12.dp)
                ) {
                    Text(lyrics, color = Tokens.InkStrong, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (hits.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Text("曲库里对到的 ${hits.size} 条", color = Tokens.Ink, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                hits.forEachIndexed { i, v ->
                    if (i > 0) Hairline()
                    MusicRow(
                        video = v,
                        playing = "online:" + v.key == playingId,
                        onClick = { onPlay(v) },
                        onQueue = { onQueue(v) },
                        onDownload = { onDownload(v) },
                        downloading = downloadingKey == v.key,
                        sub = listOfNotNull(
                            v.artist.takeIf { it.isNotBlank() },
                            sourceLabel(v.sourceId),
                        ).joinToString(" · ").ifBlank { null },
                    )
                }
            }

            Spacer(Modifier.height(26.dp))
            Text("模型设置", color = Tokens.Ink, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            SettingField("接口地址（OpenAI 兼容，形如 https://…/compatible-mode/v1）", baseUrl, onBaseUrl, KeyboardType.Uri)
            Spacer(Modifier.height(10.dp))
            SettingField("模型名（能收音频的那一个）", model, onModel)
            Spacer(Modifier.height(10.dp))
            SettingField("密钥", key, onKey, hide = true)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .height(Tokens.Dimen.FilterBtnH)
                        .clip(RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .background(Tokens.CardBg)
                        .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.FilterBtnR))
                        .padding(horizontal = 18.dp)
                        .tap(onClick = onSaveSettings),
                    contentAlignment = Alignment.Center,
                ) { Text("保存", color = Tokens.InkStrong, fontSize = 14.sp) }
                Spacer(Modifier.width(12.dp))
                Text("三格都存本机，不进仓库", color = Tokens.InkFaint, fontSize = 12.sp)
            }
        }
    }
}

/** 一格设置：自绘输入框（原生控件一律不用），标签就压在卡片里当占位字 */
@Composable
private fun SettingField(label: String, value: String, onValue: (String) -> Unit, keyboard: KeyboardType = KeyboardType.Text, hide: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
            .background(Tokens.CardSolid)
            .border(1.dp, Tokens.Divider, RoundedCornerShape(Tokens.Dimen.CardCorner))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(label, color = Tokens.InkFaint, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            cursorBrush = SolidColor(Tokens.Accent),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Tokens.InkStrong),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
