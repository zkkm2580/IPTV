package io.microshow.iptv.model

import java.io.Serializable

data class IptvChannel(
    val id: String,
    val name: String,
    val group: String = "未分类",
    val streamUrl: String,
    val logoUrl: String = "",
    val epgId: String = ""
) : Serializable
