package com.xiaojoo.lightplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// 黑胶唱机。比例按参考图量出来的：盘面约占卡片边长的 76%，唱臂支点在卡片右上角。
// 盘面旋转走 graphicsLayer（不触发重组），唱臂落点是播放状态的 0→1 插值。
@Composable
fun Turntable(
    cover: ImageBitmap?,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val angle = remember { Animatable(0f) }
    val armProgress = remember { Animatable(0f) }

    LaunchedEffect(playing) {
        if (playing) {
            armProgress.animateTo(1f, tween(600, easing = LinearEasing))
            while (true) {
                angle.animateTo(angle.value + 360f, tween(1800, easing = LinearEasing))
                delay(1L)
            }
        } else {
            armProgress.animateTo(0f, tween(600, easing = LinearEasing))
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Tokens.Dimen.TurntableCorner))
            .background(Tokens.CardBg),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsRotation { angle.value }
        ) {
            drawVinyl(cover, cardSide = min(size.width, size.height))
        }

        // 唱臂单独一层：它绕自己的支点转，不能跟着盘面一起转
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawTonearm(armProgress.value, cardSide = min(size.width, size.height))
        }
    }
}

private fun DrawScope.drawVinyl(cover: ImageBitmap?, cardSide: Float) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = cardSide * 0.38f

    drawCircle(color = Tokens.InkStrong, radius = r, center = c)
    for (k in 1..4) {
        drawCircle(
            color = Color.White.copy(alpha = 0.05f),
            radius = r * (0.55f + 0.11f * k),
            center = c,
            style = Stroke(width = 1.5f)
        )
    }
    val labelR = r * 0.44f
    if (cover != null) {
        val d = (labelR * 2).toInt()
        drawImage(
            image = cover,
            dstSize = androidx.compose.ui.unit.IntSize(d, d),
            dstOffset = androidx.compose.ui.unit.IntOffset((c.x - labelR).toInt(), (c.y - labelR).toInt())
        )
    } else {
        drawCircle(color = Color(0xFFE3E7EA), radius = labelR, center = c)
    }
    drawCircle(color = Tokens.InkStrong, radius = r * 0.030f, center = c)
}

private fun DrawScope.drawTonearm(progress: Float, cardSide: Float) {
    val pivot = Offset(size.width * 0.875f, size.height * 0.115f)
    // 刚体臂：长度固定，只绕支点转角度。100° 停在盘外，139° 落到纹路上。
    val len = cardSide * 0.273f
    val theta = Math.toRadians((100.0 + 39.0 * progress))
    val tip = Offset(
        pivot.x + (len * cos(theta)).toFloat(),
        pivot.y + (len * sin(theta)).toFloat()
    )

    drawLine(
        color = Tokens.Ink,
        start = pivot,
        end = tip,
        strokeWidth = cardSide * 0.012f,
        cap = androidx.compose.ui.graphics.StrokeCap.Round
    )
    drawCircle(color = Color(0xFFB9BDC1), radius = cardSide * 0.052f, center = pivot)
    drawCircle(color = Tokens.InkStrong, radius = cardSide * 0.016f, center = pivot)
    drawCircle(color = Tokens.Ink, radius = cardSide * 0.014f, center = tip)
}

private fun Modifier.graphicsRotation(block: () -> Float): Modifier =
    this.graphicsLayer { rotationZ = block() }
