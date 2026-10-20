package io.microshow.iptv.data

import io.microshow.iptv.model.IptvChannel
import io.microshow.iptv.parser.M3uParser
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object IptvRepository {

    // 默认内置在线 M3U 播放列表源（涵盖央视、各省卫视、高清影视）
    const val DEFAULT_ONLINE_M3U = "https://raw.githubusercontent.com/Guovin/TV/gd/result.m3u"

    /**
     * 预设内置高质量电视直播频道（央视全套 + 热门省市卫视）
     */
    fun getPresetChannels(): List<IptvChannel> = listOf(
        // 央视频道
        IptvChannel("cctv_1", "CCTV-1 综合", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv1hd.m3u8"),
        IptvChannel("cctv_2", "CCTV-2 财经", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv2hd.m3u8"),
        IptvChannel("cctv_3", "CCTV-3 综艺", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv3hd.m3u8"),
        IptvChannel("cctv_4", "CCTV-4 中文国际", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv4hd.m3u8"),
        IptvChannel("cctv_5", "CCTV-5 体育", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv5hd.m3u8"),
        IptvChannel("cctv_5p", "CCTV-5+ 体育赛事", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv5phd.m3u8"),
        IptvChannel("cctv_6", "CCTV-6 电影", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv6hd.m3u8"),
        IptvChannel("cctv_7", "CCTV-7 国防军事", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv7hd.m3u8"),
        IptvChannel("cctv_8", "CCTV-8 电视剧", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv8hd.m3u8"),
        IptvChannel("cctv_9", "CCTV-9 纪录", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv9hd.m3u8"),
        IptvChannel("cctv_10", "CCTV-10 科教", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv10hd.m3u8"),
        IptvChannel("cctv_11", "CCTV-11 戏曲", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv11hd.m3u8"),
        IptvChannel("cctv_12", "CCTV-12 社会与法", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv12hd.m3u8"),
        IptvChannel("cctv_13", "CCTV-13 新闻", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv13hd.m3u8"),
        IptvChannel("cctv_14", "CCTV-14 少儿", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv14hd.m3u8"),
        IptvChannel("cctv_15", "CCTV-15 音乐", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv15hd.m3u8"),
        IptvChannel("cctv_16", "CCTV-16 奥林匹克", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv16hd.m3u8"),
        IptvChannel("cctv_17", "CCTV-17 农业农村", "央视频道", "http://ivi.bupt.edu.cn/hls/cctv17hd.m3u8"),

        // 卫视频道
        IptvChannel("ws_hn", "湖南卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/hunanhd.m3u8"),
        IptvChannel("ws_zj", "浙江卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/zjhd.m3u8"),
        IptvChannel("ws_js", "江苏卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/jshd.m3u8"),
        IptvChannel("ws_df", "东方卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/dfhd.m3u8"),
        IptvChannel("ws_bj", "北京卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/btv1hd.m3u8"),
        IptvChannel("ws_gd", "广东卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/gdhd.m3u8"),
        IptvChannel("ws_sz", "深圳卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/szhd.m3u8"),
        IptvChannel("ws_sd", "山东卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/sdhd.m3u8"),
        IptvChannel("ws_tj", "天津卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/tjhd.m3u8"),
        IptvChannel("ws_ah", "安徽卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/ahhd.m3u8"),
        IptvChannel("ws_cq", "重庆卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/cqhd.m3u8"),
        IptvChannel("ws_sc", "四川卫视", "各省卫视", "http://ivi.bupt.edu.cn/hls/schd.m3u8")
    )

    /**
     * 异步拉取远程 M3U 播放列表并解析
     */
    fun fetchOnlinePlaylist(urlStr: String = DEFAULT_ONLINE_M3U, onResult: (List<IptvChannel>) -> Unit) {
        thread {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) IPTVPlayer")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = M3uParser.parse(content)
                    if (parsed.isNotEmpty()) {
                        onResult(parsed)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
