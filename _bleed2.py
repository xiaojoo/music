"""第二步：长按菜单的 state + Popup + MenuLine + PressableRow 本体。"""
import io, sys

SH = 'app/src/main/java/com/xiaojoo/lightplayer/ui/Shell.kt'
s = io.open(SH, encoding='utf-8').read()


def rep(old, new):
    global s
    if s.count(old) != 1:
        sys.exit('count=%d for %r' % (s.count(old), old[:160]))
    s = s.replace(old, new)


rep("""import androidx.compose.ui.layout.ContentScale""",
    """import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize""")

# 列表页的长按 state
rep("""    var query by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }""",
    """    var query by remember { mutableStateOf("") }
    var sortOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<Track?>(null) }
    var menuTop by remember { mutableStateOf(0) }
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    val dens = LocalDensity.current""")

# 菜单本体：挂在页面 Column 之前（Popup 是独立窗口，写在哪一层不影响层叠）
rep("""    Column(
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
    """    if (menuFor != null) {
        val picked = menuFor!!
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(0, menuTop),
            onDismissRequest = { menuFor = null },
            properties = PopupProperties(focusable = true)
        ) {
            Column(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .width(180.dp)
                    .clip(RoundedCornerShape(Tokens.Dimen.CardCorner))
                    .background(Tokens.CardBg)
            ) {
                MenuLine("加入播放列表") {
                    onAddToPlaylist(picked)
                    menuFor = null
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Tokens.Track))
                MenuLine("复制歌名") {
                    clip.setText(AnnotatedString(picked.name))
                    android.widget.Toast.makeText(ctx, "已复制歌名", android.widget.Toast.LENGTH_SHORT).show()
                    menuFor = null
                }
            }
        }
    }

    Column(
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
                    .clickable(onClick = onBack),""")

# PressableRow + MenuLine：整行铺满的按下色 + 长按回调带行位置
rep("""@Composable
private fun PlaylistRow(""",
    """/** 整行（含两侧留白）都是热区和按下底色；内容自己缩进，长按把行的位置交出去给菜单当锚 */
@Composable
private fun PressableRow(
    onClick: () -> Unit,
    onLongClick: ((top: Int, height: Int) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    var top by remember { mutableStateOf(0) }
    var h by remember { mutableStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                top = coords.boundsInRoot().top.toInt()
                h = coords.size.height
            }
            .background(if (pressed) Tokens.Track else Color.Transparent)
            .combinedClickable(
                interactionSource = src,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick?.let { cb -> { cb(top, h) } }
            )
            .padding(horizontal = Tokens.Dimen.ScreenPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

@Composable
private fun MenuLine(text: String, onClick: () -> Unit) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (pressed) Tokens.Track else Color.Transparent)
            .clickable(interactionSource = src, indication = ripple(), onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Tokens.InkStrong, fontSize = 14.sp)
    }
}

@Composable
private fun PlaylistRow(""")

io.open(SH, 'w', encoding='utf-8', newline='\n').write(s)
print('step2 written', len(s))
