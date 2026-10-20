package io.microshow.iptv.model

import java.io.Serializable

data class EpgProgramme(
    val channelId: String,
    val title: String,
    val startMs: Long,
    val stopMs: Long
) : Serializable {
    fun isCurrent(now: Long = System.currentTimeMillis()): Boolean {
        return now in startMs..stopMs
    }

    fun getProgressPercent(now: Long = System.currentTimeMillis()): Int {
        if (stopMs <= startMs) return 0
        val percent = ((now - startMs).toFloat() / (stopMs - startMs).toFloat() * 100).toInt()
        return percent.coerceIn(0, 100)
    }
}
