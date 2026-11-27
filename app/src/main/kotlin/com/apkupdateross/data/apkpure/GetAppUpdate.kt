package com.apkupdateross.data.apkpure

import kotlin.random.Random


data class GetAppUpdate(
    val app_info_for_update: List<AppInfoForUpdate> = emptyList(),
    val android_id: String = java.lang.Long.toHexString(Random.nextLong()),
    val application_id: String = "com.apkpure.aegon",
    val cached_size: Long = -1
)
