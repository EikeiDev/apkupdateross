package com.apkupdateross.data.gitlab


data class GitLabLink(
    val url: String,
    val name: String = "",
    val direct_asset_url: String? = null
)
