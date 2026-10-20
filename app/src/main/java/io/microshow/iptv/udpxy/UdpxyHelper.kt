package io.microshow.iptv.udpxy

import android.content.Context
import android.content.SharedPreferences

/**
 * 专网组播 udpxy 代理转换辅助类
 * 将运营商电信/联通光猫内网 rtp://239.x.x.x 或 udp:// 转换为 http://proxy:port/rtp/ 或 /udp/
 */
object UdpxyHelper {

    private const val PREF_NAME = "iptv_udpxy"
    private const val KEY_UDPXY_HOST = "udpxy_host"

    private var cachedHost: String? = null

    fun getUdpxyHost(context: Context): String {
        if (cachedHost == null) {
            val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            cachedHost = sp.getString(KEY_UDPXY_HOST, "") ?: ""
        }
        return cachedHost ?: ""
    }

    fun setUdpxyHost(context: Context, host: String) {
        cachedHost = host.trim()
        val sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_UDPXY_HOST, cachedHost).apply()
    }

    fun convertUrl(context: Context, rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val proxy = getUdpxyHost(context).trimEnd('/')
        if (proxy.isBlank()) return trimmed

        return when {
            trimmed.startsWith("rtp://", ignoreCase = true) -> {
                val path = trimmed.substring("rtp://".length)
                "$proxy/rtp/$path"
            }
            trimmed.startsWith("udp://", ignoreCase = true) -> {
                val path = trimmed.substring("udp://".length)
                "$proxy/udp/$path"
            }
            else -> trimmed
        }
    }
}
