package io.microshow.iptv.parser

import io.microshow.iptv.model.IptvChannel
import java.io.BufferedReader
import java.io.StringReader

/**
 * 健壮的 M3U / M3U8 播放列表逐行解析器
 * 提取 #EXTM3U 中的 #EXTINF 元数据（tvg-id, tvg-name, tvg-logo, group-title），支持多级分类与多行格式
 */
object M3uParser {

    private val TVG_NAME_REGEX = Regex("""tvg-name="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val TVG_ID_REGEX = Regex("""tvg-id="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val TVG_LOGO_REGEX = Regex("""tvg-logo="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val GROUP_TITLE_REGEX = Regex("""group-title="([^"]+)"""", RegexOption.IGNORE_CASE)

    fun parse(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var currentGroup = "未分类"
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

                // 1. 识别分组分类行（#genre#、#group#、[分组]）
                if (line.contains(",#genre#") || line.contains(",#group#") || (line.startsWith("[") && line.endsWith("]"))) {
                    currentGroup = line.replace(",#genre#", "")
                        .replace(",#group#", "")
                        .removeSurrounding("[", "]")
                        .trim()
                    continue
                }

                // 2. 识别 #EXTINF 行，逐项扫描元数据
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

                // 3. 识别单行逗号格式：频道名称,URL
                if (line.contains(",") && (line.contains("://"))) {
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

                // 4. 识别流媒体播放地址行（HTTP/HTTPS/HLS/MPEG-TS/RTP/UDP/RTMP/RTSP）
                if (line.startsWith("http://", ignoreCase = true) ||
                    line.startsWith("https://", ignoreCase = true) ||
                    line.startsWith("rtp://", ignoreCase = true) ||
                    line.startsWith("udp://", ignoreCase = true) ||
                    line.startsWith("rtmp://", ignoreCase = true) ||
                    line.startsWith("rtsp://", ignoreCase = true)) {

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
                            epgId = if (currentTvgId.isNotEmpty()) currentTvgId else name
                        )
                    )
                    channelIndex++

                    currentChannelName = ""
                    currentTvgName = ""
                    currentTvgId = ""
                    currentLogoUrl = ""
                } else if (!line.startsWith("#")) {
                    currentChannelName = line
                }
            }
        }
        return channels
    }
}
