"""四处调整：卡片名单行省略、列表行距收小(×0.5)、菜单窄一点(×0.83)+分隔线淡一半、搜索框移到全部播放上方；子页藏底栏。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
plans = {}


def edit(path, pairs):
    s = plans.get(path) or io.open(path, encoding='utf-8').read()
    for old, new in pairs:
        if s.count(old) != 1:
            sys.exit('count=%d in %s for:\n%r' % (s.count(old), path, old[:160]))
        s = s.replace(old, new)
    plans[path] = s


# 1) 卡片歌名单行 + 省略号
edit(SH, [
    ("import androidx.compose.ui.text.style.TextAlign",
     "import androidx.compose.ui.text.style.TextAlign\nimport androidx.compose.ui.text.style.TextOverflow"),
    ("""        CoverBox(track = coverOf, side = 112.dp)
        Text(text = label, color = Tokens.InkStrong, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))""",
     """        CoverBox(track = coverOf, side = 112.dp)
        // 卡片位只给一行：超出省略号，全名进「最近播放」列表页看
        Text(
            text = label,
            color = Tokens.InkStrong,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )"""),
])

# 3) 菜单窄一点 + 分隔线淡一半
s = plans.get(SH) or io.open(SH, encoding='utf-8').read()
assert s.count('.width(180.dp)') == 2, s.count('.width(180.dp)')
plans[SH] = s.replace('.width(180.dp)', '.width(150.dp)')

edit(SH, [
    ("""private fun MenuDivider() {
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track))
}""",
     """private fun MenuDivider() {
    // 与弹框底色各 50%（Track 188 / CardBg 250 -> 合成 219）
    Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track.copy(alpha = 0.5f)))
}"""),
    # 2) 行距：把纵向内衬做成参数，列表页用一半
    ("""private fun PressableRow(
    onClick: () -> Unit,
    onLongClick: ((top: Int, height: Int) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {""",
     """private fun PressableRow(
    onClick: () -> Unit,
    onLongClick: ((top: Int, height: Int) -> Unit)? = null,
    verticalPad: Dp = 12.dp,
    content: @Composable RowScope.() -> Unit
) {"""),
    ("            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 12.dp),\n        verticalAlignment = Alignment.CenterVertically\n    ) { content() }",
     "            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = verticalPad),\n        verticalAlignment = Alignment.CenterVertically\n    ) { content() }"),
    ("""                    PressableRow(
                        onClick = { onPlayList(visible, i) },
                        onLongClick = { top, height -> menuFor = t; menuTop = top + height }
                    ) {""",
     """                    PressableRow(
                        onClick = { onPlayList(visible, i) },
                        onLongClick = { top, height -> menuFor = t; menuTop = top + height },
                        verticalPad = 6.dp
                    ) {"""),
])

# 4) 搜索框挪到「全部播放」上方
s = plans[SH]
L = s.split('\n')
blk6 = lambda i: chr(10).join(L[i:i + 8])
sb = next(i for i, l in enumerate(L) if l.strip() == 'Box(' and 'fillMaxWidth' in blk6(i) and '.padding(bottom = 10.dp)' in blk6(i))
search = L[sb:sb + 40]
assert 'RoundedCornerShape(20.dp)' in '\n'.join(search) and 'BasicTextField' in '\n'.join(search), 'search block'
assert search[39].strip() == '}', repr(search[39])
rest = L[:sb] + L[sb + 40:]
rb = next(i for i, l in enumerate(rest) if l.strip() == 'Row(' and 'vertical = 10.dp' in '\n'.join(rest[i:i + 6]))
assert 'PlayAllButton' in '\n'.join(rest[rb:rb + 27]), 'playall block'
plans[SH] = '\n'.join(rest[:rb] + search + [''] + rest[rb + 1:])

# 4b) 底栏没了，页面自己吃底部安全区
edit(SH, [
    ("""    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Tokens.Dimen.IconHit)
                    .clickable(onClick = onBack),""",
     """    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.ListBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Tokens.Dimen.ScreenPad)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(Tokens.Dimen.IconHit)
                    .clickable(onClick = onBack),"""),
])

for p, v in plans.items():
    io.open(p, 'w', encoding='utf-8', newline='\n').write(v)
    print('wrote', p, len(v))
