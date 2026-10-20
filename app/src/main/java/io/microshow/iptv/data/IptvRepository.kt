package io.microshow.iptv.data

import android.content.Context
import android.content.SharedPreferences
import io.microshow.iptv.model.IptvChannel
import io.microshow.iptv.parser.M3uParser
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * 播放源数据仓库
 * 取消硬编码内置频道，完全由用户配置的 M3U 在线源、本地导入源进行驱动并持久化
 */
object IptvRepository {

    private const val PREF_NAME = "iptv_data"
    private const val KEY_M3U_URL = "custom_m3u_url"
    private const val KEY_CACHED_CONTENT = "cached_m3u_content"

    fun getM3uUrl(context: Context): String {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_M3U_URL, "") ?: ""
    }

    fun saveM3uUrl(context: Context, url: String) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_M3U_URL, url.trim()).apply()
    }

    /**
     * 读取本地缓存的播放列表
     */
    fun getCachedChannels(context: Context): List<IptvChannel> {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val content = sp.getString(KEY_CACHED_CONTENT, "") ?: ""
        return if (content.isNotBlank()) M3uParser.parse(content) else emptyList()
    }

    /**
     * 保存播放列表缓存
     */
    fun saveCachedContent(context: Context, content: String) {
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_CACHED_CONTENT, content).apply()
    }

    /**
     * 异步拉取用户配置的 M3U 播放列表
     */
    fun fetchPlaylist(context: Context, urlStr: String = getM3uUrl(context), onResult: (List<IptvChannel>) -> Unit) {
        if (urlStr.isBlank()) {
            onResult(emptyList())
            return
        }

        thread {
            try {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) IPTVPlayer")
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    saveCachedContent(context, content)
                    val channels = M3uParser.parse(content)
                    onResult(channels)
                } else {
                    onResult(getCachedChannels(context))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(getCachedChannels(context))
            }
        }
    }
}
