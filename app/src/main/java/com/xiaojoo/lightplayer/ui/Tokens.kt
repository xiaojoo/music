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
    val Track = Color(0xFFBCBCBC)             // 迷你条、分隔线那一支灰（上一张参考图量来的）
    // 参考图未播段是 7% 白、已播段 22% 白（同一支颜色只变深浅）。我们这边浅色底上白叠不上去，
    // 换成同一支墨的深浅：未播压到 7% 墨，已播 22% 墨（压在 231 底上实测 18.2% 不够，另给一支）。
    val TrackIdle = Color(0xFFD7D7D7)         // 231 底色上 7% 墨 = 215
    val TrackPlayed = Color(0xFFB4B4B4)       // 231 底色上 22% 墨 = 180
    val Divider = Color(0xFFD4D6D8)           // 抽屉里的分行线：Track 往卡片底色混一半，比 Track 淡一档
    val Accent = Color(0xFF06C755)            // 参考图绿色（播放态/开关）
    val FavoriteTileBg = Color(0xFFF1D3D3)    // 选单抽屉里「收藏」那枚格子的粉底：参考图避开白心的两处各采一次，都是 (241,211,211)

    val playerBackground: Brush = Brush.verticalGradient(
        0f to PlayerBg, 0.78f to PlayerBg, 0.86f to WarmTop, 0.94f to WarmMid, 1f to WarmBottom
    )

    object Dimen {
        // 2026-10-12 按新参考图重量：图 810px 宽、轨道 721px，我们内容宽 352.7dp ⇒ 1 图px = 0.4892dp
        val TrackHeight = 4.9.dp              // 轨道厚 10 图px = 4.89dp（原 2dp 是上一张参考图的 6px）
        val ThumbSize = 5.4.dp                // 滑块直径 11 图px = 5.38dp，只比轨道厚 0.5dp；两端圆点已删
        val PlayIcon = 60.5.dp                // 裸三角：画框 60.5dp 时墨迹 30.9x35.6dp = 参考图 63x73 图px
        val PauseIcon = 56.dp                 // 暂停两竖笔的墨迹高 = 0.635·画框，取 56dp 才和 ▶ 的 35.6dp 齐平
        val PlayInkShift = (-1.8).dp          // Play 的归一化坐标把墨迹摆在画框中心右 0.0299 画框宽，裸三角没圆底就露出来，补回去
        val PrevNextIcon = 48.dp              // 快进/快退：画框 48dp 时墨迹 38.2x23.5dp = 参考图 78x48 图px
        val QueueIcon = 15.6.dp               // 队列：墨宽 = 0.887·画框 + 半个笔画、墨高 = 0.7605·画框 + 半个笔画。
                                              // 要最大边 40px = 14.55dp → (14.55−0.735)/0.887 = 15.6dp，短边出 ~34px
        val QueueStroke = 1.47.dp             // 队列的横线改成吃统一笔画参数了，这里把参考图那 3 图px 钉回去
        // 循环那颗要和队列那颗看上去一样大：两颗墨迹外接框同宽同高。
        // 直接放大画框会把笔画按 0.075 系数一起撑粗（旁边队列才 1.47dp，一粗一细），
        // 所以画框配钉死的笔画 1.5dp。→| 那档：墨宽 = 0.60·画框 + 笔画、墨高 = 0.50·画框 + 笔画，
        // 要最大边 40px = 14.55dp → (14.55−1.5)/0.60 = 21.75dp。
        // 注意这一颗按循环模式换图形：→| 60→40 是照这档算的，另两档（循环/单曲）同框不同墨，
        // 改前就比 →| 窄 5px 左右，不跟着这次数。
        val LoopIcon = 21.7.dp
        val LoopStroke = 1.5.dp
        // ── 右栏图标系统：表头 2 颗 + 列表行 3 颗 + 迷你条 3 颗，8 颗一套数。
        // 归一是把仿射烘进 LineIcon 各颗的常数里（不是运行时变换）：目标 = 默认笔画
        // 0.075·画框 下墨宽正好 0.5·画框、墨中心落在画框正中。所以这一栏不能再另给笔画数，
        // 另给就和归一算的那支对不上，墨宽会跟着差出去。
        val ColIcon = 25.dp        // 画框 → 6 颗归一过的图形墨宽都是 12.5dp
        val ColStroke = 1.875.dp   // = 0.075 × ColIcon，归一算的就是这支；栏里 8 颗同一线粗
        val ColBox = 39.5.dp       // 命中盒宽。三盒挨着排不塞 Spacer、一律 Center
                                   // → 相邻两颗的墨间隙 = 39.5 − 12.5 = 27dp，颗颗相同，不用再按每颗墨宽配盒
        // 播放 / 暂停 / 队列这 3 颗没烘进归一：它们同时是播放页按参考图量出来的那三颗
        // （墨 30.9x35.6 / 22.1x35.6 / 22.0x18.6dp），归一会把播放页一起改掉。
        // 迷你条这三颗按"和心同一只 13.74dp 正方框、等比不形变"反解画框（笔画固定 1.875dp），
        // 再各自补掉"这条边的形状只长出 +0.2px 抗锯齿"那一点 —— 心是曲线描边能吃到 +1.2px，
        // 实心三角和直笔只吃 +0.2px，不补的话真机读数 38 vs 39 在 205/215/225/235 四档下都差 1px：
        val ColPlay = 24.0.dp      // 实心三角墨高占画框 0.5882 → 13.74 / 0.5882 = 23.4，补量化到 24.0
        val ColPause = 21.8.dp     // 暂停改成实心胶囊后墨高 = 0.635·画框（不再吃笔画）；真机 22.4→40px，收到 21.8 出 39
        val ColQueue = 14.8.dp     // ▶+三行墨宽 = 0.887·画框 + 右半个笔画 → (13.74 − 0.9375) / 0.887 = 14.4，补到 14.8
        // ▶ 的墨中心在画框 0.5304（右偏 0.0304·画框 = 0.73dp = 2px）：不补的话迷你条上
        // "心↔播放"比"播放↔队列"窄 4px。暂停两笔对称、队列只偏 0.67px，都不用在运行时补。
        // 播放页那颗由 PlayerScreen 自己的 PlayInkShift(-1.8dp) 补同一件事，两边不共用。
        val ColInkShift = (-0.71).dp
        // 三个 surface 的右内衬：盒宽 39.5 里墨只占 12.73，居中后盒右沿自带 13.4dp，
        // 所以 Row 的右内衬要收成 20 − 13.4 = 6.6dp，最右一颗的墨右沿才压在内容右边界上
        // （原来那把数是靠"最右一颗贴盒右沿 + 自带 11px 画框留白"凑出来的，归一后那两样都没了）
        val ColEndPad = 6.6.dp
        // 顶栏右端那两颗要按"墨的右沿"对齐到下方那一行排序那顆的墨右沿（真机 1022px = 离屏幕右 57px）。
        // 画框右沿贴在容器右边界 1025，可两颗图形在画框里都不占满：放大镜的墨只到画框 0.7465
        // （柄在右下，左边留 0.1425）→ 墨右沿落在 1010，差 12px = 4.4dp；
        // ✕ 的画框是居中在 48dp 盒里的，墨右沿只到 974，差 48px —— 先把它改成贴右（补回 41px），
        // 再补 3.0dp 收尾（真机 2.6dp 出 1021、离目标还差 1px）。
        val SearchInkShift = 4.5.dp
        val CloseInkShift = 3.0.dp
        // 三个 surface 的右内衬一律 ScreenPad（迷你条原来收 16dp 是给旧队列画法那 11px 画框留白补的）：
        // 最右一颗的墨右沿因此落在离内容右边界 6.28dp 处，和改前量到的 5.45dp 差 0.8dp
        val IconSize = 18.dp                  // 工具行图标宽 ~54px = 18dp
        // 筛选页的五条上下缝，一律按"时长 → 它那排芯片"这一条为准（真机 35px）。
        // 组标题行给死高 34dp：歌手组右端有折叠箭头（34dp 命中盒），时长组没有，
        // 不锁高的话有箭头那组的标题到芯片会多出 25px（60 vs 35）。
        val FilterHeadH = 34.dp
        val FilterGapTop = 1.dp               // 上一组芯片 → 本组标题：1dp + 标题行里那 32px 空 = 35
        val FilterGapBot = 1.5.dp             // 本组标题 → 本组芯片：基准就是这条
        val FilterGapFirstTop = 0.dp          // 第一组：负 padding 会被 Compose 直接拒（"Padding must be
                                              // non-negative"，一进筛选页就闪退）。顶栏要和外面那几页的标题栏
                                              // 一模一样（48dp 命中盒决定条高），所以这条缝只能 0，不再往上顶
        val FilterBtnH = 38.dp                // 底部两颗按钮：44 → 38（×0.86）
        val FilterBtnR = 12.dp                // 圆角 22 → 12（×0.55），不再是对边衬的胶囊
        val IconHit = 48.dp                   // 视觉 18dp，命中区补到 48dp
        val IconPitch = 69.dp                 // 中心间距 208px = 69dp
        val LyricLineHeight = 47.dp           // 行距 141±2px = 47dp
        val LyricEndBand = 32.dp            // 歌词上下内衬 = 一行白缝 99px（真机量的，见 LyricPage）
        val PlayButton = 64.dp                // 中央播放键的命中区（圆底已删，只剩这块按得着的方）
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
