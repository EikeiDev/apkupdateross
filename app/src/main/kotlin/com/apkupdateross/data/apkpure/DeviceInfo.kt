package com.apkupdateross.data.apkpure

import android.content.res.Resources
import android.os.Build
import kotlin.random.Random


data class DeviceInfo(
    val abis: List<String> = Build.SUPPORTED_ABIS.toList(),
    val android_id: String = java.lang.Long.toHexString(Random.nextLong()),
    val os_ver: String = Build.VERSION.SDK_INT.toString(),
    val os_ver_name: String = Build.VERSION.RELEASE,
    val platform: Int = 1,   // TODO: Figure out what is this
    val screen_height: Int =  Resources.getSystem().displayMetrics.heightPixels,
    val screen_width: Int = Resources.getSystem().displayMetrics.widthPixels
)
