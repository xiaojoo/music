package com.xiaojoo.lightplayer.ui

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaojoo.lightplayer.data.Appearance

// 全部数值来自 2026-10-09 对三张 QQ 音乐参考截图的像素测量（1080x2340，按 dpr 3.0 折算成 dp）
object Tokens {
    // 底色：播放页与列表页是两档不同的灰，实测差 (5,4,6)，别合并成一档
    val PlayerBg = Color(0xFFECF0F3)          // (236,240,243) 上半屏恒定
    val ListBg = Color(0xFFF1F4F9)            // (241,244,249)
    private val DefaultCardBg = Color(0xFFFAFAFA)   // 唱机外框 (250,250,250)
    private val DefaultNavBg = Color(0xFFFFFFFF)    // 底栏与迷你条同一档纯白（2026-10-10 他点名要白色）

    // 底部暖渐变：实测 y≈1850 起，到屏幕底
    val WarmTop = Color(0xFFE6E6E6)           // (236,230,230) @y1900
    val WarmMid = Color(0xFFEBC9CC)           // (234,209,204) @y2100
    val WarmBottom = Color(0xFFE9C1A3)        // (233,197,163) @y2300

    val InkStrong = Color(0xFF1A1A1A)         // 当前行歌词 / 标题
    val Ink = Color(0xFF5F6062)               // 参考图非当前行墨色中位 (95,96,98)
    private val DefaultInkFaint = Color(0xFF9B9EA3)
    /**
     * 次要那一档墨：铺了本地图片就往上顶一档（顶到 Ink 那一档）。
     * 真机量过：这张图上「收藏」那两个字 1.93、「装扮」1.78、宫格数字 2.23~2.35，
     * 而默认档（241 灰底）是 2.44 —— 图透上来多少，这一档就掉多少，卡片加白纱也补不回来
     * （把卡片提到 0.75 只从 1.78 到 2.1）。图是他要点开的东西，不能为了字把图压回去，
     * 所以动字不动图。代价：铺图时这一档和正文那一档并成同一支，层次少一级。
     */
    val InkFaint: Color get() = if (Skin.image.value == null) DefaultInkFaint else Ink
    val Track = Color(0xFFBCBCBC)             // 迷你条、分隔线那一支灰（上一张参考图量来的）
    // 参考图未播段是 7% 白、已播段 22% 白（同一支颜色只变深浅）。我们这边浅色底上白叠不上去，
    // 换成同一支墨的深浅：未播压到 7% 墨，已播 22% 墨（压在 231 底上实测 18.2% 不够，另给一支）。
    val TrackIdle = Color(0xFFD7D7D7)         // 231 底色上 7% 墨 = 215
    val TrackPlayed = Color(0xFFB4B4B4)       // 231 底色上 22% 墨 = 180
    val Divider = Color(0xFFD4D6D8)           // 抽屉里的分行线：Track 往卡片底色混一半，比 Track 淡一档
    private val DefaultAccent = Color(0xFF06C755)  // 参考图绿色（播放态/开关）；装扮没换主题色时就是它
    val FavoriteTileBg = Color(0xFFF1D3D3)    // 选单抽屉里「收藏」那枚格子的粉底：参考图避开白心的两处各采一次，都是 (241,211,211)

