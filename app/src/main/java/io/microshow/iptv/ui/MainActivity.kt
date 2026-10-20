package io.microshow.iptv.ui

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.microshow.iptv.R
import io.microshow.iptv.data.IptvRepository
import io.microshow.iptv.epg.TvgProvider
import io.microshow.iptv.model.IptvChannel
import io.microshow.iptv.udpxy.UdpxyHelper
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * IPTV 核心播放界面 (LibVLC 核心引擎 + Tvheadend / MPEG-TS 深度适配)
 * - 核心机制：基于 VideoLAN 官方底层 LibVLC (libvlc.so + libvlcjni.so)；
 * - 针对 Tvheadend 特征流（:8168/:9981/stream/profile=pass/audio/mp2t）定制 TS 解封装器与 VLC 原生 User-Agent；
 * - 软硬解双重自愈容灾：当车机或盒子 MediaCodec 硬解对 MPEG-2/MP2 音频报错时，毫秒级自动回退至 LibVLC 软解；
 * - 组播代理：内置 udpxy 转换；
 * - 画面比例：16:9 / 4:3 / 全屏拉伸 / 自适应。
 */
class MainActivity : AppCompatActivity() {

    private lateinit var vlcVideoLayout: VLCVideoLayout
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private lateinit var layoutOsd: View
    private lateinit var tvOsdNumber: TextView
    private lateinit var tvOsdName: TextView
    private lateinit var tvOsdEpg: TextView
    private lateinit var tvOsdGroup: TextView
    private lateinit var tvAspectRatio: TextView
    private lateinit var tvBtnSettings: TextView
    private lateinit var tvClock: TextView
    private lateinit var layoutBuffering: View
    private lateinit var tvBufferStatus: TextView
    private lateinit var layoutEmptyState: View

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

    // 容灾重试状态：记录当前频道是否已尝试过降级软解
    private var hasTriedSoftwareFallback = false

