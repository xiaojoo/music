package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 双指张开 / 捏合的门槛判定。这一段没法在真机上驱动（`sendevent` 被 selinux 拒、
 * `input motionevent` 一个事件只带一个触点），所以只拿数字过：
 * 门槛 = 短边 × 8%，这台机短边 1080 ⇒ 86.4px，竖屏横屏同一个数。
 */
class PinchCallTest {

    private val gate = 1080 * PINCH_GATE_RATIO

    @Test
    fun 张开越过门槛报一差一点报零() {
        assertEquals(1, pinchCall(300f, 390f, gate))
        assertEquals(0, pinchCall(300f, 380f, gate))
    }

    @Test
    fun 捏合是反过来的同一道门槛() {
        assertEquals(-1, pinchCall(900f, 810f, gate))
        assertEquals(0, pinchCall(900f, 820f, gate))
    }

    @Test
    fun 算的是变化量不是起点距离() {
        // 开局两指就分得很开、之后几乎不动：不该报张开
        assertEquals(0, pinchCall(2000f, 2010f, gate))
        // 开局两指挨着、之后几乎不动：也不该报捏合
        assertEquals(0, pinchCall(20f, 15f, gate))
    }

    @Test
    fun 正好压在门槛上算越过() {
        // 门槛这里取能精确表示的 100，专测"压在门槛上算越过"（>= 不是 >）：
        // 真机那道 86.4 在 float 下 4·gate - 3·gate 差一点点，测不出这一条边界
        assertEquals(1, pinchCall(0f, 100f, 100f))
        assertEquals(-1, pinchCall(400f, 300f, 100f))
        assertEquals(0, pinchCall(0f, 99.5f, 100f))
    }

    @Test
    fun 两档屏幕方向同一个门槛() {
        // 横过来短边仍是 1080：不按方向分两套门槛
        assertEquals(86.4f, gate, 0.001f)
    }
}
