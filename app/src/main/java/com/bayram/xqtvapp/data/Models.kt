package com.bayram.xqtvapp.data

data class PortalConfig(
    val portalUrl: String = "",
    val mac: String = ""
)

data class StalkerChannel(
    val id: String,
    val name: String,
    val logo: String,
    val cmd: String,
    val genre: String
)

data class StalkerCategory(
    val id: String,
    val title: String
)
