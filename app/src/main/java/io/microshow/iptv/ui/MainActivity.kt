package io.microshow.iptv.ui

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.microshow.iptv.R
import io.microshow.iptv.data.IptvRepository
import io.microshow.iptv.model.IptvChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var exoPlayer: ExoPlayer? = null

    private lateinit var layoutOsd: View
    private lateinit var tvOsdNumber: TextView
    private lateinit var tvOsdName: TextView
    private lateinit var tvOsdGroup: TextView
    private lateinit var tvClock: TextView
    private lateinit var layoutBuffering: View
    private lateinit var tvBufferStatus: TextView

    private lateinit var layoutChannelDrawer: View
    private lateinit var rvGroups: RecyclerView
    private lateinit var rvChannels: RecyclerView

    private lateinit var groupAdapter: GroupAdapter
    private lateinit var channelAdapter: ChannelAdapter

    private var allChannels: List<IptvChannel> = emptyList()
    private var groupedChannels: Map<String, List<IptvChannel>> = emptyMap()
    private var currentGroupNames: List<String> = emptyList()
    private var currentDisplayChannels: List<IptvChannel> = emptyList()

    private var currentChannelIndex = 0
    private var currentChannel: IptvChannel? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    // OSD 5 秒自动隐藏
    private val hideOsdRunnable = Runnable {
        layoutOsd.animate().alpha(0f).setDuration(300).withEndAction {
            layoutOsd.visibility = View.GONE
        }.start()
    }

    // 实时时钟刷新
    private val clockRunnable = object : Runnable {
        override fun run() {
            tvClock.text = clockFormat.format(Date())
            mainHandler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_main)

        initViews()
        initPlayer()
        loadChannels()
    }

    private fun initViews() {
        playerView = findViewById(R.id.player_view)
        layoutOsd = findViewById(R.id.layout_osd)
        tvOsdNumber = findViewById(R.id.tv_osd_number)
        tvOsdName = findViewById(R.id.tv_osd_name)
        tvOsdGroup = findViewById(R.id.tv_osd_group)
        tvClock = findViewById(R.id.tv_clock)
        layoutBuffering = findViewById(R.id.layout_buffering)
        tvBufferStatus = findViewById(R.id.tv_buffer_status)

        layoutChannelDrawer = findViewById(R.id.layout_channel_drawer)
        rvGroups = findViewById(R.id.rv_groups)
        rvChannels = findViewById(R.id.rv_channels)

        // 触屏点击唤出或隐藏选台菜单
        playerView.setOnClickListener {
            toggleChannelDrawer()
        }

        // 分组与频道列表初始化
        rvGroups.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = LinearLayoutManager(this)

        groupAdapter = GroupAdapter(emptyList()) { group, _ ->
            val list = groupedChannels[group] ?: emptyList()
            currentDisplayChannels = list
            channelAdapter.updateChannels(list)
        }
        rvGroups.adapter = groupAdapter

        channelAdapter = ChannelAdapter(emptyList()) { channel, index ->
            currentChannel = channel
            currentChannelIndex = allChannels.indexOf(channel).coerceAtLeast(0)
            playChannel(channel)
            hideChannelDrawer()
        }
        rvChannels.adapter = channelAdapter
    }

    private fun initPlayer() {
        exoPlayer = ExoPlayer.Builder(this)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                layoutBuffering.visibility = View.VISIBLE
                                tvBufferStatus.text = "正在缓冲直播源..."
                            }
                            Player.STATE_READY -> {
                                layoutBuffering.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                // 直播流异常中断，自动重试重连
                                currentChannel?.let { playChannel(it) }
                            }
                            Player.STATE_IDLE -> {}
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        layoutBuffering.visibility = View.VISIBLE
                        tvBufferStatus.text = "流断开，正在尝试重连..."
                        mainHandler.postDelayed({
                            currentChannel?.let { playChannel(it) }
                        }, 2500L)
                    }
                })
            }

        playerView.player = exoPlayer
    }

    private fun loadChannels() {
        // 1. 先载入本地预设
        allChannels = IptvRepository.getPresetChannels()
        applyChannels(allChannels)

        // 2. 默认起播第 1 个频道
        if (allChannels.isNotEmpty()) {
            playChannel(allChannels[0])
        }

        // 3. 异步拉取最新在线源并平滑更新
        IptvRepository.fetchOnlinePlaylist { onlineChannels ->
            runOnUiThread {
                if (onlineChannels.isNotEmpty()) {
                    allChannels = onlineChannels
                    applyChannels(onlineChannels)
                }
            }
        }
    }

    private fun applyChannels(channels: List<IptvChannel>) {
        groupedChannels = channels.groupBy { it.group }
        currentGroupNames = groupedChannels.keys.toList()

        groupAdapter.updateGroups(currentGroupNames)
        if (currentGroupNames.isNotEmpty()) {
            val firstGroup = currentGroupNames[0]
            val list = groupedChannels[firstGroup] ?: emptyList()
            currentDisplayChannels = list
            channelAdapter.updateChannels(list)
        }
    }

    private fun playChannel(channel: IptvChannel) {
        currentChannel = channel
        showOsd(channel)

        exoPlayer?.let { player ->
            layoutBuffering.visibility = View.VISIBLE
            tvBufferStatus.text = "正在连接 ${channel.name}..."

            val mediaItem = MediaItem.fromUri(Uri.parse(channel.streamUrl))
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
        }
    }

    private fun showOsd(channel: IptvChannel) {
        val index = allChannels.indexOf(channel)
        tvOsdNumber.text = String.format("%02d", if (index >= 0) index + 1 else 1)
        tvOsdName.text = channel.name
        tvOsdGroup.text = "${channel.group} · 高清直播"

        layoutOsd.alpha = 1f
        layoutOsd.visibility = View.VISIBLE

        mainHandler.removeCallbacks(hideOsdRunnable)
        mainHandler.postDelayed(hideOsdRunnable, 5000L)
    }

    private fun toggleChannelDrawer() {
        if (layoutChannelDrawer.visibility == View.VISIBLE) {
            hideChannelDrawer()
        } else {
            showChannelDrawer()
        }
    }

    private fun showChannelDrawer() {
        layoutChannelDrawer.visibility = View.VISIBLE
        layoutChannelDrawer.animate().translationX(0f).alpha(1f).setDuration(250).start()
    }

    private fun hideChannelDrawer() {
        layoutChannelDrawer.animate().alpha(0f).setDuration(200).withEndAction {
            layoutChannelDrawer.visibility = View.GONE
        }.start()
    }

    private fun nextChannel() {
        if (allChannels.isEmpty()) return
        currentChannelIndex = (currentChannelIndex + 1) % allChannels.size
        playChannel(allChannels[currentChannelIndex])
    }

    private fun prevChannel() {
        if (allChannels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex - 1 < 0) allChannels.size - 1 else currentChannelIndex - 1
        playChannel(allChannels[currentChannelIndex])
    }

    // =========================================================================
    // 电视盒子遥控器与车机按键分发
    // =========================================================================

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (layoutChannelDrawer.visibility != View.VISIBLE) {
                    prevChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (layoutChannelDrawer.visibility != View.VISIBLE) {
                    nextChannel()
                    return true
                }
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                toggleChannelDrawer()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                if (layoutChannelDrawer.visibility == View.VISIBLE) {
                    hideChannelDrawer()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
        mainHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
        mainHandler.removeCallbacks(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
