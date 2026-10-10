package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 参考图实测：轨道厚 6px=2dp、已播与未播只差 ΔB=7（即同一支灰），两端各有一个小圆点。
// material3 的 Slider 这个版本没有 trackHeight 参数，画不出这个形状，所以自绘。
@Composable
fun ScrubBar(
    fraction: Float,
    enabled: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = Tokens.Track,
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
        val r = Tokens.Dimen.ThumbSize.toPx() / 2f
        drawCircle(trackColor, radius = r, center = Offset(r, cy))
        drawCircle(trackColor, radius = r, center = Offset(size.width - r, cy))
        drawLine(
            color = trackColor,
            start = Offset(r, cy),
            end = Offset(size.width - r, cy),
            strokeWidth = Tokens.Dimen.TrackHeight.toPx()
        )
        val x = r + fraction.coerceIn(0f, 1f) * (size.width - 2f * r)
        drawCircle(thumbColor, radius = r * 1.2f, center = Offset(x, cy))
    }
}
