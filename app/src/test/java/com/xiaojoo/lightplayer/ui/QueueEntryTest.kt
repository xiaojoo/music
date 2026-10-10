package com.xiaojoo.lightplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 队列页签把一份队列分成"音乐列表 / 视频列表"，分错的代价是那一整页看不见。
 * 库里的条目有 Track.isVideo 兜着，这里只管认不出来的那种 uri 形态，
 * 其中 SAF 选进来的文件在真机队列里排不到，所以按数判死。
 */
class QueueEntryTest {

    @Test
    fun `MediaStore 音频库的 uri 不算视频`() {
        assertFalse(QueueEntry.isVideoUri("content://media/external/audio/media/1234"))
    }

    @Test
    fun `MediaStore 视频库的 uri 算视频`() {
        assertTrue(QueueEntry.isVideoUri("content://media/external/video/media/1234"))
    }

    @Test
    fun `file 路径按扩展名判`() {
        assertTrue(QueueEntry.isVideoUri("file:///storage/emulated/0/Download/dts10.mkv"))
        assertTrue(QueueEntry.isVideoUri("file:///storage/emulated/0/Movies/CLIP_0001.MP4"))
        assertFalse(QueueEntry.isVideoUri("file:///storage/emulated/0/Music/01.flac"))
    }

    @Test
    fun `SAF 的 uri 路径被编码过也能认出来`() {
        assertTrue(
            QueueEntry.isVideoUri(
                "content://com.android.externalstorage.documents/document/" +
                    "primary%3ADownload%2Fmovie.mp4"
            )
        )
        assertFalse(
            QueueEntry.isVideoUri(
                "content://com.android.externalstorage.documents/document/" +
                    "primary%3AMusic%2Fsong.mp3"
            )
        )
    }

    @Test
    fun `查询参数不污染扩展名`() {
        assertFalse(QueueEntry.isVideoUri("content://media/external/audio/media/9?something=1"))
    }
}
