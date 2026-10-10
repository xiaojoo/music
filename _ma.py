"""MainActivity 的 App 尾巴重排：底栏在子页里让位、加歌弹框回到顶层（之前被误挪进子页分支里）。"""
import io, sys

P = 'app/src/main/java/com/xiaojoo/lightplayer/MainActivity.kt'
L = io.open(P, encoding='utf-8').read().split('\n')

assert L[146].strip() == 'if (page != null) {', repr(L[146])
assert L[147].strip().startswith('pendingAdd?.let'), repr(L[147])
dlg = L[147:162]
assert dlg[-1].strip() == '}', repr(dlg[-1])
assert 'AddToPlaylistDialog' in '\n'.join(dlg)

i_when = next(i for i, l in enumerate(L) if l.strip() == 'when (tab) {')
assert L[i_when - 1].strip() == 'Box(modifier = Modifier.weight(1f)) {', repr(L[i_when - 1])
i_end_when = next(i for i, l in enumerate(L[i_when:], i_when) if l == '            }')
when_block = L[i_when:i_end_when + 1]
assert 'MineScreen(' in '\n'.join(when_block) and 'PlayerScreen(' in '\n'.join(when_block)

new = [''] + '''    // 子页（最近播放/本地歌曲/视频）盖在壳层上；进子页时底栏让位，出口只有返回箭头和系统返回
    BackHandler(enabled = page != null) { page = null }
    val (pageTitle, pageUnit, pageList) = when (page) {
        "history" -> Triple("\u6700\u8fd1\u64ad\u653e", "\u6761", history)
        "videos" -> Triple("\u89c6\u9891", "\u4e2a", videoTracks)
        else -> Triple("\u672c\u5730\u6b4c\u66f2", "\u9996", audioTracks)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            if (page != null) {
                TrackListPage(
                    title = pageTitle,
                    unit = pageUnit,
                    searchHint = "\u641c\u7d22" + pageTitle,
                    sort = sortMode,
                    onSortChange = { m -> sortMode = m; ListSort.save(context, m) },
                    tracks = pageList,
                    permissionGranted = mediaGranted,
                    onRequestPermission = onAskMediaPermission,
                    onBack = { page = null },
                    // 从列表页点歌要顺手关掉这页，否则切到音乐页还被它盖着
                    onPlayList = { tracks, index ->
                        page = null
                        startPlaylist(tracks, index)
                    },
                    onAddToPlaylist = { pendingAdd = it }
                )
            } else {'''.split('\n') + when_block + ['            }'] + '''        }
        if (page == null) {
            BottomBar(tab = tab, onSelect = { tab = it })
        }
    }

'''.split(chr(10)) + [''] + dlg + ['}']

out = L[:146] + new + ['']
io.open(P, 'w', encoding='utf-8', newline='\n').write('\n'.join(out))
print('rewrote App tail: lines', len(L), '->', len(out))
