package com.xiaojoo.lightplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「这条 uri 是不是视频」的唯一判据。历史/存档里存的标记可能是错的，分栏全靠它兜住。 */
class VideoUriTest {

    @Test
    fun 媒体库的视频uri没有扩展名也要认出来() {
        // 真机上那 7 条走错栏的就是这一形：整条路径里没有点号，只看扩展名会判成音频
        assertTrue(isVideoUri("content://media/external/video/media/66022"))
        assertFalse(isVideoUri("content://media/external/audio/media/66022"))
    }

    @Test
    fun 选文件进来的按扩展名认() {
        assertTrue(isVideoUri("content://com.android.externalstorage.documents/document/primary%3ADM%2Fclip.mp4"))
        assertTrue(isVideoUri("file:///storage/emulated/0/Movie/a.mkv"))
        assertTrue(isVideoUri("file:///x/y/z.M2TS"))          // 大小写都要吃
    }

    @Test
    fun 带查询串的取问号之前那一段() {
        // 不先切掉 ?... 的话，"最后一个点"落在查询串上，扩展名就成了 b
        assertTrue(isVideoUri("content://x/y.mp4?token=a.b"))
        assertFalse(isVideoUri("content://media/external/audio/media/9?x=1"))
    }

    @Test
    fun 没扩展名又不是视频库的一律不算视频() {
        assertFalse(isVideoUri("content://media/external/audio/media/9"))
        assertFalse(isVideoUri("content://com.android.media/mediasession/"))
        assertFalse(isVideoUri(""))
    }

    @Test
    fun 音频扩展名不许被误判成视频() {
        assertFalse(isVideoUri("file:///sdcard/Music/歌.flac"))
        assertFalse(isVideoUri("file:///sdcard/Music/歌.mp3"))
    }
}
