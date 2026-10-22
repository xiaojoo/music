package com.xiaojoo.lightplayer.videosource

/** 引擎那边数出来的一个文件：下标是它给的，界面重排之后不能再拿这个下标回去选文件 */
data class RawFile(val index: Int, val name: String, val size: Long, val pad: Boolean)

/** 界面上摆的一条：`index` 是回给引擎的那一个，`name` 是它自己的相对路径 */
data class MagnetFile(val index: Int, val name: String, val size: Long)

/**
 * 把种子里的文件表整理成界面上那一份：丢掉占位件和零字节，剩下的按大小从大到小。
 *
 * 为什么丢 [RawFile.pad]：BEP 47 那一种是为了让后面的文件对齐到 piece 边界而插的空文件，
 * 零字节、名字像 `___pad___`，摆出来是一行废话，选中它还会下出一个 0 字节的"视频"。
 * 排序只动展示顺序，[MagnetFile.index] 留的是引擎那个原始下标 —— 选哪一个是按下标回给的。
 */
fun magnetFiles(raw: List<RawFile>): List<MagnetFile> =
    raw.filter { !it.pad && it.size > 0 }
        .sortedByDescending { it.size }
        .map { MagnetFile(it.index, it.name, it.size) }

/** 常见视频后缀，小写比 */
private val VIDEO_EXT = listOf("mp4", "mkv", "webm", "ts", "m2ts", "m4v", "avi", "mov", "flv", "wmv")

/**
 * 默认挑哪一条：**最大的那一个"看着是视频"的**，一个视频后缀都没有才退回最大的那一条。
 *
 * 只看大小不够 —— 一部剧集的种子最大的那一条常是 `xxx.ass` 字幕之外的东西，而排在它前面的
 * `.srt`/`.nfo` 也小；反过来一张专辑里最大的 `cover.jpg` 会被当成"那条视频"。
 * 认后缀这一眼能挡掉大部分。挑错了不要紧，那一排芯片就是给他换的。
 */
fun defaultFileIndex(files: List<MagnetFile>): Int {
    val i = files.indexOfFirst { it.name.substringAfterLast('.', "").lowercase() in VIDEO_EXT }
    return if (i >= 0) i else 0
}

/** 芯片上那一格的字：文件名末段 + 大小。整条相对路径摆进芯片会撑爆那一行 */
fun magnetFileLabel(f: MagnetFile): String = f.name.substringAfterLast('/').ifBlank { f.name }
