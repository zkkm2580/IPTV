package io.microshow.iptv.epg

import android.content.Context
import android.util.Log
import android.util.Xml
import io.microshow.iptv.model.EpgProgramme
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/**
 * EPG 电子节目单管理类 (TvgProvider)
 * 异步下载并流式解析 XMLTV (.xml 或 .xml.gz)，建立电视台当前节目与时段索引
 */
object TvgProvider {

    private const val TAG = "TvgProvider"
    private const val PREF_NAME = "iptv_epg"
    private const val KEY_EPG_URL = "epg_url"

    // 默认备选 EPG 节目单地址（支持 XMLTV 格式）
    const val DEFAULT_EPG_URL = "http://epg.51zmt.top:8000/e.xml.gz"

    // 缓存：channelId/name -> 节目列表
    private val epgCache = ConcurrentHashMap<String, MutableList<EpgProgramme>>()
    private val timeFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

    fun getEpgUrl(context: Context): String {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_EPG_URL, DEFAULT_EPG_URL) ?: DEFAULT_EPG_URL
    }

    fun setEpgUrl(context: Context, url: String) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_EPG_URL, url.trim()).apply()
    }

    /**
     * 获取指定频道正在播出的当前节目
     */
    fun getCurrentProgramme(channelName: String, epgId: String = ""): EpgProgramme? {
        val now = System.currentTimeMillis()
        val list = epgCache[channelName] ?: (if (epgId.isNotEmpty()) epgCache[epgId] else null)
        return list?.firstOrNull { it.isCurrent(now) }
    }

    /**
     * 异步拉取并解析 XMLTV 节目单
     */
    fun loadEpgAsync(context: Context, onComplete: (() -> Unit)? = null) {
        val epgUrl = getEpgUrl(context)
        if (epgUrl.isBlank()) return

        thread {
            try {
                Log.d(TAG, "开始下载并解析 EPG 节目单: $epgUrl")
                val url = URL(epgUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) IPTVPlayer")

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    var inStream: InputStream = conn.inputStream
                    if (epgUrl.endsWith(".gz", ignoreCase = true)) {
                        inStream = GZIPInputStream(inStream)
                    }

                    parseXmltv(inStream)
                    Log.d(TAG, "EPG 节目单解析完成，涵盖频道数: ${epgCache.size}")
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                Log.w(TAG, "EPG 下载/解析失败: ${e.message}")
            }
        }
    }

    private fun parseXmltv(stream: InputStream) {
        try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, "UTF-8")

            var eventType = parser.eventType
            var currentChannelId = ""
            var currentStartMs = 0L
            var currentStopMs = 0L
            var currentTitle = ""

            val now = System.currentTimeMillis()
            val dayStart = now - 24 * 3600 * 1000L
            val dayEnd = now + 24 * 3600 * 1000L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name.equals("programme", ignoreCase = true)) {
                            currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                            val startStr = parser.getAttributeValue(null, "start") ?: ""
                            val stopStr = parser.getAttributeValue(null, "stop") ?: ""
                            currentStartMs = parseTime(startStr)
                            currentStopMs = parseTime(stopStr)
                            currentTitle = ""
                        } else if (name.equals("title", ignoreCase = true)) {
                            currentTitle = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name.equals("programme", ignoreCase = true)) {
                            // 仅缓存 24 小时内的节目，节省内存
                            if (currentChannelId.isNotEmpty() && currentTitle.isNotEmpty() &&
                                currentStopMs >= dayStart && currentStartMs <= dayEnd) {
                                val prog = EpgProgramme(currentChannelId, currentTitle, currentStartMs, currentStopMs)
                                epgCache.getOrPut(currentChannelId) { mutableListOf() }.add(prog)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { stream.close() } catch (e: Exception) {}
        }
    }

    private fun parseTime(raw: String): Long {
        if (raw.length < 14) return 0L
        return try {
            val clean = raw.substring(0, 14)
            timeFormat.parse(clean)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}
