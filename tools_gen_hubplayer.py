import re, io

# 从 javap 的 androidx.media3.common.Player 抽象方法表生成 HubPlayer 的转发实现。
# 路由规则写死在这里，改接口时重跑本脚本即可（tools_gen_hubplayer.py）。
PRIM = {
    "void": "Unit", "int": "Int", "long": "Long", "float": "Float", "double": "Double",
    "boolean": "Boolean", "byte": "Byte", "short": "Short",
    "java.lang.String": "String", "java.lang.CharSequence": "CharSequence",
    "java.lang.Object": "Any", "java.util.List": "List", "java.util.Set": "Set",
}
# Player 上标了 @Nullable 的返回值（javap 不显示注解，这份表按 1.11.1 的源码逐个核过）
NULLABLE_RET = {
    "getMetadata", "getPlayerError", "getCurrentMediaItem", "getAudioFormat",
    "getCurrentTrackGroup", "getLoudnessInfo", "getCurrentManifest",
}
QUEUE = {
    "setMediaItem", "setMediaItems", "addMediaItem", "addMediaItems", "removeMediaItem",
    "removeMediaItems", "clearMediaItems", "moveMediaItem", "moveMediaItems",
    "replaceMediaItem", "replaceMediaItems", "reorderMediaItem", "reorderMediaItems",
}
CONFIG = {
    "setAudioAttributes", "setHandleAudioBecomingNoisy", "setPlaybackParameters",
    "setPlaybackSpeed", "setRepeatMode", "setShuffleModeEnabled", "setWakeMode",
    "setTrackSelectionParameters", "setVolume", "setPlaybackSuppressionEnabled",
    "setDeviceVolume", "adjustDeviceVolume", "setDeviceMuted", "setTunnelingEnabled",
}
LISTENERS = {"addListener", "removeListener"}  # 这两个手写（要按成员分道门，见 HubPlayer.gate）
# 生命周期：释放必须打到每个成员，否则池里会漏一个活着的解码器
LIFECYCLE = {"release"}
# Java 侧标了 @Nullable 的参数：Kotlin 覆写时必须写成可空
NULLABLE_ARG = {
    "setVideoSurface", "clearVideoSurface", "setVideoSurfaceHolder", "clearVideoSurfaceHolder",
    "setVideoSurfaceView", "clearVideoSurfaceView", "setVideoTextureView", "clearVideoTextureView",
}
SURFACE = {
    "setVideoSurface", "setVideoSurfaceHolder", "setVideoSurfaceView", "setVideoTextureView",
    "clearVideoSurface", "clearVideoSurfaceHolder", "clearVideoSurfaceView",
    "clearVideoTextureView", "getSurface", "getSurfaceSize",
}

def jtype(t):
    t = t.strip()
    m = re.match(r"^(java\.util\.(List|Set))<(.*)>$", t)
    if m:
        return "%s<%s>" % (PRIM[m.group(1)], jtype(m.group(3)))
    t = t.replace("[]", "").replace("$", ".")
    parts = t.split(".")
    # 嵌套类型要连着父类名一起留（Player.Listener、Player.Command.Commands）
    if len(parts) > 1 and parts[-2][:1].isupper():
        return ".".join(parts[-2:])
    return PRIM.get(t, parts[-1])

def params(inner):
    """inner 是括号里的原始串（不含括号）"""
    if not inner.strip():
        return []
    out, depth, cur = [], 0, ""
    for ch in inner:
        if ch == "<":
            depth += 1
        elif ch == ">":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur); cur = ""
        else:
            cur += ch
    out.append(cur)
    return [p.strip() for p in out]

lines = io.open("build/_gen/player.txt", encoding="utf-8").read().splitlines()
body, seen = [], set()
for ln in lines:
    m = re.match(r"^(.*?) ([A-Za-z0-9_]+)\((.*)\);", ln)
    if not m:
        print("跳过:", ln)
        continue
    ret, name, argstr = m.group(1), m.group(2), m.group(3)
    ps = params(argstr)
    # Kotlin 不允许同名同参签名的重复覆写；javap 的重载靠参数个数区分
    args = []
    for i, p in enumerate(ps):
        pt = p.rsplit(" ", 1)
        t, n = (pt[0], pt[1]) if len(pt) == 2 else (pt[0], "p%d" % i)
        ty = jtype(t)
        if name in NULLABLE_ARG and not ty.endswith("?"):
            ty += "?"
        args.append((ty, "a%d" % i if n in ("in", "object", "fun") else n))
    sig_in = ", ".join("%s: %s" % (n, t) for t, n in args)
    call = ", ".join(n for _, n in args)
    r = jtype(ret)
    if r != "Unit" and name in NULLABLE_RET:
        r += "?"
    if name in LISTENERS:
        continue  # 这两个手写：事件要按成员分道门，只有当前成员的那道才转给会话（HubPlayer.gate）
    if name in QUEUE or name in CONFIG or name in LIFECYCLE:
        body.append("    override fun %s(%s)%s { members.forEach { it.%s(%s) } }" %
                    (name, sig_in, "" if r == "Unit" else ": " + r, name, call))
    else:
        body.append("    override fun %s(%s)%s = current.%s(%s)" %
                    (name, sig_in, "" if r == "Unit" else ": " + r, name, call))
    seen.add(name)

# 类头（imports / 字段 / use() / gate() / resync() / init / 手写的 addListener+removeListener）
# 不在本脚本里存第二份：从现有文件取"第一条生成的转发之前"那一段原样留着，
# 升级 media3 后重跑本脚本只换转发体，手写那扇门不会被冲掉。
TARGET = "app/src/main/java/com/xiaojoo/lightplayer/HubPlayer.kt"
FIRST_GEN = "    override fun getApplicationLooper"
old = io.open(TARGET, encoding="utf-8").read().splitlines(True)
cut = next(i for i, ln in enumerate(old) if ln.startswith(FIRST_GEN))
head = "".join(old[:cut]).rstrip("\n")
out = head + "\n" + "\n".join(body) + "\n}\n"
io.open(TARGET, "w", encoding="utf-8", newline="\n").write(out)
print("生成 %d 个转发，队列广播 %d 个，配置广播 %d 个" %
      (len(body), len(QUEUE & seen), len(CONFIG & seen)))
print("画面类走当前成员:", sorted(SURFACE & seen))
