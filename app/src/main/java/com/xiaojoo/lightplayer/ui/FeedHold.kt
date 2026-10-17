package com.xiaojoo.lightplayer.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.sqrt

/** 视频那一屏长按落在屏宽四等分的哪一格。Middle 不进「按住」那一档：它到点开抽屉，没有松手要收回的东西。 */
enum class FeedZone { Left, Middle, Right }

/** 按住快进、按住倒带共用这一个倍率 */
const val HOLD_SPEED = 2f

/**
 * 双指张开 = 进全屏，双指捏合 = 出全屏（他 10-17 点的，在线、本地两栏都要）。
 *
 * 判据只看**两指距离的变化**，起点距离不算数 —— 两个人手指头开局分多开因人而异。
 * 越过 [PINCH_GATE_RATIO]（短边的 8%，这台机两档都是 86px，和横滑跳档同一把比例）报一次，
 * 一次手势只报一次。
 *
 * 两指一落地就把位移全部吃掉：不然 `VerticalPager` 会拿其中一指当竖滑，捏到一半页面自己滑走。
 * 只有一指时一个事件都不 consume，翻页、单击、长按那几档照旧。
 *
 * **键要带上 fullscreen**：那颗开关翻的时候 pointerInput 不重开就一直拿着旧闭包，
 * 里面那个 fullscreen 永远停在按之前那一档 —— 本地那一栏在单击那颗上真机撞到过（见 VideoFeed 那段注释）。
 */
fun Modifier.feedPinchFullscreen(
    key: Any?,
    onSpread: () -> Unit,
    onPinch: () -> Unit,
): Modifier = pointerInput(key) {
    val gate = minOf(size.width, size.height) * PINCH_GATE_RATIO
    awaitEachGesture {
        var start = -1f
        var fired = false
        while (true) {
            val e = awaitPointerEvent()
            val down = e.changes.filter { it.pressed }
            if (down.size >= 2) {
                val dx = down[0].position.x - down[1].position.x
                val dy = down[0].position.y - down[1].position.y
                val d = sqrt(dx * dx + dy * dy)
                if (start < 0f) start = d
                if (!fired) when (pinchCall(start, d, gate)) {
                    1 -> { onSpread(); fired = true }
                    -1 -> { onPinch(); fired = true }
                }
                down.forEach { it.consume() }
            }
            if (down.isEmpty()) break
        }
    }
}

/** 双指捏合/张开认的门槛：短边的 8%（这台机竖屏 86px、横屏也是 86px） */
const val PINCH_GATE_RATIO = 0.08f

/**
 * 这一次双指该报什么：1 = 张开、-1 = 捏合、0 = 还没越过门槛。
 * 只看两指距离相对**起手那一刻**的变化量，起点分多开不算数。
 *
 * 单独抽出来是为了能测：这台机注入不了真双指（`sendevent` 被 selinux 拒、
 * `input motionevent` 一个事件只带一个触点 —— 真机试过，Compose 一次都没收到两根手指），
 * 所以判定这一段只能拿数字过，见 PinchCallTest。
 */
internal fun pinchCall(start: Float, now: Float, gate: Float): Int {
    val moved = now - start
    return if (moved >= gate) 1 else if (-moved >= gate) -1 else 0
}

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
 *
 * `onDoubleTap` 给 null 时那颗检测器根本不装 —— 装了以后 `detectTapGestures` 要等满系统的双击时限
 * 才敢报单击，单击出条子就会慢半拍。所以只在真的要多击那一档（全屏）才传进来。
 */
fun Modifier.feedZoneGestures(
    key: Any?,
    onTap: () -> Unit,
    onZone: (FeedZone) -> Unit,
    onRelease: () -> Unit,
    /** 双击那一下：只在需要它的那一档传，其余时候留 null 免得拖慢单击 */
    onDoubleTap: (() -> Unit)? = null,
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
            // 双击：装了它，第一下就不再当单击报（检测器等满双击时限才决定是"一次单击"还是"一次双击"）
            onDoubleTap = onDoubleTap?.let { cb -> { cb() } },
        )
    }

/**
 * 屏幕两头各 1/4 的竖滑：左边调亮度、右边调声音（他 10-16 点的，配参考图那两块浮标）。
 *
 * 必须在 **Main pass** 里把竖向位移 consume 掉：翻页那台 VerticalPager 是这一格的**父节点**，
 * Main pass 子节点先拿到事件，我们一 consume，pager 拿到的可用位移就是 0。
 * 代价说清：起手落在这两头 1/4 里的竖滑**不再翻页**，中间那一半照旧；横向为主的位移不吃，
 * 所以单击切播停、原地长按快进/倒带那两档一个没受影响。
 *
 * 起手门槛给的是**屏高的 8%**（他 10-16 点的：得滑够一段距离才动手，免得擦一下就把音量/亮度带走）。
 * 这台机 1080 高 ⇒ 86.4px；原来用系统 touchSlop（22px）就等于一碰就动。
 */
