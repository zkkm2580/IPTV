package io.microshow.iptv.parser

import io.microshow.iptv.model.IptvChannel
import java.io.BufferedReader
import java.io.StringReader

/**
 * 健壮的 M3U / M3U8 播放列表解析器
 * 支持 EXTINF 元数据提取（tvg-name, tvg-id, tvg-logo, group-title）、分组与单行逗号格式
 */
object M3uParser {

    private val TVG_NAME_REGEX = Regex("""tvg-name="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val TVG_ID_REGEX = Regex("""tvg-id="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val TVG_LOGO_REGEX = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val GROUP_TITLE_REGEX = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var currentGroup = "综合频道"
        var currentTvgName = ""
        var currentTvgId = ""
        var currentLogoUrl = ""
        var currentChannelName = ""
        var channelIndex = 1

        BufferedReader(StringReader(content)).useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("#EXTM3U") || line.startsWith("//")) {
                    continue
                }

                // 1. 识别分组/分类标签
                if (line.contains(",#genre#") || line.contains(",#group#") || (line.startsWith("[") && line.endsWith("]"))) {
                    currentGroup = line.replace(",#genre#", "")
                        .replace(",#group#", "")
                        .removeSurrounding("[", "]")
                        .trim()
                    continue
                }

                // 2. 识别 #EXTINF 行
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    TVG_NAME_REGEX.find(line)?.let { currentTvgName = it.groupValues[1].trim() }
                    TVG_ID_REGEX.find(line)?.let { currentTvgId = it.groupValues[1].trim() }
                    TVG_LOGO_REGEX.find(line)?.let { currentLogoUrl = it.groupValues[1].trim() }
                    GROUP_TITLE_REGEX.find(line)?.let { currentGroup = it.groupValues[1].trim() }

                    val commaIndex = line.lastIndexOf(',')
                    if (commaIndex != -1 && commaIndex < line.length - 1) {
                        currentChannelName = line.substring(commaIndex + 1).trim()
                    }
                    continue
                }

                // 3. 识别单行逗号制：频道名称,URL
                if (line.contains(",") && (line.contains("http://", ignoreCase = true) || line.contains("https://", ignoreCase = true) || line.contains("rtp://", ignoreCase = true))) {
                    val parts = line.split(",", limit = 2)
                    val name = parts[0].trim()
                    val url = parts[1].trim()
                    if (name.isNotEmpty() && url.isNotEmpty()) {
                        channels.add(
                            IptvChannel(
                                id = "ch_$channelIndex",
                                name = name,
                                group = currentGroup,
                                streamUrl = url
                            )
                        )
                        channelIndex++
                    }
                    continue
                }

                // 4. 识别流地址行（HLS, HTTP-TS, RTMP, RTSP, RTP）
                if (line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) ||
                    line.startsWith("rtmp://", ignoreCase = true) ||
                    line.startsWith("rtsp://", ignoreCase = true) ||
                    line.startsWith("rtp://", ignoreCase = true)) {

                    val name = when {
                        currentChannelName.isNotEmpty() -> currentChannelName
                        currentTvgName.isNotEmpty() -> currentTvgName
                        else -> "频道 $channelIndex"
                    }

                    channels.add(
                        IptvChannel(
                            id = "ch_$channelIndex",
                            name = name,
                            group = if (currentGroup.isNotEmpty()) currentGroup else "默认分组",
                            streamUrl = line,
                            logoUrl = currentLogoUrl,
                            epgId = currentTvgId
                        )
                    )
                    channelIndex++

                    // 重置单行状态
                    currentChannelName = ""
                    currentTvgName = ""
                    currentTvgId = ""
                    currentLogoUrl = ""
                } else if (!line.startsWith("#")) {
                    // 暂存频道名称（针对两行制模式：第一行名称，第二行URL）
                    currentChannelName = line
                }
            }
        }
        return channels
    }
}
