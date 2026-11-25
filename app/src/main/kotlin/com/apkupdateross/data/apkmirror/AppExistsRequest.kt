package com.apkupdateross.data.apkmirror

data class AppExistsRequest(
    val pnames: List<String>,
    val exclude: List<String> = listOf("alpha", "beta")
)
