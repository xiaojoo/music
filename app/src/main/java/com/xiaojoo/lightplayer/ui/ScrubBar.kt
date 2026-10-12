package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 参考图实测：整条是一根胶囊（两端圆头收尾，没有独立端点），厚 10px=4.9dp；
// 已播/未播同一支颜色只变深浅（22% 对 7%），滑块是只比条厚 0.5dp 的实心小圆。
// material3 的 Slider 这个版本没有 trackHeight 参数，画不出这个形状，所以自绘。
@Composable
fun ScrubBar(
    fraction: Float,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    idleColor: Color = Tokens.TrackIdle,
    playedColor: Color = Tokens.TrackPlayed,
    thumbColor: Color = Tokens.InkStrong
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val w = size.width.toFloat().coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onDragStart()
                    var f = (down.position.x / w).coerceIn(0f, 1f)
                    onDrag(f)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        f = (change.position.x / w).coerceIn(0f, 1f)
                        onDrag(f)
                        change.consume()
                    }
                    onDragEnd(f)
                }
            }
    ) {
        val cy = size.height / 2f
        val th = Tokens.Dimen.TrackHeight.toPx()
        val r = Tokens.Dimen.ThumbSize.toPx() / 2f
        val top = cy - th / 2f
        val x = r + fraction.coerceIn(0f, 1f) * (size.width - 2f * r)
        drawRoundRect(idleColor, Offset(0f, top), Size(size.width, th), CornerRadius(th / 2f))
        drawRoundRect(playedColor, Offset(0f, top), Size(x, th), CornerRadius(th / 2f))
        drawCircle(thumbColor, radius = r, center = Offset(x, cy))
    }
}