    // ── 装扮：下面四支是"活"的，读 Skin 那三个档。读发生在组合期间，所以换一颗色板
    //    会把用到它的每一屏各自重画一遍，不需要往子组件往下传色。
    //    默认档（UNSET）逐支回到上面那批实测数，所以没装扮过的界面是 Δ0 不变。
    /** 主题色：选中芯片、播放态、开关、收藏心、底部动作那颗实心键，芯片上是白字 */
    val Accent: Color get() = Skin.accent.value.let { if (it == Appearance.UNSET) DefaultAccent else Color(it) }
    /** 壳层最外面那一层，恒不透明：本地背景图就铺在它上面，各页的根底再压它一层 */
    val SkinBase: Color get() = if (Skin.bg.value == Appearance.UNSET) ListBg else Color(Skin.bg.value)
    /** 各整屏页的根底。铺了图就透明（让壳层那张透出来，只在壳层混合一次，不叠两层洗白） */
    val Backdrop: Color get() = if (Skin.image.value != null) Color.Transparent else SkinBase
    /** 播放页上半屏：默认档是 PlayerBg 不是 ListBg（这两档实测差 (5,4,6)，本来就不许合并） */
    val PlayerTop: Color get() = when {
        Skin.image.value != null -> Color.Transparent
        Skin.bg.value == Appearance.UNSET -> PlayerBg
        else -> Color(Skin.bg.value)
    }
    /** 底栏和迷你条那一档：默认还是他 10-10 点名那档纯白，装扮选了底色就跟着那一档走 */
    val NavBg: Color get() = if (Skin.bg.value == Appearance.UNSET) DefaultNavBg else Color(Skin.bg.value)
    /**
     * 浮在页底上的控件与卡片那一档（那颗搜索胶囊、首页那两张卡）。
     * **不跟装扮的底色走** —— 跟了就和页底同色，那颗胶囊直接化在背景里（10-15 装完第一版就没了）。
     * 默认仍是纯白一格不动；铺了图才和卡片一起半透。
     */
    val Surface: Color get() =
        if (Skin.image.value == null) DefaultNavBg else DefaultNavBg.copy(alpha = Appearance.CARD_ALPHA)
    /** 卡片那一档：只在铺了本地图片时半透（让图从卡片底下透出来），默认档和纯色档仍是实心 */
    val CardBg: Color get() =
        if (Skin.image.value == null) DefaultCardBg else DefaultCardBg.copy(alpha = Appearance.CARD_ALPHA)
    /**
     * 卡片那一档的**实心**版：给"整屏不铺图"的那几屏用。
     * 视频那一屏的地址源设置就是这种 —— 它是盖在视频列表上面的，页底一透就透出后面的片子，
     * 输入框里那行网址压在画面上读不了（2026-10-15 他点名这一屏不要透明）。
     */
    val CardSolid: Color = DefaultCardBg

    // ── 画幅：播放页中间那一格换成海报/自选图之后，那张图铺成整屏背景，字压在图上。
    //    图上面盖一层墨色纱（不透明度见 [Appearance.ART_SCRIM_ALPHA] 那段账），这一屏的三档墨全换成白。
    /** 画幅该铺的那一张：海报档是这首歌自带的封面，自选档是他挑的那张；唱机档或还没到手回 null */
    val artBitmap: ImageBitmap? get() = when (Skin.artMode.value) {
        Appearance.ART_POSTER -> Skin.cover.value
        Appearance.ART_IMAGE -> Skin.artImage.value
        else -> null
    }

    /** 有没有真铺上画幅：图没到手就不算，否则白字会先压在浅色页底上闪一下（那是"看不见字"那一类） */
    val artOn: Boolean get() = artBitmap != null

    /** 压在图上那一层纱：就是正文那支墨，不另挑色 */
    val ArtScrim: Color get() = InkStrong

    /** 图上「标题/当前句/图标」那一档 */
    val ArtInkStrong: Color = Color.White

    /** 图上「歌手行/非当前句」那一档 */
    val ArtInk: Color = Color.White.copy(alpha = Appearance.ART_INK_BODY_ALPHA)

    /** 图上「两端时间/上一句下一句」那一档 */
    val ArtInkFaint: Color = Color.White.copy(alpha = Appearance.ART_INK_FAINT_ALPHA)

    /** 图上进度条那两段：0.28 / 0.85。PlayerArtTest 量的判据是"两段各自对底、以及两段互比，都不低于默认档" */
    val ArtTrackIdle: Color = Color.White.copy(alpha = 0.28f)
    val ArtTrackPlayed: Color = Color.White.copy(alpha = 0.85f)

    /** 歌词页选中那一条的底：白透一层，不透明才压得住底下那张图 */
    val ArtPill: Color = Color.White.copy(alpha = 0.14f)

    /** 播放页那三档墨的取色：画幅开着走白那一套，否则回到实测那三档 */
    fun inkStrong(onArt: Boolean) = if (onArt) ArtInkStrong else InkStrong
    fun ink(onArt: Boolean) = if (onArt) ArtInk else Ink
    fun inkFaint(onArt: Boolean) = if (onArt) ArtInkFaint else InkFaint

    /**
     * 状态栏压在后面那条带**实际**是什么色：没铺图就是壳层底色；铺了图是"图按 IMAGE_ALPHA
     * 压在底色上"混出来的那一次 —— 和屏幕上真画出来的那一步是同一个算式，不是估的。
     * 顶带平均色见 Skin.stripArgb（解图那一趟数出来的）。
     */
    val StatusStrip: Color get() {
        val tint = Skin.topTint.value
        return if (tint == null) SkinBase
        else Color(Appearance.blend(tint, SkinBase.toArgb(), Appearance.IMAGE_ALPHA))
    }

