package com.xiaojoo.lightplayer.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 全部数值来自 2026-10-09 对三张 QQ 音乐参考截图的像素测量（1080x2340，按 dpr 3.0 折算成 dp）
object Tokens {
    // 底色：播放页与列表页是两档不同的灰，实测差 (5,4,6)，别合并成一档
    val PlayerBg = Color(0xFFECF0F3)          // (236,240,243) 上半屏恒定
    val ListBg = Color(0xFFF1F4F9)            // (241,244,249)
    val CardBg = Color(0xFFFAFAFA)            // 唱机外框 (250,250,250)
    val NavBg = Color(0xFFFFFFFF)             // 底栏与迷你条同一档纯白（2026-10-10 他点名要白色）

    // 底部暖渐变：实测 y≈1850 起，到屏幕底
    val WarmTop = Color(0xFFE6E6E6)           // (236,230,230) @y1900
    val WarmMid = Color(0xFFEBC9CC)           // (234,209,204) @y2100
    val WarmBottom = Color(0xFFE9C1A3)        // (233,197,163) @y2300

    val InkStrong = Color(0xFF1A1A1A)         // 当前行歌词 / 标题
    val Ink = Color(0xFF5F6062)               // 参考图非当前行墨色中位 (95,96,98)
    val InkFaint = Color(0xFF9B9EA3)
    val Track = Color(0xFFBCBCBC)             // 进度条：已播 (188,192,195) 与未播 (188,188,188) 实测只差 ΔB=7
    val Divider = Color(0xFFD4D6D8)           // 抽屉里的分行线：Track 往卡片底色混一半，比 Track 淡一档
    val Accent = Color(0xFF06C755)            // 参考图绿色（播放态/开关）

    val playerBackground: Brush = Brush.verticalGradient(
        0f to PlayerBg, 0.78f to PlayerBg, 0.86f to WarmTop, 0.94f to WarmMid, 1f to WarmBottom
    )

    object Dimen {
        // 参考图：进度条 y=1872..1877 → 厚 6px = 2dp
        val TrackHeight = 2.dp
        val ThumbSize = 8.dp                  // 进度条两端点实测约 24px = 8dp
        val IconSize = 18.dp                  // 工具行图标宽 ~54px = 18dp
        val IconHit = 48.dp                   // 视觉 18dp，命中区补到 48dp
        val IconPitch = 69.dp                 // 中心间距 208px = 69dp
        val LyricLineHeight = 47.dp           // 行距 141±2px = 47dp
        val PlayButton = 64.dp                // 中央播放键，参考图圆径 ~190px = 63dp
        val SideIcon = 24.dp

        // 圆角整体收小（2026-10-09 他要"小一点"，取原值的一半，系数 0.5）
        val TurntableCorner = 14.dp           // 原 28
        val CardCorner = 8.dp                 // 原 16（抽屉里的卡片、倍速胶囊）
        val SheetCorner = 12.dp               // 原 material3 默认 28（抽屉顶两角）
        val ThumbCorner = 3.dp                // 原 6（列表封面）
        val ScreenPad = 20.dp
        val NavHeight = 62.dp
    }
}