    // 画幅比例模式（16:9、4:3、全屏拉伸、自适应）
    private val aspectRatios = arrayOf("16:9", "4:3", "拉伸", "自适应")
    private var currentAspectIndex = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private val clockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val TAG = "IptvPlayer"
        const val VLC_USER_AGENT = "VLC/3.0.18 (Linux; Android)"
    }

    private val hideOsdRunnable = Runnable {
        layoutOsd.animate().alpha(0f).setDuration(300).withEndAction {
            layoutOsd.visibility = View.GONE
        }.start()
    }

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
        initLibVlc()
        loadPlaylistAndEpg()
    }

    private fun initViews() {
        vlcVideoLayout = findViewById(R.id.vlc_video_layout)
        layoutOsd = findViewById(R.id.layout_osd)
        tvOsdNumber = findViewById(R.id.tv_osd_number)
        tvOsdName = findViewById(R.id.tv_osd_name)
        tvOsdEpg = findViewById(R.id.tv_osd_epg)
        tvOsdGroup = findViewById(R.id.tv_osd_group)
        tvAspectRatio = findViewById(R.id.tv_aspect_ratio)
        tvBtnSettings = findViewById(R.id.tv_btn_settings)
        tvClock = findViewById(R.id.tv_clock)
        layoutBuffering = findViewById(R.id.layout_buffering)
        tvBufferStatus = findViewById(R.id.tv_buffer_status)
        layoutEmptyState = findViewById(R.id.layout_empty_state)

        layoutChannelDrawer = findViewById(R.id.layout_channel_drawer)
        rvGroups = findViewById(R.id.rv_groups)
        rvChannels = findViewById(R.id.rv_channels)

        findViewById<View>(R.id.btn_config_source).setOnClickListener {
            showSettingsDialog()
        }

        tvBtnSettings.setOnClickListener {
            showSettingsDialog()
        }

        tvAspectRatio.setOnClickListener {
            cycleAspectRatio()
        }

        vlcVideoLayout.setOnClickListener {
            toggleChannelDrawer()
        }

        rvGroups.layoutManager = LinearLayoutManager(this)
        rvChannels.layoutManager = LinearLayoutManager(this)

        groupAdapter = GroupAdapter(emptyList()) { group, _ ->
            val list = groupedChannels[group] ?: emptyList()
            currentDisplayChannels = list
            channelAdapter.updateChannels(list)
        }
        rvGroups.adapter = groupAdapter

        channelAdapter = ChannelAdapter(emptyList()) { channel, _ ->
            currentChannel = channel
            currentChannelIndex = allChannels.indexOf(channel).coerceAtLeast(0)
            hasTriedSoftwareFallback = false
            playChannel(channel, useSoftwareDecoder = false)
            hideChannelDrawer()
        }
        rvChannels.adapter = channelAdapter
    }

    /**
     * 初始化 VideoLAN LibVLC 原生播放引擎
     * 全面支持 Tvheadend MPEG-TS 流与 HLS 协议，移除破坏 TS 时钟同步的 clock-jitter=0 选项
     */
    private fun initLibVlc() {
        val options = arrayListOf(
            "--http-user-agent=$VLC_USER_AGENT",
            "--network-caching=2500",
            "--live-caching=2000",
            "--file-caching=2000",
            "--sout-mux-caching=2000",
            "--codec=mediacodec_ndk,mediacodec_jni,all", // 优先硬件加速，失败自动降级
            "--http-reconnect",
            "--ts-trust-pcr",
            "--no-sub-autodetect-file",
            "-vvv"
        )
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer?.attachViews(vlcVideoLayout, null, false, false)

        applyAspectRatio(aspectRatios[currentAspectIndex])

        mediaPlayer?.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    layoutBuffering.visibility = View.VISIBLE
                    tvBufferStatus.text = "正在缓冲直播流... ${event.buffering.toInt()}%"
                }
                MediaPlayer.Event.Playing -> {
                    layoutBuffering.visibility = View.GONE
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.w(TAG, "LibVLC 播放异常")
                    handlePlaybackError()
                }
                MediaPlayer.Event.EndReached -> {
                    // Tvheadend 或 HLS 断开时自动重连
                    currentChannel?.let { playChannel(it, useSoftwareDecoder = false) }
                }
            }
        }
    }

    /**
     * 判断是否为 Tvheadend 特征流或 MPEG-TS 传输流
     */
    private fun isTvheadendOrTs(url: String): Boolean {
        return url.contains(":8168", ignoreCase = true) ||
                url.contains(":9981", ignoreCase = true) ||
                url.contains("/stream/", ignoreCase = true) ||
                url.contains("profile=pass", ignoreCase = true) ||
                url.endsWith(".ts", ignoreCase = true) ||
                url.contains(".ts?", ignoreCase = true) ||
                url.startsWith("rtp://", ignoreCase = true) ||
                url.startsWith("udp://", ignoreCase = true)
    }

    private fun playChannel(channel: IptvChannel, useSoftwareDecoder: Boolean = false) {
        currentChannel = channel
        showOsd(channel)

        val vlc = libVLC ?: return
        val player = mediaPlayer ?: return

        // 组播代理转换（rtp/udp 经由 udpxy 转为 http）
        val convertedUrl = UdpxyHelper.convertUrl(this, channel.streamUrl)
        val isTvheadend = isTvheadendOrTs(convertedUrl)

        layoutBuffering.visibility = View.VISIBLE
        val modeDesc = if (useSoftwareDecoder) "LibVLC软解" else "LibVLC硬解"
        tvBufferStatus.text = if (isTvheadend) "正在连接 Tvheadend 流 ($modeDesc)..." else "正在连接 ${channel.name}..."

        try {
            player.stop()
            val media = Media(vlc, Uri.parse(convertedUrl)).apply {
                // 软硬解选择：若开启硬解失败，则 fallback 到 LibVLC 内置软解
                setHWDecoderEnabled(!useSoftwareDecoder, false)

                // 注入 VLC 标准 UA，防止 Tvheadend 服务端拒绝非 VLC 客户端访问
                addOption(":http-user-agent=$VLC_USER_AGENT")
                addOption(":network-caching=2500")
                addOption(":http-reconnect=true")

                // 针对 Tvheadend 及 MPEG-TS 流显式指定 TS 解封装器与时钟对齐
                if (isTvheadend) {
                    addOption(":demux=ts")
                    addOption(":ts-extra-pmt=1")
                    addOption(":ts-trust-pcr")
                }
            }
            player.media = media
            media.release()
            player.play()
        } catch (e: Exception) {
            Log.e(TAG, "playChannel 发生异常: ${e.message}")
            handlePlaybackError()
        }
    }

    /**
     * 自动容灾：硬解失败时自动降级软解，避免某些芯片对 Tvheadend MP2 音频或特定 MPEG-2 报错黑屏
     */
    private fun handlePlaybackError() {
        val channel = currentChannel ?: return
        if (!hasTriedSoftwareFallback) {
            hasTriedSoftwareFallback = true
            Log.d(TAG, "检测到播放失败，尝试自动切换至 LibVLC 原生软解模式...")
            mainHandler.post {
                layoutBuffering.visibility = View.VISIBLE
                tvBufferStatus.text = "硬解兼容受限，正在自动切换软解..."
                playChannel(channel, useSoftwareDecoder = true)
            }
        } else {
            layoutBuffering.visibility = View.VISIBLE
            tvBufferStatus.text = "直播源断开，3 秒后自动重试..."
            mainHandler.postDelayed({
                hasTriedSoftwareFallback = false
                playChannel(channel, useSoftwareDecoder = false)
            }, 3000L)
        }
    }

    private fun loadPlaylistAndEpg() {
        // 1. 异步加载 EPG
        TvgProvider.loadEpgAsync(this) {
            runOnUiThread {
                channelAdapter.notifyDataSetChanged()
                currentChannel?.let { updateOsdEpg(it) }
            }
        }

        // 2. 从本地或远程拉取用户配置的 M3U 源（无内置硬编码频道）
        IptvRepository.fetchPlaylist(this) { channels ->
            runOnUiThread {
                if (channels.isNotEmpty()) {
                    layoutEmptyState.visibility = View.GONE
                    allChannels = channels
                    applyChannels(channels)
                    hasTriedSoftwareFallback = false
                    playChannel(channels[0], useSoftwareDecoder = false)
                } else {
                    layoutEmptyState.visibility = View.VISIBLE
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

    private fun showOsd(channel: IptvChannel) {
        val index = allChannels.indexOf(channel)
        tvOsdNumber.text = String.format("%02d", if (index >= 0) index + 1 else 1)
        tvOsdName.text = channel.name
        val streamTag = if (isTvheadendOrTs(channel.streamUrl)) "Tvheadend TS" else "LibVLC"
        tvOsdGroup.text = "${channel.group} · $streamTag"
        updateOsdEpg(channel)

        layoutOsd.alpha = 1f
        layoutOsd.visibility = View.VISIBLE

        mainHandler.removeCallbacks(hideOsdRunnable)
        mainHandler.postDelayed(hideOsdRunnable, 5000L)
    }

    private fun updateOsdEpg(channel: IptvChannel) {
        val prog = TvgProvider.getCurrentProgramme(channel.name, channel.epgId)
        if (prog != null) {
            tvOsdEpg.text = "正在播: ${prog.title} (${prog.getProgressPercent()}%)"
        } else {
            tvOsdEpg.text = "暂无当前节目单"
        }
    }

    private fun cycleAspectRatio() {
        currentAspectIndex = (currentAspectIndex + 1) % aspectRatios.size
        val ratio = aspectRatios[currentAspectIndex]
        tvAspectRatio.text = ratio
        applyAspectRatio(ratio)
        Toast.makeText(this, "画面比例: $ratio", Toast.LENGTH_SHORT).show()
    }

    private fun applyAspectRatio(ratio: String) {
        when (ratio) {
            "16:9" -> {
                mediaPlayer?.aspectRatio = "16:9"
                mediaPlayer?.scale = 0f
            }
            "4:3" -> {
                mediaPlayer?.aspectRatio = "4:3"
                mediaPlayer?.scale = 0f
            }
            "拉伸" -> {
                mediaPlayer?.aspectRatio = null
                mediaPlayer?.scale = 0f
            }
            "自适应" -> {
                mediaPlayer?.aspectRatio = null
                mediaPlayer?.scale = 1.0f
            }
        }
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
        hasTriedSoftwareFallback = false
        playChannel(allChannels[currentChannelIndex], useSoftwareDecoder = false)
    }

    private fun prevChannel() {
        if (allChannels.isEmpty()) return
        currentChannelIndex = if (currentChannelIndex - 1 < 0) allChannels.size - 1 else currentChannelIndex - 1
        hasTriedSoftwareFallback = false
        playChannel(allChannels[currentChannelIndex], useSoftwareDecoder = false)
    }

    /**
     * 播放源与专网组播代理设置弹窗
     */
    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("IPTV 播放设置")

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }

        val etM3u = EditText(this).apply {
            hint = "请输入 M3U / M3U8 播放列表 URL"
            setText(IptvRepository.getM3uUrl(this@MainActivity))
        }
        val etUdpxy = EditText(this).apply {
            hint = "udpxy 组播代理 (例如 http://192.168.1.1:4022)"
            setText(UdpxyHelper.getUdpxyHost(this@MainActivity))
        }
        val etEpg = EditText(this).apply {
            hint = "EPG 节目单 XMLTV URL"
            setText(TvgProvider.getEpgUrl(this@MainActivity))
        }

        layout.addView(TextView(this).apply { text = "M3U 订阅源地址：" })
        layout.addView(etM3u)
        layout.addView(TextView(this).apply { text = "udpxy 光猫组播代理地址：" })
        layout.addView(etUdpxy)
        layout.addView(TextView(this).apply { text = "XMLTV 节目单地址：" })
        layout.addView(etEpg)

        builder.setView(layout)
        builder.setPositiveButton("保存并刷新") { _, _ ->
            val newM3u = etM3u.text.toString().trim()
            val newUdpxy = etUdpxy.text.toString().trim()
            val newEpg = etEpg.text.toString().trim()

            IptvRepository.saveM3uUrl(this, newM3u)
            UdpxyHelper.setUdpxyHost(this, newUdpxy)
            TvgProvider.setEpgUrl(this, newEpg)

            loadPlaylistAndEpg()
        }
        builder.setNegativeButton("取消", null)
        builder.show()
    }

    // =========================================================================
    // 电视盒子遥控器与车载按键
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
            KeyEvent.KEYCODE_MENU -> {
                showSettingsDialog()
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
        mediaPlayer?.play()
        mainHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        mainHandler.removeCallbacks(clockRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        libVLC?.release()
        mediaPlayer = null
        libVLC = null
    }
}