    /** 系统状态栏 / 导航条的图标用深还是用白，只看上面那一条带有多亮 */
    val LightSystemIcons: Boolean get() = Appearance.lightAppearance(StatusStrip.toArgb())

    // 底部暖渐变那三档不动：装扮换的是上半屏，暖色那一条是播放页自己的身份
    fun playerBackground(): Brush = Brush.verticalGradient(
        0f to PlayerTop, 0.78f to PlayerTop, 0.86f to WarmTop, 0.94f to WarmMid, 1f to WarmBottom
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
        // 顶栏右端那颗放大镜的 offset 从 2026-10-22 起跟搜索框那一行的 ✕ 共线（下面 searchInkShift）。
        // 它原来是钉在下方那一行排序那顆的墨右沿上（真机 1022px = 离屏幕右 57px，放大镜的墨只到画框 0.7465
        // → 补 4.5dp 收尾）。现在放大镜挪到 79px 那一档，**排序那一行没跟着挪，还停在 57px** —— 这一屏
        // 右缘因此差 22px，要一起挪说一句（挪的是那行的右内衬，动的是另一屏的东西，没自己动）。
        // 三个 surface 的右内衬一律 ScreenPad（迷你条原来收 16dp 是给旧队列画法那 11px 画框留白补的）：
        // 最右一颗的墨右沿因此落在离内容右边界 6.28dp 处，和改前量到的 5.45dp 差 0.8dp
        val IconSize = 18.dp                  // 工具行图标宽 ~54px = 18dp
        // 筛选页的五条上下缝，一律按"时长 → 它那排芯片"这一条为准（真机 35px）。
        // 组标题行给死高 34dp：歌手组右端有折叠箭头（34dp 命中盒），时长组没有，
        // 不锁高的话有箭头那组的标题到芯片会多出 25px（60 vs 35）。
        val FilterHeadH = 34.dp
        val FilterGapTop = 1.dp               // 上一组芯片 → 本组标题：1dp + 标题行里那 32px 空 = 35
        val FilterGapBot = 1.5.dp             // 本组标题 → 本组芯片：基准就是这条
        val FilterGapTail = 12.73.dp          // 末排芯片 → 屏底（真机 35px ÷ 2.75），和"标题 → 芯片"同宽
        val FilterGapFirstTop = 0.dp          // 第一组：负 padding 会被 Compose 直接拒（"Padding must be
                                              // non-negative"，一进筛选页就闪退）。顶栏要和外面那几页的标题栏
                                              // 一模一样（48dp 命中盒决定条高），所以这条缝只能 0，不再往上顶
        val FilterBtnH = 38.dp                // 底部两颗按钮：44 → 38（×0.86）
        val FilterBtnR = 12.dp                // 圆角 22 → 12（×0.55），不再是对边衬的胶囊
        val IconHit = 48.dp                   // 视觉 18dp，命中区补到 48dp
        // 播放页右上角那两颗（画幅 / 齿轮）的命中盒**宽**。高仍是 IconHit。
        // 48 见方的盒里那颗 30px 的墨两边各空 51px，两颗挨着排就是 94px = 34.2dp 的空；
        // 间隙 = 盒宽 − 墨宽（ColBox 那一族同一条算式）→ 28dp − 10.91dp = 17.1dp，正好是原来的一半。
        val ArtIconBox = 28.dp
        /** 收完盒之后把整对往回挪这么多，齿轮的墨右沿才仍停在原来的 x=974 */
        val ArtIconEndPad = 10.dp
        val IconPitch = 69.dp                 // 中心间距 208px = 69dp
        val LyricLineHeight = 47.dp           // 行距 141±2px = 47dp
        val LyricEndBand = 32.dp            // 歌词上下内衬 = 一行白缝 99px（真机量的，见 LyricPage）
        // 封面页最上面那道缝。原来是 `weight(0.34f)`，在 10-15 那版布局里量到 17.33dp（=47.7px）——
        // 改成定值只有一个理由：滚动歌词那一格变高（3 行 → 5 行）的时候，它和歌手行下面那只盒子
        // 是按权重分同一截余量的，权重不动的话封面会跟着往上/下挪十几 dp。
        // 定值把封面钉死，多出来/少掉的那一截全落到歌手行下面的盒子里去。
        // 代价：换到更高/更矮的屏上这一道不再跟着屏高缩放（这台机是唯一参考机，见 README）。
        val CoverTopGap = 17.3.dp
        // 进度条上面那五行滚动歌词：一格 22dp、五行 110dp。
        // 22dp 那一格吃的是当前句 18sp：CJK 一行盒 ≈ 20.7dp，留 1.3dp 呼吸，不再高。
        val LyricStripSlot = 22.dp
        val PlayButton = 64.dp                // 中央播放键的命中区（圆底已删，只剩这块按得着的方）
        val SideIcon = 24.dp

        // 圆角整体收小（2026-10-09 他要"小一点"，取原值的一半，系数 0.5）
        val TurntableCorner = 14.dp           // 原 28
        val CardCorner = 8.dp                 // 原 16（抽屉里的卡片、倍速胶囊）
        val SheetCorner = 12.dp               // 原 material3 默认 28（抽屉顶两角）
        val ThumbCorner = 3.dp                // 原 6（列表封面）
        val ScreenPad = 20.dp

        /* 搜索框那一行（五屏共用：列表页搜索态、本地整屏搜索、接口整屏搜索、相关视频、搜歌手）的三笔间隙，
           一律从 pathData 反解，别再抄字面量 —— 改前五处是各抄各的，抄出两种结果：三处 ✕ 离屏幕右 20.85dp、
           两处（相关视频、搜歌手）压根没补 offset，落在 38.35 / 33.35dp。
             · 返回箭头：22dp 画框，中心线 x 0.22..0.74，两端圆头各伸出半个笔画（0.0375 画框）
               → 墨占画框 0.1825..0.7775。
             · ✕：19dp 画框，中心线 x 0.24..0.76 → 墨右沿离画框右沿 0.2025 画框 = 3.8475dp。
             · 那颗的命中盒 48dp：CenterEnd 画的时候盒心比墨心偏左 (48−19)/2 = 14.5dp，所以整盒先往右补这
               14.5、字形再往左回同样多 —— 墨不动，只有点击带跟着墨走（10-14 他点名的那条判据）。
             · pad 五屏都是 20（ScreenPad）—— 这几个函数从来没有调用方给过 15。歌手那两屏踩在 15 网格上的是
               详情页的名字与行内衬（2026-10-27 ae45fc7 并到 ScreenPad），不是搜索框这一行。
           算式锁在 JVM 测试 SearchBarGapTest 里。 */

        /** 屏幕左沿 → 返回箭头墨左沿。这一笔是这一行的参照缝（pad=20 时 24.015dp = 真机 66px） */
        fun searchBackGap(pad: Dp): Dp = pad + 22.dp * 0.1825f

        /** 输入文字起点（相对容器左边界）：让「箭头墨右沿 → 文字」和 searchBackGap 同宽。改前这里是 IconHit=48，那笔是 30.895 */
        fun searchTextStart(pad: Dp): Dp = 22.dp * 0.7775f + searchBackGap(pad)

        /** ✕ 的墨右沿离屏幕右：他要"右边间隙大一点"、没给数 → 参照缝 ×1.2（×1.0 = 和左缝同宽，那是下一档） */
        fun searchCloseGap(pad: Dp): Dp = searchBackGap(pad) * 1.2f

        /** ✕ 那颗 48dp 命中盒的 offset：用的地方写成 offset(x = 这一笔 + hitSkew)、字形 offset(x = −hitSkew) */
        fun searchCloseShift(pad: Dp): Dp = pad - searchCloseGap(pad) + 19.dp * 0.2025f

        /**
         * 未搜索态那颗放大镜的 offset：让它的墨右沿和搜索态的 ✕ 落在同一条线（searchCloseGap）上，
         * 不然列表页每进出一次搜索，右端那颗就横跳一笔（真机量过：57px vs 79px = 跳 22px）。
         * 放大镜的墨只到画框 0.7465（柄在右下、左边留 0.1425）→ 右下角留白 = 0.2535·画框。
         * icon 要传那颗自己的画框：列表页 22dp、歌手页 21dp，两档的留白不同。
         */
        fun searchInkShift(pad: Dp, icon: Dp): Dp = pad - searchCloseGap(pad) + icon * 0.2535f
        val NavHeight = 62.dp
    }
}