fun Modifier.edgeValueDrag(
    key: Any?,
    /** 关掉就一个事件都不吃：翻页那台照常收到竖向位移。这一屏只在全屏那一档接管 */
    enabled: Boolean,
    /** 越过门槛那一刻报一次落在哪一边：-1 左（亮度）、1 右（声音）。外面拿它取当前值当起点 */
    onStart: (Int) -> Unit,
    /** 每一帧的增量，已经换算成"整屏高 = 100%"，往上为正 */
    onStep: (Float) -> Unit,
    onEnd: () -> Unit,
): Modifier = pointerInput(key, enabled) {
    if (!enabled) return@pointerInput
    val slop = size.height * 0.08f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat()
        val side = when {
            down.position.x < w / 4f -> -1
            down.position.x > w * 3f / 4f -> 1
            else -> 0
        }
        var acc = 0f
        var dragging = false
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            if (side == 0) continue
            val d = c.positionChange()
            // 竖向为主就从**第一帧**吃掉位移，不等自己的 slop：pager 那颗的 slop 和这条一样长，
            // 等越过才吃等于两边同时起步抢，实测就是它赢（一记竖滑把这一屏换成了下一条）
            if (dragging || abs(d.y) >= abs(d.x)) c.consume()
            if (!dragging) {
                acc += d.y
                if (abs(acc) <= slop) continue
                dragging = true
                onStart(side)
            }
            onStep(-d.y / size.height)
        }
        if (dragging) onEnd()
    }
}

/** 横滑一次跳多少：和条子上那两颗 ±10 秒同一个数 */
const val SWIPE_SEEK_MS = 10_000L

/** 收/放系统那两条栏和转屏之间隔这一口气：MIUI 那两条栏的动画约 200ms 走完，给足 260ms */
const val BAR_SETTLE_MS = 260L

/**
 * 全屏那一档、中间那一半的横滑：往右跳进 10 秒、往左跳退 10 秒（他 10-16 点的）。
 *
 * 一次滑动只算一档：越过门槛就锁上，抬手之前再怎么滑也不叠加。
 * 门槛 = **屏宽的 8%**（这台机 2400 宽 ⇒ 192px），并且要求横向位移不小于竖向位移，
 * 所以斜着往下划仍然走翻页那条路 —— 没锁上之前这一颗对位移一个不吃。
 * 左右两头各 1/4 不参与：那两段归亮度/声音的竖滑。
 */
fun Modifier.feedSeekSwipe(
    key: Any?,
    /** 只在全屏那一档接管（非全屏那档横滑本来就不做别的事，但他点的是"只在全屏"） */
    enabled: Boolean,
    /** 跳进为正、跳退为负，单位毫秒 */
    onSeekBy: (Long) -> Unit,
): Modifier = pointerInput(key, enabled) {
    if (!enabled) return@pointerInput
    val gate = size.width * 0.08f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val w = size.width.toFloat()
        if (down.position.x < w / 4f || down.position.x > w * 3f / 4f) return@awaitEachGesture
        var ax = 0f
        var ay = 0f
        var fired = false
        while (true) {
            val e = awaitPointerEvent()
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            val d = c.positionChange()
            ax += d.x
            ay += d.y
            if (!fired && abs(ax) >= gate && abs(ax) >= abs(ay)) {
                fired = true
                onSeekBy(if (ax > 0f) SWIPE_SEEK_MS else -SWIPE_SEEK_MS)
            }
            if (fired) c.consume()
        }
    }
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

/** 正中那一格瞬态回显的大小：按住那对双三角、横滑那圈「10 秒」共用一个数 */
const val FEED_TOAST_DP = 60f

/**
 * 按住那一档的回显：占屏幕正中，正是那支暂停三角的位置 —— 按住的时候两个不会同时出现，
 * 谁在正中只有一个。10-16 从「快进 2.0x」那一行字换成主流播放器那对双三角：
 * 往右 = 快进、往左 = 快退（就是条子上那两颗同形，白 0.90 沿用另一处瞬态回显的档）。
 */
@Composable
fun FeedHoldHint(forward: Boolean, modifier: Modifier = Modifier) {
    LineIcon(
        kind = if (forward) IconKind.Next else IconKind.Prev,
        modifier = modifier,
        size = FEED_TOAST_DP.dp,
        color = Color.White.copy(alpha = 0.90f),
    )
}

/**
 * 横滑跳那一档的回显：同一支双三角 + 一个秒数（他 10-16 点的「三角的方向加个数字，快进了多少秒」）。
 * 数字摆在**三角指的那一侧**：▶▶ 10 / 10 ◀◀。三角仍钉在正中那一格不动（真机墨中心 1200,540），
 * 数字用 offset 挂出去 —— 整组居中会把三角推偏 19dp，那一格他刚点过位置。
 * 偏移定在 52dp：真机按段拆开量到数字的墨（65x56px）与三角的墨之间留 **46px = 16.7dp** 的缝、
 * 两个数字之间 15px，左右两档镜像逐位相同（各 6709 个白墨点）。往外多给 10dp 缝就涨到 73px
 * （26.5dp），整组散开，不像一支回显了。
 */
@Composable
fun FeedSeekToast(forward: Boolean, seconds: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        LineIcon(
            kind = if (forward) IconKind.Next else IconKind.Prev,
            size = FEED_TOAST_DP.dp,
            color = Color.White.copy(alpha = 0.90f),
        )
        val side = if (forward) 1f else -1f
        Text(
            text = seconds.toString(),
            color = Color.White.copy(alpha = 0.90f),
            fontSize = 34.sp,
            modifier = Modifier.offset(x = (52f * side).dp),
        )
    }
}
