package io.microshow.iptv.model

import java.io.Serializable

data class IptvChannel(
    val id: String,
    val name: String,
    val group: String = "综合频道",
    val streamUrl: String,
    val logoUrl: String = "",
    val epgId: String = ""
) : Serializable
