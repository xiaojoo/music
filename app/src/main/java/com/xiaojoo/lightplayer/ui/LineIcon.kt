package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 参考图里的图标是细线描边风格，用 Canvas 画比引一套 icon 字体更可控（宽度、圆头都能定）
@Composable
fun LineIcon(
    kind: IconKind,
    modifier: Modifier = Modifier,
    size: Dp = Tokens.Dimen.IconSize,
    color: Color = Tokens.Ink
) {
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension
        val st = s * 0.075f
        when (kind) {
            IconKind.Play -> {
                val p = Path().apply {
                    moveTo(s * 0.30f, s * 0.20f)
                    lineTo(s * 0.80f, s * 0.50f)
                    lineTo(s * 0.30f, s * 0.80f)
                    close()
                }
                drawPath(p, color)
            }

            IconKind.Pause -> {
                drawLine(color, Offset(s * 0.34f, s * 0.22f), Offset(s * 0.34f, s * 0.78f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.22f), Offset(s * 0.66f, s * 0.78f), st, StrokeCap.Round)
            }

            IconKind.Next -> {
                drawLine(color, Offset(s * 0.26f, s * 0.24f), Offset(s * 0.62f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.62f, s * 0.50f), Offset(s * 0.26f, s * 0.76f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.74f, s * 0.22f), Offset(s * 0.74f, s * 0.78f), st, StrokeCap.Round)
            }

            IconKind.Prev -> {
                drawLine(color, Offset(s * 0.74f, s * 0.24f), Offset(s * 0.38f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.50f), Offset(s * 0.74f, s * 0.76f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.26f, s * 0.22f), Offset(s * 0.26f, s * 0.78f), st, StrokeCap.Round)
            }

            // 播放顺序：一圈顺时针弧线，缺口留在正上方；箭头压在弧线的终点上（240°），
            // 两枝倒钩沿切线方向往后张开 60°，所以看着是弧"长"出来的箭头而不是贴上去的小钩
            // 顺序播放：一支往右的箭头顶到一条竖线——播到末尾就停，不带圈
            IconKind.OrderSeq -> {
                drawLine(color, Offset(s * 0.16f, s * 0.50f), Offset(s * 0.62f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.46f, s * 0.36f), Offset(s * 0.62f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.46f, s * 0.64f), Offset(s * 0.62f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.76f, s * 0.28f), Offset(s * 0.76f, s * 0.72f), st, StrokeCap.Round)
            }

            IconKind.Repeat -> loopArrow(color)

            // 单曲循环：同一圈箭头，中间立一个「1」
            IconKind.LoopOne -> {
                loopArrow(color)
                drawLine(color, Offset(s * 0.50f, s * 0.40f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.43f, s * 0.46f), Offset(s * 0.50f, s * 0.40f), st, StrokeCap.Round)
            }

            IconKind.Queue -> {
                drawLine(color, Offset(s * 0.14f, s * 0.22f), Offset(s * 0.60f, s * 0.22f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.42f), Offset(s * 0.60f, s * 0.42f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.62f), Offset(s * 0.44f, s * 0.62f), st, StrokeCap.Round)
                // 音符：符头贴在笔杆底端的左边（右边缘正好切到笔杆），不让笔杆穿过头心
                drawLine(color, Offset(s * 0.79f, s * 0.36f), Offset(s * 0.79f, s * 0.74f), st, StrokeCap.Round)
                drawCircle(color, radius = s * 0.085f, center = Offset(s * 0.705f, s * 0.74f))
            }

            IconKind.ChevronDown -> {
                drawLine(color, Offset(s * 0.26f, s * 0.40f), Offset(s * 0.50f, s * 0.64f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.50f, s * 0.64f), Offset(s * 0.74f, s * 0.40f), st, StrokeCap.Round)
            }

            IconKind.ChevronRight -> {
                drawLine(color, Offset(s * 0.40f, s * 0.26f), Offset(s * 0.64f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.64f, s * 0.50f), Offset(s * 0.40f, s * 0.74f), st, StrokeCap.Round)
            }

            IconKind.Favorite -> {
                val p = Path().apply {
                    moveTo(s * 0.50f, s * 0.80f)
                    cubicTo(s * 0.06f, s * 0.52f, s * 0.18f, s * 0.14f, s * 0.50f, s * 0.32f)
                    cubicTo(s * 0.82f, s * 0.14f, s * 0.94f, s * 0.52f, s * 0.50f, s * 0.80f)
                    close()
                }
                drawPath(p, color)
            }

            IconKind.Download -> {
                drawLine(color, Offset(s * 0.50f, s * 0.18f), Offset(s * 0.50f, s * 0.60f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.33f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.67f, s * 0.44f), Offset(s * 0.50f, s * 0.62f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.22f, s * 0.80f), Offset(s * 0.78f, s * 0.80f), st, StrokeCap.Round)
            }

            IconKind.Sort -> {
                drawLine(color, Offset(s * 0.26f, s * 0.18f), Offset(s * 0.26f, s * 0.82f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.31f), Offset(s * 0.26f, s * 0.17f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.31f), Offset(s * 0.26f, s * 0.17f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.14f, s * 0.69f), Offset(s * 0.26f, s * 0.83f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.38f, s * 0.69f), Offset(s * 0.26f, s * 0.83f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.26f), Offset(s * 0.86f, s * 0.26f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.50f), Offset(s * 0.80f, s * 0.50f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.55f, s * 0.74f), Offset(s * 0.74f, s * 0.74f), st, StrokeCap.Round)
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

            // 视频方向旋转：一圈弧线带箭头，中间一个直角标记
            IconKind.Rotate -> {
                val r = s * 0.30f
                drawArc(
                    color = color, startAngle = -50f, sweepAngle = 260f, useCenter = false,
                    topLeft = Offset(s / 2f - r, s / 2f - r),
                    size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
                    style = Stroke(width = st, cap = StrokeCap.Round)
                )
                drawLine(color, Offset(s * 0.66f, s * 0.20f), Offset(s * 0.76f, s * 0.32f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.76f, s * 0.32f), Offset(s * 0.62f, s * 0.36f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.58f), Offset(s * 0.42f, s * 0.42f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.42f), Offset(s * 0.58f, s * 0.42f), st, StrokeCap.Round)
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
                drawCircle(color, radius = s * 0.24f, center = Offset(s * 0.42f, s * 0.42f),
                           style = Stroke(width = st))
                drawLine(color, Offset(s * 0.60f, s * 0.60f), Offset(s * 0.80f, s * 0.80f), st, StrokeCap.Round)
            }

            IconKind.PlaylistAdd -> {
                drawLine(color, Offset(s * 0.30f, s * 0.20f), Offset(s * 0.30f, s * 0.60f), st, StrokeCap.Round)
                val flag = Path().apply {
                    moveTo(s * 0.30f, s * 0.20f)
                    cubicTo(s * 0.52f, s * 0.18f, s * 0.60f, s * 0.30f, s * 0.74f, s * 0.30f)
                }
                drawPath(flag, color, style = Stroke(width = st))
                drawLine(color, Offset(s * 0.52f, s * 0.72f), Offset(s * 0.80f, s * 0.72f), st, StrokeCap.Round)
                drawLine(color, Offset(s * 0.66f, s * 0.58f), Offset(s * 0.66f, s * 0.86f), st, StrokeCap.Round)
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
        }
    }
}

enum class IconKind {
    Play, Pause, Next, Prev, Repeat, Queue, ChevronDown, ChevronRight,
    Home, MusicNote, Video, Person, Add, Favorite, Download, ArrowLeft, Search, PlaylistAdd, Sort,
    Close, Drag, Speed, Expand, Rotate, Copy, OpenLink, OrderSeq, LoopOne
}

/** 一圈顺时针弧线：缺口留在正上方，弧线终点 240° 上接一个实心三角箭头。
    不用两条倒钩 —— 倒钩里朝内那一枝的端点会落到半径 0.234，正好压在圈这条笔画
    （0.075 宽、占 0.2325..0.3075）的内沿上，看着就是箭头和圈重叠了一小块。
    三角整支放在缺口里（240°..300° 这段没有弧），所以不会再压到圈。 */
private fun DrawScope.loopArrow(color: Color) {
    val s = this.size.minDimension
    val st = s * 0.075f
    val r = s * 0.27f
    val c = s / 2f
    drawArc(
        color = color, startAngle = 300f, sweepAngle = 300f, useCenter = false,
        topLeft = Offset(c - r, c - r),
        size = androidx.compose.ui.geometry.Size(r * 2, r * 2),
        style = Stroke(width = st, cap = StrokeCap.Round)
    )
    // 240° 那点的坐标、以及顺时针方向的切线和法线
    val px = c - 0.135f * s
    val py = c - 0.234f * s
    val tx = 0.866f
    val ty = -0.5f
    val nx = 0.5f
    val ny = 0.866f
    val apex = Offset(px + tx * 0.10f * s, py + ty * 0.10f * s)
    val bx = px - tx * 0.03f * s
    val by = py - ty * 0.03f * s
    val half = 0.078f * s
    val p = Path().apply {
        moveTo(apex.x, apex.y)
        lineTo(bx + nx * half, by + ny * half)
        lineTo(bx - nx * half, by - ny * half)
        close()
    }
    drawPath(p, color)
}
