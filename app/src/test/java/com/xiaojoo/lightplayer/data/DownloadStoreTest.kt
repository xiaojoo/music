package com.xiaojoo.lightplayer.data

import com.xiaojoo.lightplayer.videosource.MagnetFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 任务表落盘那一份。全在临时目录上跑，不碰真机 */
class DownloadStoreTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "lp-test-${System.nanoTime()}")
    private val file get() = File(dir, "queue.json")

    private fun row(id: String = "a@0", mag: MagnetFile? = null) = DownloadStore.Saved(
        id = id, name = "片子.mp4", meta = "磁力 · 片子.mp4", url = "magnet:?xt=urn:btih:$id",
        dest = DownloadDest.Video, total = 6_250_000_000L, done = 123_456_789L, magnet = mag,
    )

    @Test
    fun `存进去取出来逐字相等`() {
        val r = row(mag = MagnetFile(3, "剧集/第3集.mp4", 700_000_000L))
        DownloadStore.save(file, listOf(r))
        assertEquals(listOf(r), DownloadStore.load(file))
    }

    @Test
    fun `meta 为空、没有磁力那一格也照原样回来`() {
        val r = row().copy(meta = null, magnet = null)
        DownloadStore.save(file, listOf(r))
        val back = DownloadStore.load(file).single()
        assertEquals(null, back.meta)
        assertEquals(null, back.magnet)
        assertEquals(r, back)
    }

    @Test
    fun `两档去处各存各的`() {
        DownloadStore.save(file, listOf(row("v"), row("m").copy(dest = DownloadDest.Music)))
        val back = DownloadStore.load(file)
        assertEquals(listOf(DownloadDest.Video, DownloadDest.Music), back.map { it.dest })
    }

    @Test
    fun `文件不在回空表`() {
        assertTrue(DownloadStore.load(File(dir, "nope.json")).isEmpty())
    }

    @Test
    fun `坏 JSON 回空表，不抛`() {
        dir.mkdirs()
        file.writeText("{这根本不是数组")
        assertTrue(DownloadStore.load(file).isEmpty())
    }

    @Test
    fun `缺 id 或缺 url 的那一行丢掉，别的留着`() {
        dir.mkdirs()
        file.writeText(
            """[{"name":"没号","url":"u"},{"id":"","url":"u","name":"空号"},
               {"id":"ok","url":"magnet:?x","name":"好的","dest":"v","total":-1,"done":0}]"""
        )
        val back = DownloadStore.load(file)
        assertEquals(1, back.size)
        assertEquals("ok", back[0].id)
        assertEquals("好的", back[0].name)
    }

    @Test
    fun `存空表把上一次的清掉`() {
        DownloadStore.save(file, listOf(row()))
        DownloadStore.save(file, emptyList())
        assertTrue(DownloadStore.load(file).isEmpty())
    }
}
