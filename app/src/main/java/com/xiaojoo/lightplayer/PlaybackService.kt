package com.xiaojoo.lightplayer

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.xiaojoo.lightplayer.data.FavoriteRepository

/**
 * 通知上那颗自定义按钮（收藏）的口令和落点。
 *
 * 服务和界面在同一个进程（manifest 里根本没有 `android:process`），所以落点直接挂在这个对象上，
 * 不过 binder。界面被系统回收时这个是 null，由服务自己兜：收藏照样写盘。
 */
object NotificationCommands {
    const val FAVORITE = "com.xiaojoo.lightplayer.command.FAVORITE"

    /** 名单落盘之后叫界面重读一遍它自己那一份副本 */
    @Volatile var onFavoritesChanged: (() -> Unit)? = null
}

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val favRepo by lazy { FavoriteRepository(applicationContext) }

    /** 已经发给会话的那两颗。只有它变了才重发，否则每个进度事件都要刷一遍通知 */
    private var shownButtons: List<CommandButton> = emptyList()

    /**
     * 池里每台都在听：会话此刻挂在谁身上不由这里定，而换条目 / 换路都要重算那一排该不该长这样。
     * 听这三件事就够 —— 另外的事件（音量、缓冲那些）动不了这一排。
     */
    private val layoutListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) ||
                events.contains(Player.EVENT_TIMELINE_CHANGED) ||
                events.contains(Player.EVENT_IS_PLAYING_CHANGED)
            ) publishLayout()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 池由 PlayerPool 建；会话默认挂在**音频那一路**那台上，切到视频那一屏时由 PlayerPool.use()
        // 把会话转给池里的成员（media3 官方给的就是这条转移入口），离开时 useAudio() 再换回来
        PlayerPool.ensure(this)
        val player = PlayerPool.sessionPlayer()
        val buttons = buttonsFor(player)
        val s = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setMediaButtonPreferences(buttons)
            .build()
        shownButtons = buttons
        mediaSession = s
        PlayerPool.attach(s)
        (PlayerPool.members() + listOfNotNull(PlayerPool.audioPlayer()) + PlayerPool.onlinePlayers())
            .forEach { it.addListener(layoutListener) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_IDLE) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 会话先放，成员后放：反过来会话那侧的监听会打到已经 release 的播放器上
        mediaSession?.run {
            release()
            mediaSession = null
        }
        PlayerPool.release()
        super.onDestroy()
    }

    /* ── 通知上那两颗自定义的 ───────────────────────────────────── */

    /**
     * 那一颗按"现在放的是什么"给：
     *
     * 「收藏」只在本地条目上摆（content/file）。界面上那颗红心认的就是 uri 这一串，在线那一路播的
     * uri 是站点直链，写进名单界面里没有任何一处认得它 —— 摆一颗按下去在哪儿都看不见结果的，不如不摆。
     *
     * 中间那三颗（上一首 / 播放暂停 / 下一首）不在这里，那是 media3 自己按 `availablePlayerCommands` 配的，
     * 播放/暂停跟着状态换哪一支也是它自己算的。
     *
     * 这一份名单由 media3 追加在那三颗后面（`DefaultMediaNotificationProvider.getMediaButtons` 只认
     * SLOT_BACK / SLOT_FORWARD 两个坑）。以前后面还跟着一颗「词」，按下去在 MIUI 上拉不起界面，10-29 撤了。
     */
    private fun buttonsFor(player: Player): List<CommandButton> {
        val uri = player.currentMediaItem?.localConfiguration?.uri
        val out = mutableListOf<CommandButton>()
        if (uri != null && (uri.scheme == "content" || uri.scheme == "file")) {
            out += heart(uri.toString() in favRepo.all())
        }
        return out
    }

    private fun heart(filled: Boolean): CommandButton = CommandButton.Builder()
        .setSessionCommand(command(NotificationCommands.FAVORITE))
        .setIconResId(if (filled) R.drawable.notif_favorite_filled else R.drawable.notif_favorite)
        .setDisplayName("收藏")
        .build()

    /** extras 给一份空的：会话那侧比的是「action + extras」两样都一致（[SessionCommand.equals]），两边都得是空的 */
    private fun command(action: String) = SessionCommand(action, Bundle())

    /** 重算一遍那两颗，和已经发出去的不一样才发（[CommandButton.equals] 比的是内容） */
    private fun publishLayout() {
        val s = mediaSession ?: return
        val next = buttonsFor(s.player)
        if (next == shownButtons) return
        shownButtons = next
        s.setMediaButtonPreferences(next)
    }

    /* ── 那两颗按下去做什么 ──────────────────────────────────────── */

    private val callback = object : MediaSession.Callback {

        /** 自定义口令要在连接时就报出去，不然通知那颗按下去会被会话按"没这回事"丢掉 */
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(command(NotificationCommands.FAVORITE))
                    .build()
            )
            .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == NotificationCommands.FAVORITE) toggleFavorite(session)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

    /**
     * 收藏由服务这边落盘（界面在不在都是同一个结果），写完叫界面重读它那一份副本，
     * 再把那一颗重发一次 —— 心形要当场从描边变实心。
     */
    private fun toggleFavorite(session: MediaSession) {
        val uri = session.player.currentMediaItem?.localConfiguration?.uri ?: return
        if (uri.scheme != "content" && uri.scheme != "file") return
        favRepo.toggle(uri.toString())
        NotificationCommands.onFavoritesChanged?.invoke()
        publishLayout()
    }
}
