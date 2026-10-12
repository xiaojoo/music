package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LastPlayedTest {

    private val a = "content://media/external/audio/media/10494"
    private val b = "content://media/external/video/media/98365"

    @Test
    fun `队列存出去再读回来还是那一串`() {
        assertEquals(listOf(a, b, a), LastPlayed.decode(LastPlayed.encode(listOf(a, b, a))))
    }

    @Test
    fun `空队列存成空串，读回来是空列表不是一格空字`() {
        assertEquals("", LastPlayed.encode(emptyList()))
        assertTrue(LastPlayed.decode("").isEmpty())
    }

}
