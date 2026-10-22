package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

// 参考图里的图标是细线描边风格，用 Canvas 画比引一套 icon 字体更可控（宽度、圆头都能定）
// stroke：同一颗图形放到不同大的画框时，笔画不能跟着画框等比放大（34dp 框配 0.075 系数就是
// 2.6dp 粗线，和旁边 1.5dp 的队列那颗摆一起就是一粗一细），要给固定笔画就传进来。
@Composable
fun LineIcon(
    kind: IconKind,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Dimen.IconSize,
    color: Color = Tokens.Ink,
    stroke: Dp? = null
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val st = stroke?.toPx() ?: s * 0.075f
        when (kind) {
            IconKind.Play -> {
                /* 三个尖各倒一个圆角：每个尖沿两条邻边让进 d，用原顶点当控制点画二次弧，
                   直边还是那三条直线，只有尖被收圆。
                   圆角必然吃掉尖，所以先把三角形按内心放大 u = 1 + d 再收 —— 系数 1.0 是按
                   真机量回来的：1.28 会把墨撑到 25×30（原来 23×28），1.0 落在 24×27，看不出差。 */
                val d = 0.15f
                val u = 1f + d
                val ix = 0.4698f
                fun ax(x: Float) = s * (ix + (x - ix) * u)
                fun ay(y: Float) = s * (0.50f + (y - 0.50f) * u)
                val ux = 0.8575f * d
                val uy = 0.5145f * d
                val p = Path().apply {
                    moveTo(ax(0.30f) + s * ux, ay(0.20f) + s * uy)
                    lineTo(ax(0.80f) - s * ux, ay(0.50f) - s * uy)
                    quadraticTo(ax(0.80f), ay(0.50f), ax(0.80f) - s * ux, ay(0.50f) + s * uy)
                    lineTo(ax(0.30f) + s * ux, ay(0.80f) - s * uy)
                    quadraticTo(ax(0.30f), ay(0.80f), ax(0.30f), ay(0.80f) - s * d)
                    lineTo(ax(0.30f), ay(0.20f) + s * d)
                    quadraticTo(ax(0.30f), ay(0.20f), ax(0.30f) + s * ux, ay(0.20f) + s * uy)
                    close()
                }
                drawPath(p, color)
            }

            /* 暂停：两根胶囊。比例按他给的参考图量：单笔宽/高 = 25/75 = 0.333、缝/宽 = 10/25 = 0.4。
               墨高仍锁 0.635·画框（= 播放页 ▶ 的墨高，真机 98px）：换成实心胶囊后两端各长出 w/2，
               所以直段从原来的 0.56 收到 0.4234，两半圆各 0.1058 → 0.1825..0.8175，总高不变。
               原来那是两条 0.075·画框 的细线（宽/高只有 0.118、缝/宽 3.3），比参考图细一大截。 */
            IconKind.Pause -> {
                val w = s * 0.2116f
                val r = CornerRadius(w / 2f)
                drawRoundRect(color, Offset(s * 0.35185f - w / 2f, s * 0.1825f),
                    androidx.compose.ui.geometry.Size(w, s * 0.635f), r)
                drawRoundRect(color, Offset(s * 0.64815f - w / 2f, s * 0.1825f),
                    androidx.compose.ui.geometry.Size(w, s * 0.635f), r)
            }

            /* 快进 / 快退：两枚同向实心三角首尾相接。整颗墨迹 38.2x23.5dp、单枚 18.6x23.5dp、
               两枚之间那道缝 1.0dp，圆角 2.4dp（= 参考图尖上被削掉的那 5 图px）。
               顶点坐标比墨迹外沿外扩：尖那头让 1.48dp、底角各让 1.61dp，
               这是 roundedTri 反解出来的数，不是拍的。 */
            IconKind.Next -> {
                val yt = s * 0.22156f
                val yb = s * 0.77844f
                val my = s * 0.5f
                val r = s * 0.05f
                roundedTri(Offset(s * 0.10229f, yt), Offset(s * 0.10229f, yb), Offset(s * 0.52063f, my), r, color)
                roundedTri(Offset(s * 0.51021f, yt), Offset(s * 0.51021f, yb), Offset(s * 0.92854f, my), r, color)
            }

            IconKind.Prev -> {
                val yt = s * 0.22156f
                val yb = s * 0.77844f
                val my = s * 0.5f
                val r = s * 0.05f
                roundedTri(Offset(s * 0.48979f, yt), Offset(s * 0.48979f, yb), Offset(s * 0.07146f, my), r, color)
                roundedTri(Offset(s * 0.89771f, yt), Offset(s * 0.89771f, yb), Offset(s * 0.47937f, my), r, color)
            }

            /* 播放模式那三颗（顺序播放 / 列表循环 / 单曲循环）10-16 按主流播放器那一家重画：
               原来列表循环画的是一圈 300° 弧加一记箭头，和「刷新」同形，摆在一排里认不出是循环。
               墨框自己居中：两档画框（18dp 默认笔画 / 21.7dp 固定 1.5dp 笔画）上量到中心偏移 0.0px。 */
            // 顺序播放：一支往右的箭头顶到一条竖线 —— 播到末尾就停，不带圈
            IconKind.OrderSeq -> {
                drawLine(color, Offset(s * 0.18f, s * 0.50f), Offset(s * 0.56f, s * 0.50f), st, StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(s * 0.56f, s * 0.28f)
                        lineTo(s * 0.74f, s * 0.50f)
                        lineTo(s * 0.56f, s * 0.72f)
                        close()
                    },
                    color,
                )
                drawLine(color, Offset(s * 0.80f, s * 0.20f), Offset(s * 0.80f, s * 0.80f), st, StrokeCap.Round)
            }

            IconKind.Repeat -> repeatLoop(color, st)

            // 单曲循环：同一对钩，中间立一个「1」—— 笔画收到 0.72 成，免得和上下两条横边糊在一起
            IconKind.LoopOne -> {
                repeatLoop(color, st)
                val ds = st * 0.72f
                drawLine(color, Offset(s * 0.50f, s * 0.38f), Offset(s * 0.50f, s * 0.62f), ds, StrokeCap.Round)
                drawLine(color, Offset(s * 0.435f, s * 0.445f), Offset(s * 0.50f, s * 0.38f), ds, StrokeCap.Round)
            }

            /* 播放队列：一枚小 ▶ 顶着一行，下面再两行（原来是三条横线加一个音符，按参考图换掉）。
               画布 24dp 时墨迹 22.0x18.6dp = 参考图 45x38 图px；笔画 3 图px = 1.47dp、
               行距 15 图px = 7.34dp，第一行从 ▶ 墨右沿空 2.45dp 起。
               小三角的顶点按"圆角削完之后墨迹 5.4x6.4dp"外扩过，不是照抄外接框。 */
            IconKind.Queue -> {
                drawLine(color, Offset(s * 0.3777f, s * 0.2451f), Offset(s * 0.9282f, s * 0.2451f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.1126f, s * 0.5510f), Offset(s * 0.9282f, s * 0.5510f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.1126f, s * 0.8568f), Offset(s * 0.9282f, s * 0.8568f), st, StrokeCap.Round)
                roundedTri(
                    Offset(s * 0.0412f, s * 0.09635f), Offset(s * 0.0412f, s * 0.39385f),
                    Offset(s * 0.2733f, s * 0.2451f), s * 0.04f, color
                )
            }

            IconKind.ChevronDown -> {
                drawLine(color, Offset(s * 0.26f, s * 0.40f), Offset(s * 0.50f, s * 0.64f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.74f, s * 0.40f), st, StrokeCap.Round)
            }

            IconKind.ChevronRight -> {
                drawLine(color, Offset(s * 0.40f, s * 0.26f), Offset(s * 0.64f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.50f), Offset(s * 0.40f, s * 0.74f), st, StrokeCap.Round)
            }

            IconKind.Favorite -> drawPath(heartPath(s, 0.87169f, 0.06416f, 1.09921f), color)

            /* 没收藏时同一颗心只描边（列表行上那颗要分得出"已收藏/未收藏"，
               只靠颜色分不出：描边和实心在 22dp 上是两种形状）。
               u 是把整颗心等比放进 13.74dp 正方框的系数，两态不同是因为描边那态要额外吃掉一支笔画。 */
            IconKind.FavoriteLine -> drawPath(
                heartPath(s, 0.74093f, 0.12954f, 1.11670f), color,
                style = Stroke(width = st, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            )

            /* 加入播放列表：竖笔 + 从笔顶斜拉到右边一个尖 + 下面一枚加号（不是闭合三角，放大参考图数出来的形状）。
               坐标按"墨迹外沿"反算过圆头：参考图墨 x 146..176 / y 51..83，盒子 60px、笔画 5px。 */
            IconKind.PlayAdd -> {
                drawLine(color, Offset(s * 0.283f, s * 0.267f), Offset(s * 0.283f, s * 0.717f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.283f, s * 0.267f), Offset(s * 0.708f, s * 0.500f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.517f, s * 0.567f), Offset(s * 0.517f, s * 0.700f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.442f, s * 0.633f), Offset(s * 0.592f, s * 0.633f), st, StrokeCap.Round)
            }

            /* 加到歌单：圆角方框里一枚加号。参考图那颗量到墨迹 52x52、笔画 4px、
               顶边平的一段只有 18px 宽 → 圆角半径 (52-18)/2=17px，折算到 22dp 画框就是这套比例 */
            IconKind.SquareAdd -> {
                val m = s * 0.10f
                drawRoundRect(
                    color,
                    topLeft = Offset(m, m),
                    size = androidx.compose.ui.geometry.Size(s - m * 2f, s - m * 2f),
                    cornerRadius = CornerRadius(s * 0.283f),
                    style = Stroke(width = s * 0.067f)
                )
                drawLine(color, Offset(s * 0.50f, s * 0.35f), Offset(s * 0.50f, s * 0.65f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.35f, s * 0.50f), Offset(s * 0.65f, s * 0.50f), st, StrokeCap.Round)
            }

            /* 分享：开口朝右上的一段圆 + 一支指向右上的箭头（参考图那颗墨迹 50x51）。
               弧从 3 点钟起顺时针扫 280°，缺口正好落在 12 点到 3 点那一段。 */
            IconKind.Share -> {
                drawArc(
                    color, startAngle = 0f, sweepAngle = 280f, useCenter = false,
                    topLeft = Offset(s * 0.14f, s * 0.18f),
                    size = androidx.compose.ui.geometry.Size(s * 0.62f, s * 0.62f),
                    style = Stroke(width = st, cap = StrokeCap.Round)
                )
                drawLine(color, Offset(s * 0.52f, s * 0.48f), Offset(s * 0.82f, s * 0.18f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.58f, s * 0.18f), Offset(s * 0.82f, s * 0.18f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.82f, s * 0.18f), Offset(s * 0.82f, s * 0.42f), st, StrokeCap.Round)
            }

            /* 置顶：上面一道横杠，下面一支朝上的空心箭头。
               参考图量到整颗 39x47（横杠 0.175..0.825 宽、箭头两翼 0.24..0.76、杆到 0.89） */
            IconKind.PinTop -> {
                drawLine(color, Offset(s * 0.175f, s * 0.14f), Offset(s * 0.825f, s * 0.14f), s * 0.085f, StrokeCap.Round)
                val p = Path().apply {
                    moveTo(s * 0.50f, s * 0.245f)
                    lineTo(s * 0.76f, s * 0.51f)
                    lineTo(s * 0.61f, s * 0.51f)
                    lineTo(s * 0.61f, s * 0.865f)
                    quadraticTo(s * 0.61f, s * 0.89f, s * 0.585f, s * 0.89f)
                    lineTo(s * 0.415f, s * 0.89f)
                    quadraticTo(s * 0.39f, s * 0.89f, s * 0.39f, s * 0.865f)
                    lineTo(s * 0.39f, s * 0.51f)
                    lineTo(s * 0.24f, s * 0.51f)
                    close()
                }
                drawPath(p, color, style = Stroke(width = s * 0.058f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }

            /* 删除：垃圾桶。✕ 在我们这儿已经是"清输入框"那颗，拿它当删除会读成"关掉" */
            IconKind.Trash -> {
                drawLine(color, Offset(s * 0.20f, s * 0.26f), Offset(s * 0.80f, s * 0.26f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.26f), Offset(s * 0.38f, s * 0.16f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.16f), Offset(s * 0.62f, s * 0.16f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.62f, s * 0.16f), Offset(s * 0.62f, s * 0.26f), st, StrokeCap.Round)
                val body = Path().apply {
                    moveTo(s * 0.28f, s * 0.34f)
                    lineTo(s * 0.33f, s * 0.84f)
                    lineTo(s * 0.67f, s * 0.84f)
                    lineTo(s * 0.72f, s * 0.34f)
                }
                drawPath(body, color, style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(color, Offset(s * 0.43f, s * 0.46f), Offset(s * 0.45f, s * 0.72f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.57f, s * 0.46f), Offset(s * 0.55f, s * 0.72f), st, StrokeCap.Round)
            }

            /* 加入播放列表：闭合的右指三角 + 一枚正方加号放在它右下角，两块墨互不相碰。
               尺寸按"和筛选同一只 13.74dp 正方框、各自等比不形变"反解：框 = 墨 x/y 0.22524..0.77476。
               三角中心线 0.38 × 0.39444（h/w = 1.038，原素材那颗的比例），加号臂 0.08 = 2.0dp 正方、
               心 (0.65726, 0.65726)，右沿和下沿正好贴到框边。
               为什么不再让加号压在三角底边上（上一版那样）：三角恢复原比例后，底边到加号顶圆头
               只有 0.0435 画框 = 1.2px，比一支笔画（1.875dp = 5.2px）还近 —— 要留 2px 缝就得把
               30.9px 长的底边切掉 19.5px，三角看着是断的。挪开之后 8 倍超采样量到两块墨
               最近 3.25px、重叠像素 0。 */
            /* 加入播放列表：一枚三角 + 右下角一个加号。三角现在是**真等边** ——
               原来那条竖边 0.39444、顶点横距 0.38，两斜边算出来 0.4281，是个腰比底长的等腰三角
               （他 10-15 点的"三角不是等边"）。等边的横高是 a·√3/2 = 0.39444×0.866025 = 0.341606，
               所以顶点从 0.64274 收到 0.604346。
               补的是横、没动竖：竖边（底）一格不缩，加号那颗也一格没动，所以整颗的墨外接框
               还是 0.26274..0.73726 那只正方框（宽=高=0.4745），和这一族"同一只框、等比不形变"不打架。 */
            IconKind.PlayQueue -> {
                /* 三角 + 右下那枚加号。加号自己吃 0.72 成笔画（和 LoopOne 里那个「1」同一条道理）。
                   改前：加号和三角一样粗（1.875dp = 真机 5.2px）、臂只到画框 0.08（整条 11px）⇒ 四个象限
                   各剩 2.9px 空，读不出是加号；而且它离三角的尖顶净缝只有 3.8px，比一支笔画还窄，两坨糊在一起
                   （他 10-22 说这一颗"有缺陷"）。
                   改后：三角收到画框 0.33 高、加号臂 0.105、笔画 0.72 成 ⇒ 净缝 5.5px（≥ 一支笔画）、
                   象限空 5.4px。墨外接框还是 0.22524..0.77476 那一只 13.74dp 正方框，家族口径没动。 */
                val ds = st * 0.72f
                drawPath(
                    Path().apply {
                        moveTo(s * 0.26274f, s * 0.26274f)
                        lineTo(s * 0.26274f, s * 0.59274f)
                        lineTo(s * 0.54853f, s * 0.42774f)
                        close()
                    }, color,
                    style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawLine(color, Offset(s * 0.64276f, s * 0.53776f), Offset(s * 0.64276f, s * 0.74776f), ds, StrokeCap.Round)
                drawLine(color, Offset(s * 0.53776f, s * 0.64276f), Offset(s * 0.74776f, s * 0.64276f), ds, StrokeCap.Round)
            }

            /* 三点（更多）：竖排三枚实心点，等距压在画框中线。没进 InkFit 归一 ——
               它是这一族里唯一"三枚点"的形状，墨宽只占画框 0.11，归一会把它撑成一颗饼。 */
            IconKind.More -> {
                drawCircle(color, radius = s * 0.055f, center = Offset(s * 0.50f, s * 0.28f))
                drawCircle(color, radius = s * 0.055f, center = Offset(s * 0.50f, s * 0.50f))
                drawCircle(color, radius = s * 0.055f, center = Offset(s * 0.50f, s * 0.72f))
            }
            /* 设置：一只八齿齿轮 + 一枚轮毂圈。按"墨宽 0.5 画框"那档反解 —— 齿顶半径 0.2125、
               齿根 0.145、轮毂 0.078，笔画 0.075 一支：8 倍超采样离屏量到墨 10.91 x 10.82dp
               （22dp 画框的目标是 11.00），中心 0.4990/0.4990；齿比谷凸出 1.45dp = 真机 4.0px，
               这一档笔画吃得住。为什么不用"三横带圆点"那支：它和这一族的筛选、排序同一形状语言，
               摆在一起分不出谁是谁。
               它顶替的是右上角原来那颗「选文件」文字键 —— 那颗按的是 SAF 挑单个文件来播，
               和歌词页那颗「选文件」（挑歌词目录）不是同一件事，那一条能力随按钮一起撤了。 */
            IconKind.Settings -> {
                drawPath(
                    Path().apply {
                        moveTo(s * 0.64099f, s * 0.46615f)
                        lineTo(s * 0.71014f, s * 0.46841f)
                        lineTo(s * 0.71014f, s * 0.53159f)
                        lineTo(s * 0.64099f, s * 0.53385f)
                        lineTo(s * 0.62363f, s * 0.57576f)
                        lineTo(s * 0.67093f, s * 0.62625f)
                        lineTo(s * 0.62625f, s * 0.67093f)
                        lineTo(s * 0.57576f, s * 0.62363f)
                        lineTo(s * 0.53385f, s * 0.64099f)
                        lineTo(s * 0.53159f, s * 0.71014f)
                        lineTo(s * 0.46841f, s * 0.71014f)
                        lineTo(s * 0.46615f, s * 0.64099f)
                        lineTo(s * 0.42424f, s * 0.62363f)
                        lineTo(s * 0.37375f, s * 0.67093f)
                        lineTo(s * 0.32907f, s * 0.62625f)
                        lineTo(s * 0.37637f, s * 0.57576f)
                        lineTo(s * 0.35901f, s * 0.53385f)
                        lineTo(s * 0.28986f, s * 0.53159f)
                        lineTo(s * 0.28986f, s * 0.46841f)
                        lineTo(s * 0.35901f, s * 0.46615f)
                        lineTo(s * 0.37637f, s * 0.42424f)
                        lineTo(s * 0.32907f, s * 0.37375f)
                        lineTo(s * 0.37375f, s * 0.32907f)
                        lineTo(s * 0.42424f, s * 0.37637f)
                        lineTo(s * 0.46615f, s * 0.35901f)
                        lineTo(s * 0.46841f, s * 0.28986f)
                        lineTo(s * 0.53159f, s * 0.28986f)
                        lineTo(s * 0.53385f, s * 0.35901f)
                        lineTo(s * 0.57576f, s * 0.37637f)
                        lineTo(s * 0.62625f, s * 0.32907f)
                        lineTo(s * 0.67093f, s * 0.37375f)
                        lineTo(s * 0.62363f, s * 0.42424f)
                        close()
                    }, color,
                    style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawCircle(color, radius = s * 0.078f, center = Offset(s * 0.5f, s * 0.5f))
            }

            /* 刷新（「获取」那颗）：一段 290° 的弧 + 端点两笔箭头，缺口朝右上。
               半径 0.245 画框、笔画 0.075 —— 墨宽 0.542 画框（19dp 画框时 28.3px），
               和同排的 Close（0.6136）差半成，是这一族里"圈"形自己的账。
               中心线 x 0.255..0.722 / y 0.255..0.745：横向少 0.023 是因为弧在右边留了缺口，
               整颗墨中心偏左 0.0115 画框 = 19dp 画框时 0.6px，在量化底噪里，不额外补。 */
            IconKind.Refresh -> {
                val r = s * 0.245f
                drawArc(
                    color, startAngle = 45f, sweepAngle = 290f, useCenter = false,
                    topLeft = Offset(s * 0.5f - r, s * 0.5f - r), size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(width = st, cap = StrokeCap.Round)
                )
                /* 箭头：骑在弧末端上的实心三角。
                   改前是两撇（各 0.09 画框），但它们的角平分线离弧末端的切线整整差 **90°**、两撇还一长一短
                   不对称 ⇒ 看着像别着劲贴在弧上、指错了方向（他 10-22 圈出来那句"箭头错位"）。
                   摆正之后仍然不行：19dp 画框上两撇的翅膀只张到 6px，比一支笔画（3.9px）宽不了多少，还是糊
                   —— 和 OrderSeq 那支小三角同一条道理，小的直接画实心。
                   弧末端在 335°（顺时针走到这里），切线 d = (0.4226, 0.9063)、朝圆外的法向 n = (0.9063, −0.4226)：
                   顶点 = 弧端 + 0.095·d，底边中点 = 弧端 − 0.035·d，半宽 0.075 ⇒ 墨 7.1 x 7.8px、对称于切线。
                   整颗墨外接框因此往右长出 0.30dp（真机 0.8px），弧自己那三档边界没动、视觉中心没挪。 */
                drawPath(
                    Path().apply {
                        moveTo(s * 0.7621f, s * 0.4825f)
                        lineTo(s * 0.7752f, s * 0.3330f)
                        lineTo(s * 0.6392f, s * 0.3964f)
                        close()
                    }, color
                )
            }

            /* 多选：一只方框里一道勾。按这一族的口径画 —— 默认笔画 0.075·画框下墨宽正好 0.5 画框
               （25dp 上 12.50 x 12.50dp = 真机 34.4px 见方）、墨中心压在画框正中，所以它和旁边筛选、
               排序共用同一只 RowIcon 盒时白缝仍是 27dp、下面那一列车道不断。
               框的中心线 0.2875..0.7125（加半个笔画正好落到 0.25..0.75）；勾吃 0.72 成笔画
               （LoopOne 里那个「1」同一条道理：框里的东西再按满笔画，就会糊到框上）。 */
            IconKind.MultiSelect -> {
                val b0 = s * 0.2875f
                val b1 = s * 0.7125f
                drawLine(color, Offset(b0, b0), Offset(b1, b0), st, StrokeCap.Round)
                drawLine(color, Offset(b1, b0), Offset(b1, b1), st, StrokeCap.Round)
                drawLine(color, Offset(b1, b1), Offset(b0, b1), st, StrokeCap.Round)
                drawLine(color, Offset(b0, b1), Offset(b0, b0), st, StrokeCap.Round)
                val ds = st * 0.72f
                drawLine(color, Offset(s * 0.39f, s * 0.50f), Offset(s * 0.47f, s * 0.58f), ds, StrokeCap.Round)
                drawLine(color, Offset(s * 0.47f, s * 0.58f), Offset(s * 0.62f, s * 0.39f), ds, StrokeCap.Round)
            }


            IconKind.Download -> {                drawLine(color, Offset(s * 0.50f, s * 0.18f), Offset(s * 0.50f, s * 0.60f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.33f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.67f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.22f, s * 0.80f), Offset(s * 0.78f, s * 0.80f), st, StrokeCap.Round)
            }

            /* 排序：icon_u5mmu4af81c/paixu.svg —— 左右两支箭头，左支朝下、倒钩朝左下，
               右支朝上、倒钩朝右下。原图是填充轮廓，中心线直接从路径读出来：笔宽 72、
               杆 x=432 / 596、杆长 246..770、倒钩从杆的端点 45° 伸 120、圆头收尾。
               尺寸按"和筛选同一只 13.74dp 正方框、等比不形变"：原图墨高 0.6262 是长边，
               整颗乘 u=(0.54952-0.075)/(0.6262-0.075)=0.86086（X/Y 同一个系数，倒钩仍是 45°），
               于是墨 = 0.44086 × 0.54952 画框 = 11.0 × 13.74dp，比筛选窄 12% —— 这是它自己
               原图就瘦（杆距只有墨宽的 41%）造成的，不是压出来的。 */
            IconKind.Sort -> {
                drawLine(color, Offset(s * 0.42574f, s * 0.26274f), Offset(s * 0.42574f, s * 0.73726f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42574f, s * 0.73726f), Offset(s * 0.31707f, s * 0.62860f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.57426f, s * 0.73726f), Offset(s * 0.57426f, s * 0.26274f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.57426f, s * 0.26274f), Offset(s * 0.68293f, s * 0.37140f), st, StrokeCap.Round)
            }

            // 对勾：选中那颗。两笔，短笔 45° 落到底、长笔从那儿甩上去
            IconKind.Check -> {
                drawLine(color, Offset(s * 0.22f, s * 0.50f), Offset(s * 0.42f, s * 0.70f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.70f), Offset(s * 0.78f, s * 0.32f), st, StrokeCap.Round)
            }

            IconKind.Close -> {
                drawLine(color, Offset(s * 0.24f, s * 0.24f), Offset(s * 0.76f, s * 0.76f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.76f, s * 0.24f), Offset(s * 0.24f, s * 0.76f), st, StrokeCap.Round)
            }

            // 三横：拖动改顺序
            IconKind.Drag -> {
                drawLine(color, Offset(s * 0.20f, s * 0.31f), Offset(s * 0.80f, s * 0.31f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.20f, s * 0.50f), Offset(s * 0.80f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.20f, s * 0.69f), Offset(s * 0.80f, s * 0.69f), st, StrokeCap.Round)
            }

            // 倍速：圈里两个快进箭头
            IconKind.Speed -> {
                drawCircle(
                    color, radius = s * 0.34f, center = Offset(s / 2f, s / 2f),
                    style = Stroke(width = st)
                )
                drawLine(color, Offset(s * 0.34f, s * 0.37f), Offset(s * 0.49f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.49f, s * 0.50f), Offset(s * 0.34f, s * 0.63f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.51f, s * 0.37f), Offset(s * 0.66f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.50f), Offset(s * 0.51f, s * 0.63f), st, StrokeCap.Round)
            }

            // 横屏/铺满：四个角
            IconKind.Expand -> {
                val m = s * 0.20f
                val far = s - m
                val len = s * 0.17f
                listOf(m to 1f, far to -1f).forEach { (x, dx) ->
                    listOf(m to 1f, far to -1f).forEach { (y, dy) ->
                        drawLine(color, Offset(x, y), Offset(x, y + len * dy), st, StrokeCap.Round)
                        drawLine(color, Offset(x, y), Offset(x + len * dx, y), st, StrokeCap.Round)
                    }
                }
            }

            /* 退 10 秒 / 进 10 秒：一圈开口的弧带箭头（rotateRing）+ 中间「10」。
               退那一颗只把箭头换到弧的另一端、改朝左，**数字不跟着镜像** —— 镜像过去读出来是「01」。
               两位数字占 0.30 高、笔画取弧的 0.72 成：旧版数字只有 0.14 高、笔画和弧一样粗，
               26dp 画框（真机 48.7px）上「1」和「0」各糊成一个点，读不出是十。 */
            IconKind.SeekBack10, IconKind.SeekForward10 -> {
                rotateRing(color, st, back = kind == IconKind.SeekBack10)
                val ds = st * 0.72f
                drawLine(color, Offset(0.38f * s, 0.35f * s), Offset(0.38f * s, 0.65f * s), ds, StrokeCap.Round)
                drawLine(color, Offset(0.32f * s, 0.41f * s), Offset(0.38f * s, 0.35f * s), ds, StrokeCap.Round)
                drawOval(
                    color = color,
                    topLeft = Offset(0.48f * s, 0.35f * s),
                    size = androidx.compose.ui.geometry.Size(0.20f * s, 0.30f * s),
                    style = Stroke(width = ds),
                )
            }

            // 退出全屏：四颗从角上往里的箭头（Expand 那四只外扩的直角反过来）
            IconKind.Shrink -> {
                val m = s * 0.24f
                val far = s - m
                val len = s * 0.19f
                val barb = s * 0.10f
                listOf(m to 1f, far to -1f).forEach { (x, dx) ->
                    listOf(m to 1f, far to -1f).forEach { (y, dy) ->
                        val ex = x + len * dx
                        val ey = y + len * dy
                        drawLine(color, Offset(x, y), Offset(ex, ey), st, StrokeCap.Round)
                        drawLine(color, Offset(ex, ey), Offset(ex - barb * dx, ey), st, StrokeCap.Round)
                        drawLine(color, Offset(ex, ey), Offset(ex, ey - barb * dy), st, StrokeCap.Round)
                    }
                }
            }

            /* 亮度：一颗日头 + 八道光线（参考图那块浮标左边那只）。
               日头半径 0.19、光线从 0.29 走到 0.42 → 墨外沿正好 0.42 + 半个笔画，和旁边那几颗同框。 */
            IconKind.Sun -> {
                drawCircle(
                    color = color, radius = s * 0.19f, center = Offset(s / 2f, s / 2f),
                    style = Stroke(width = st)
                )
                for (i in 0 until 8) {
                    val a = i * 45.0
                    val cx = kotlin.math.cos(Math.toRadians(a)).toFloat()
                    val cy = kotlin.math.sin(Math.toRadians(a)).toFloat()
                    drawLine(
                        color,
                        Offset(s / 2f + cx * s * 0.29f, s / 2f + cy * s * 0.29f),
                        Offset(s / 2f + cx * s * 0.42f, s / 2f + cy * s * 0.42f),
                        st, StrokeCap.Round
                    )
                }
            }

            /* 声音：一只喇叭 + 两道波。整颗按墨居中：喇叭 0.23..0.51、两道波的圆心在 0.49、
               半径 0.16 / 0.28 → 外沿正好到 0.77，左右各留 0.23，和旁边那几颗同一根中轴。 */
            IconKind.Volume -> {
                val p = Path()
                p.moveTo(s * 0.23f, s * 0.42f)
                p.lineTo(s * 0.35f, s * 0.42f)
                p.lineTo(s * 0.51f, s * 0.26f)
                p.lineTo(s * 0.51f, s * 0.74f)
                p.lineTo(s * 0.35f, s * 0.58f)
                p.lineTo(s * 0.23f, s * 0.58f)
                p.close()
                drawPath(p, color, style = Stroke(width = st, join = StrokeJoin.Round))
                listOf(0.16f, 0.28f).forEach { rr ->
                    drawArc(
                        color = color, startAngle = -55f, sweepAngle = 110f, useCenter = false,
                        topLeft = Offset(s * (0.49f - rr), s * (0.5f - rr)),
                        size = androidx.compose.ui.geometry.Size(rr * 2 * s, rr * 2 * s),
                        style = Stroke(width = st, cap = StrokeCap.Round)
                    )
                }
            }

            /* 画幅：一张方相框，里面一颗太阳加一座山 —— 播放页右上角那颗「换中间那一格」。
               外沿按齿轮那颗收：中心线正好落在 x/y 0.28986..0.71014（跨 0.42028 画框），
               和 Settings 那条路径同一只方框，所以两颗的墨外沿逐位相同 ——
               22dp 画框 + 默认笔画 0.075 画框时都是 (0.42028+0.075)×22 = 10.91dp = 30px。
               原来是 0.16..0.84 横着一大块（墨 45.7×36px），摆在齿轮旁边就是一颗大一颗小。
               改成方的而不是等比缩小，是因为等比缩会把画框一起缩到 14.5dp、
               笔画跟着细到 1.09dp（齿轮是 1.65dp），一粗一细。 */
            IconKind.Photo -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.28986f, s * 0.28986f),
                    size = androidx.compose.ui.geometry.Size(s * 0.42028f, s * 0.42028f),
                    cornerRadius = CornerRadius(s * 0.05f),
                    style = Stroke(width = st, join = StrokeJoin.Round)
                )
                drawCircle(
                    color = color, radius = s * 0.033f, center = Offset(s * 0.385f, s * 0.400f),
                    style = Stroke(width = st)
                )
                // 山：折线两端都收在相框内沿里面（内沿 = 0.28986 + 半个笔画 = 0.3274）
                val ridge = listOf(
                    0.335f to 0.655f, 0.440f to 0.545f, 0.515f to 0.615f, 0.585f to 0.555f, 0.665f to 0.655f
                )
                for (i in 0 until ridge.size - 1) {
                    drawLine(
                        color,
                        Offset(s * ridge[i].first, s * ridge[i].second),
                        Offset(s * ridge[i + 1].first, s * ridge[i + 1].second),
                        st, StrokeCap.Round
                    )
                }
            }

            /* 视频方向旋转：一只竖着的画面 + 右边一道顺时针弧带箭头（三个候选里他点的这一支）。
               不再用「一圈弧 + 中间直角」那一版 —— 这一排里 ⟳ 自动连播和退/进 10 秒三颗都是整圈箭头，
               再来第四颗整圈箭头就分不出谁是谁了；换成画面框之后一眼读得出「转的是画面」。
               框 0.16..0.46 × 0.26..0.74（26dp 画框上空心 12.2×19.7px，读得出是一只框）、
               弧心在画面正中、半径 0.36、只走右侧 116°，箭头骑在弧的下端顺着切线往左下。 */
            IconKind.Rotate -> {
                drawRoundRect(
                    color = color, topLeft = Offset(0.16f * s, 0.26f * s),
                    size = androidx.compose.ui.geometry.Size(0.30f * s, 0.48f * s),
                    cornerRadius = CornerRadius(0.03f * s),
                    style = Stroke(width = st),
                )
                val r = s * 0.36f
                drawArc(
                    color = color, startAngle = -58f, sweepAngle = 116f, useCenter = false,
                    topLeft = Offset(s / 2f - r, s / 2f - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(width = st, cap = StrokeCap.Round),
                )
                val a = Math.toRadians(58.0)
                val px = s / 2f + r * cos(a).toFloat()
                val py = s / 2f + r * sin(a).toFloat()
                val tx = -sin(a).toFloat()
                val ty = cos(a).toFloat()
                val p = Path()
                p.moveTo(px + tx * s * 0.15f, py + ty * s * 0.15f)
                p.lineTo(px - ty * s * 0.085f, py + tx * s * 0.085f)
                p.lineTo(px + ty * s * 0.085f, py - tx * s * 0.085f)
                p.close()
                drawPath(p, color)
            }

            // 复制：后面那张只露上边和右边，前面那张画整框
            IconKind.Copy -> {
                drawLine(color, Offset(s * 0.34f, s * 0.20f), Offset(s * 0.74f, s * 0.20f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.74f, s * 0.20f), Offset(s * 0.74f, s * 0.60f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.36f), Offset(s * 0.62f, s * 0.36f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.62f, s * 0.36f), Offset(s * 0.62f, s * 0.80f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.62f, s * 0.80f), Offset(s * 0.24f, s * 0.80f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.80f), Offset(s * 0.24f, s * 0.36f), st, StrokeCap.Round)
            }

            // 打开地址：左下一个开口的方框，一支箭头从框里出去到右上
            IconKind.OpenLink -> {
                drawLine(color, Offset(s * 0.62f, s * 0.42f), Offset(s * 0.24f, s * 0.42f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.42f), Offset(s * 0.24f, s * 0.78f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.78f), Offset(s * 0.60f, s * 0.78f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.60f, s * 0.78f), Offset(s * 0.60f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.46f, s * 0.56f), Offset(s * 0.78f, s * 0.24f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.58f, s * 0.24f), Offset(s * 0.78f, s * 0.24f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.78f, s * 0.24f), Offset(s * 0.78f, s * 0.44f), st, StrokeCap.Round)
            }

            IconKind.Search -> {
                // 短尾：柄从圈的边沿只伸到 0.72（原来 0.80），长度 0.17s ≈ 原来的六成
                drawCircle(color, radius = s * 0.24f, center = Offset(s * 0.42f, s * 0.42f),
                           style = Stroke(width = st))
                drawLine(color, Offset(s * 0.60f, s * 0.60f), Offset(s * 0.72f, s * 0.72f), st, StrokeCap.Round)
            }

            // 漏斗：上口一条横，两侧斜着收到中间，再接一小段敞口的管子
            /* 筛选：换成 icon_u5mmu4af81c/shaixuan.svg 那个漏斗——顶上一横杠，两端各折一条 45°
               斜边收下来变成两条腿，左腿短、右腿长（原图两条腿就不等长，不是画歪）。
               整条是一根折线，圆头 + 圆角接，两个端点和六个拐点都自动带圆。
               中心线跨 0.425 画框 + 一支笔画 0.075 = 墨宽 0.5 画框，和栏里另外七颗同格；
               墨外接框 y 0.22524..0.77476，中心正好 0.5。
               原 SVG 的笔画只有它墨宽的 7.2%，这里按栏规统一到 0.075 画框（1.875dp），
               所以看着比那张图粗一点——不然它在一排 1.875dp 的邻居旁边像根头发。 */
            IconKind.Filter -> drawPath(
                Path().apply {
                    moveTo(s * 0.43510f, s * 0.63453f)
                    lineTo(s * 0.43510f, s * 0.47530f)
                    lineTo(s * 0.28750f, s * 0.32836f)
                    lineTo(s * 0.28750f, s * 0.26274f)
                    lineTo(s * 0.71250f, s * 0.26274f)
                    lineTo(s * 0.71250f, s * 0.32836f)
                    lineTo(s * 0.55975f, s * 0.48081f)
                    lineTo(s * 0.55975f, s * 0.73726f)
                }, color,
                style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            /* 旗形加号：竖笔 + 飘带 + 右下加号。整套 y 比对称位置高 0.025 —— 原来按 0.20/0.72 那组数画，
               墨外接框落在 0.1475..0.8975，中心 0.5225，和并排的 > （中心正好 0.5）差 1.5px，
               真机量到之后整体上移补平，别再"顺手改成整数"。 */
            /* 「歌单 / 新建歌单」那颗。加号压在框的右上角上、四臂等长；方框四条边都在，
               只在上边和右边各让出一截，让加号和框之间留得住一道缝。
               上一版报的"断口 1.5px"是**中心线**距离 —— 一支圆头笔画的半径就有 1.85px，两边一叠，
               缝被糊死等于没有（他圈出来说"这里留个小间隙啊"，眼睛对、我的数错）。
               缝要看得见，中心线距必须大过一支笔画 0.075：这里取 0.120，
               净缝 = (0.120 − 0.075) × 49.5 = **真机 2.2px**。代价是加号从 12.9px 收到 10.4px、
               上边保住 54%（再留大缝就继续吃框）。
               框 0.487 见方钉在左下、加号中心落在右上角 (0.691, 0.309)；六段外接 0.592 x 0.592
               （长宽比 1.0000），含 0.075 圆头的墨 33.0 x 33.0px @18dp —— 正方。 */
            IconKind.PlaylistAdd -> {
                drawLine(color, Offset(s * 0.204f, s * 0.309f), Offset(s * 0.204f, s * 0.796f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.204f, s * 0.796f), Offset(s * 0.691f, s * 0.796f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.691f, s * 0.796f), Offset(s * 0.691f, s * 0.534f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.204f, s * 0.309f), Offset(s * 0.466f, s * 0.309f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.586f, s * 0.309f), Offset(s * 0.796f, s * 0.309f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.691f, s * 0.204f), Offset(s * 0.691f, s * 0.414f), st, StrokeCap.Round)
            }

            IconKind.ArrowLeft -> {
                drawLine(color, Offset(s * 0.74f, s * 0.50f), Offset(s * 0.24f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.28f), Offset(s * 0.22f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.72f), Offset(s * 0.22f, s * 0.50f), st, StrokeCap.Round)
            }

            IconKind.Home -> {
                drawLine(color, Offset(s * 0.16f, s * 0.50f), Offset(s * 0.50f, s * 0.20f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.50f, s * 0.20f), Offset(s * 0.84f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.46f), Offset(s * 0.24f, s * 0.82f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.76f, s * 0.46f), Offset(s * 0.76f, s * 0.82f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.24f, s * 0.82f), Offset(s * 0.76f, s * 0.82f), st, StrokeCap.Round)
            }

            IconKind.MusicNote -> {
                drawCircle(color, radius = s * 0.11f, center = Offset(s * 0.36f, s * 0.72f))
                drawCircle(color, radius = s * 0.11f, center = Offset(s * 0.74f, s * 0.62f))
                drawLine(color, Offset(s * 0.47f, s * 0.72f), Offset(s * 0.47f, s * 0.24f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.85f, s * 0.62f), Offset(s * 0.85f, s * 0.14f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.47f, s * 0.24f), Offset(s * 0.85f, s * 0.14f), st, StrokeCap.Round)
            }

            IconKind.Video -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(s * 0.14f, s * 0.26f),
                    size = androidx.compose.ui.geometry.Size(s * 0.72f, s * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.10f, s * 0.10f),
                    style = Stroke(width = st)
                )
                val p = Path().apply {
                    moveTo(s * 0.42f, s * 0.38f)
                    lineTo(s * 0.60f, s * 0.50f)
                    lineTo(s * 0.42f, s * 0.62f)
                    close()
                }
                drawPath(p, color)
            }

            IconKind.Person -> {
                drawCircle(color, radius = s * 0.15f, center = Offset(s * 0.50f, s * 0.33f))
                drawArc(
                    color = color, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(s * 0.22f, s * 0.52f),
                    size = androidx.compose.ui.geometry.Size(s * 0.56f, s * 0.56f),
                    style = Stroke(width = st, cap = StrokeCap.Round)
                )
            }

            IconKind.Add -> {
                drawLine(color, Offset(s * 0.50f, s * 0.22f), Offset(s * 0.50f, s * 0.78f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.22f, s * 0.50f), Offset(s * 0.78f, s * 0.50f), st, StrokeCap.Round)
            }

            // 装扮那颗 T 恤：轮廓拆成一段段圆头直线，只有领口那一段是弧。
            // 墨框 x 0.14..0.86 / y 0.24..0.78，和旁边「视频」那颗（0.14..0.86 + 半个笔画）同档大小
            IconKind.Shirt -> {
                val pts = listOf(
                    0.38f to 0.24f, 0.22f to 0.30f, 0.14f to 0.48f, 0.30f to 0.42f,
                    0.30f to 0.78f, 0.70f to 0.78f, 0.70f to 0.42f, 0.86f to 0.48f,
                    0.78f to 0.30f, 0.62f to 0.24f,
                )
                for (i in 0 until pts.size - 1) {
                    drawLine(
                        color,
                        Offset(s * pts[i].first, s * pts[i].second),
                        Offset(s * pts[i + 1].first, s * pts[i + 1].second),
                        st, StrokeCap.Round
                    )
                }
                val collar = Path().apply {
                    moveTo(s * 0.62f, s * 0.24f)
                    quadraticTo(s * 0.50f, s * 0.36f, s * 0.38f, s * 0.24f)
                }
                drawPath(collar, color, style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

enum class IconKind {
    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown, ChevronRight,
    Home, MusicNote, Video, Person, Shirt, Add, Favorite, FavoriteLine, PlayAdd, PlayQueue, SquareAdd, Share, PinTop, Trash,
    Download, ArrowLeft, Search, PlaylistAdd, Sort, MultiSelect,
    Close, Drag, Speed, Check, Expand, Rotate, Copy, OpenLink, OrderSeq, LoopOne, Filter, More, Settings, Refresh, Photo,
    SeekBack10, SeekForward10, Shrink, Volume, Sun
}

/**
 * 一圈开口的弧 + 弧末端那颗实心三角箭头。退 10 秒 / 进 10 秒两颗共用（旋转那颗 10-16 换成画面框，不再走这里）。
 *
 * 弧半径 0.34、缺口以正上方为中心张 100°（320° 走到 220°）—— 这样弧自己就把墨框撑成
 * 0.16..0.84 见方，箭头再大也不会把框拉歪。两颗的弧一模一样，只有箭头换端点：
 * 正着那颗骑在顺时针末端（220°，左上）朝前 = 顺时针；镜像那颗骑在 320°（右上）朝反 = 逆时针。
 * 箭头底边垂直切线、正好骑在弧端上（半宽 0.085 比笔画 0.075 宽一档，才读得出是一记箭头），
 * 尖沿切线走 0.15 —— 全部落在 0.16..0.84 之内。
 * 旧版那记箭头是两撇线：一撇落在半径 0.34、一撇落在 0.18，不对称，所以怎么看都是歪的。
 */
private fun DrawScope.rotateRing(color: Color, st: Float, back: Boolean) {
    val s = size.minDimension
    val r = s * 0.34f
    val cx = s / 2f
    val cy = s / 2f
    drawArc(
        color = color, startAngle = 320f, sweepAngle = 260f, useCenter = false,
        topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
        style = Stroke(width = st, cap = StrokeCap.Round),
    )
    val a = Math.toRadians(if (back) 320.0 else 220.0)
    val ca = cos(a).toFloat()
    val sa = sin(a).toFloat()
    val px = cx + r * ca
    val py = cy + r * sa
    // 顺时针走的切线 = (-sin, cos)；逆时针那颗取反向
    val tx = if (back) sa else -sa
    val ty = if (back) -ca else ca
    val p = Path()
    p.moveTo(px + tx * s * 0.15f, py + ty * s * 0.15f)
    p.lineTo(px - ty * s * 0.085f, py + tx * s * 0.085f)
    p.lineTo(px + ty * s * 0.085f, py - tx * s * 0.085f)
    p.close()
    drawPath(p, color)
}

/** 三尖倒圆角的实心三角：每个尖沿两条邻边各让进 t = r / tan(θ/2)（θ 是该尖的内角），
    用原顶点当控制点画二次弧。t 上限取邻边长的 40%，钝角处不会让过头。 */
private fun DrawScope.roundedTri(a: Offset, b: Offset, c: Offset, r: Float, color: Color) {
    val v = listOf(a, b, c)
    val p = Path()
    for (i in 0..2) {
        val cur = v[i]
        val d1 = v[(i + 2) % 3] - cur
        val d2 = v[(i + 1) % 3] - cur
        val l1 = d1.getDistance()
        val l2 = d2.getDistance()
        val theta = acos(((d1.x * d2.x + d1.y * d2.y) / (l1 * l2)).coerceIn(-1f, 1f))
        val t = (r / tan(theta / 2f)).coerceAtMost(minOf(l1, l2) * 0.4f)
        val from = cur + d1 * (t / l1)
        val to = cur + d2 * (t / l2)
        if (i == 0) p.moveTo(from.x, from.y) else p.lineTo(from.x, from.y)
        p.quadraticTo(cur.x, cur.y, to.x, to.y)
    }
    p.close()
    drawPath(p, color)
}

/** 整套 y 比 0.80/0.52/0.14/0.32 那组数高 0.0367：贝塞尔的墨外接框量到 0.2733..0.80，
    中心 0.5367，压在画框中线下面 0.81dp（真机 1.5px）—— 行上另外两颗（加队列、三点）
    的墨中心都在 0.5，心落单低一截。描边那颗（FavoriteLine）四边各扩 0.0375，中心跟着一起搬。 */
/** 右栏归一：k/t 把这条路径的墨外沿收到"宽 = 0.5 画框"。填充版没有笔画，k=0.87169 直接
    外沿到 0.25..0.75；描边版外面还要套半支 st，路径本身要再往里收（k=0.74093），
    两颗的墨外沿才一样大——不然描边心比实心心宽 13%（原来 22dp 画框下 14.27 对 12.62dp）。 */
private fun heartPath(s: Float, k: Float, t: Float, u: Float = 1f): Path = Path().apply {
    // u：整颗心绕画框正中 0.5 等比缩放，X/Y 同一个系数 —— 只改大小、不改形状
    fun p(v: Float) = s * (0.5f + (v * k + t - 0.5f) * u)
    moveTo(p(0.50f), p(0.7633f))
    cubicTo(p(0.06f), p(0.4833f), p(0.18f), p(0.1033f), p(0.50f), p(0.2833f))
    cubicTo(p(0.82f), p(0.1033f), p(0.94f), p(0.4833f), p(0.50f), p(0.7633f))
    close()
}

/** 列表循环那一圈：两只互扣的钩（上钩往右、下钩往左），两端各一记实心三角箭头。
    照主流播放器通用的 repeat 那一支 —— 原来这里画的是一圈 300° 弧加一记箭头，和「刷新」同形。
    下钩就是上钩绕画布中心转 180°（腿 0.22↔0.78、横边 0.28↔0.72、箭头 0.17/0.39↔0.83/0.61），
    所以墨框自己居中，和笔画粗细无关。两枝腿各停在 0.50（画面正中）就收：
    主流那支也不把两头接死，中间留的这段空正好给单曲循环那颗「1」站。 */
private fun DrawScope.repeatLoop(color: Color, st: Float) {
    val s = size.minDimension
    val style = Stroke(width = st, cap = StrokeCap.Round, join = StrokeJoin.Round)
    listOf(
        listOf(0.22f to 0.50f, 0.22f to 0.28f, 0.66f to 0.28f),
        listOf(0.78f to 0.50f, 0.78f to 0.72f, 0.34f to 0.72f),
    ).forEach { pts ->
        drawPath(
            Path().apply {
                moveTo(s * pts[0].first, s * pts[0].second)
                lineTo(s * pts[1].first, s * pts[1].second)
                lineTo(s * pts[2].first, s * pts[2].second)
            },
            color, style = style,
        )
    }
    listOf(
        listOf(0.66f to 0.17f, 0.84f to 0.28f, 0.66f to 0.39f),
        listOf(0.34f to 0.61f, 0.16f to 0.72f, 0.34f to 0.83f),
    ).forEach { t ->
        drawPath(
            Path().apply {
                moveTo(s * t[0].first, s * t[0].second)
                lineTo(s * t[1].first, s * t[1].second)
                lineTo(s * t[2].first, s * t[2].second)
                close()
            },
            color,
        )
    }
}
