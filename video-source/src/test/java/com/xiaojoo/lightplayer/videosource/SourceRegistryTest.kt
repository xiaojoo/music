package com.xiaojoo.lightplayer.videosource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SourceRegistryTest {

    private val bili = FakeSource(id = "bili", hosts = setOf("bili.example"))
    private val tube = FakeSource(id = "tube", hosts = setOf("youtube.example"))

    @Test
    fun `源 id 重复直接拒绝注册`() {
        try {
            SourceRegistry(listOf(bili, FakeSource(id = "bili")))
            fail("应该抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("bili"))
        }
    }

    @Test
    fun `粘进来的链接优先问注册域命中的源`() {
        val r = SourceRegistry(listOf(bili, tube))
        val hit = r.sourcesForUrl("https://www.bili.example/video/BV1")
        assertEquals("bili", hit.first().id)
        assertEquals(2, hit.size)
    }

    @Test
    fun `未知域名时启用的源都要问一遍`() {
        val r = SourceRegistry(listOf(bili, tube))
        assertEquals(listOf("bili", "tube"), r.sourcesForUrl("https://whatever.example/x").map { it.id })
        assertEquals(listOf("bili", "tube"), r.sourcesForUrl("不是一链接").map { it.id })
    }

    @Test
    fun `关掉一个源之后所有入口都不再问它`() {
        val r = SourceRegistry(listOf(bili, tube))
        r.setEnabled("tube", false)
        assertFalse(r.isEnabled("tube"))
        assertEquals(listOf("bili"), r.searchSources().map { it.id })
        assertEquals(listOf("bili"), r.sourcesForUrl("https://youtube.example/watch?v=1").map { it.id })
        assertTrue(r.resolveSource("tube") == null)
    }

    @Test
    fun `没有声明能力的源不会被问`() {
        val noSearch = FakeSource(id = "raw", capabilities = setOf(Capability.Resolve))
        val r = SourceRegistry(listOf(noSearch))
        assertTrue(r.searchSources().isEmpty())
        assertTrue(r.resolveSource("raw") != null)
    }

    @Test
    fun `没听过的源 id 要报错而不是静默`() {
        val r = SourceRegistry(listOf(bili))
        try {
            r.setEnabled("ghost", false)
            fail("应该抛 IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("ghost"))
        }
    }

    @Test
    fun `主机名提取不依赖 Uri`() {
        assertEquals("a.example", hostOf("https://a.example:8443/p?x=1"))
        assertEquals("a.example", hostOf("http://a.example"))
        assertEquals("abc", hostOf("abc"))
        assertTrue(hostOf("") == null)
    }
}
