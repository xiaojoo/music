package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 视频那一屏长按落在屏宽四等分的哪一格。Middle 不进「按住」那一档：它到点开抽屉，没有松手要收回的东西。 */
enum class FeedZone { Left, Middle, Right }

/** 按住快进、按住倒带共用这一个倍率 */
const val HOLD_SPEED = 2f

/** 倒带一跳退 500ms、隔 250ms 跳一次：净下来正好也是 2.0 倍速往后 */
private const val REWIND_STEP_MS = 500L
private const val REWIND_TICK_MS = 250L

/**
 * 视频一整页的手势：单击切播放/暂停照旧，长按按屏宽四等分 —— 左边那一格到点进快进、右边那一格
 * 到点进倒带、中间两格开抽屉。分界线跟着脚下这一格的宽度算，横屏竖屏都自动是四个等分，不写死 dp。
 *
 * 松手要单独挂一颗只读不吃的 `pointerInput`：`detectTapGestures` 的 onLongPress 只在到点那一下报一次，
 * 之后它自己把那根手指收到抬手，外面拿不到「松手」这个信号 —— 而快进/倒带两档都只在按住期间生效，
 * 松手就得收回去。这颗一个事件都不 consume，所以和旁边那颗 tap 检测器各读各的。
 */
fun Modifier.feedZoneGestures(
    key: Any?,
    onTap: () -> Unit,
    onZone: (FeedZone) -> Unit,
    onRelease: () -> Unit,
): Modifier = this
    .pointerInput(key) {
        awaitEachGesture {
            // requireUnconsumed=false：右侧悬浮键、底下进度条那些子节点吃掉的那一下也要看见，
            // 反正松手那一句只在真按着的时候才有效果
            awaitFirstDown(requireUnconsumed = false)
            while (true) {
                if (awaitPointerEvent().changes.none { it.pressed }) break
            }
            onRelease()
        }
    }
    .pointerInput(key) {
        detectTapGestures(
            onLongPress = { p ->
                val w = size.width
                onZone(
                    when {
                        p.x < w / 4f -> FeedZone.Left
                        p.x > w * 3f / 4f -> FeedZone.Right
                        else -> FeedZone.Middle
                    }
                )
            },
            // 这一版 detectTapGestures 的 onTap 也带按下位置，外面那颗只要"按了一下"这件事
            onTap = { onTap() },
        )
    }

/**
 * 按住倒带：一台 Media3 没有反向播放可言 —— media3-common 1.11.1 的 `Player` 里扫不到任何
 * `Reverse` 接口，负倍速官方也写明不支持，所以只能按住期间一段一段往回 seek，画面是跳帧后退的。
 * 进去先暂停（不暂停的话播放器自己还在往前走，退两步进一步就不是 2.0 倍），松手再按原来的样子放回去。
 */
@Composable
fun HoldRewindEffect(
    holding: Boolean,
    player: Player?,
    /** 每退一步报一次新位置，让外面的进度条跟着走；没有进度条那一栏可以不接 */
    onPosition: (Long) -> Unit = {},
) {
    LaunchedEffect(holding, player) {
        val p = player ?: return@LaunchedEffect
        if (!holding) return@LaunchedEffect
        val resume = p.isPlaying
        if (resume) p.pause()
        try {
            while (isActive) {
                val to = (p.currentPosition - REWIND_STEP_MS).coerceAtLeast(0L)
                p.seekTo(to)
                onPosition(to)
                if (to == 0L) break
                delay(REWIND_TICK_MS)
            }
        } finally {
            if (resume) p.play()
        }
    }
}

/**
 * 按住那一档的回显：占屏幕正中，正是那支暂停三角的位置 —— 按住的时候两个不会同时出现，
 * 谁在正中只有一个。字号/颜色照拖进度条时那一行时间（25sp、白 0.90），那是这一屏另一处瞬态回显。
 */
@Composable
fun FeedHoldHint(forward: Boolean, modifier: Modifier = Modifier) {
    Text(
        text = (if (forward) "快进 " else "快退 ") + speedLabel(HOLD_SPEED),
        color = Color.White.copy(alpha = 0.90f),
        fontSize = 25.sp,
        modifier = modifier,
    )
}
