package com.apkupdateross.util

import com.apkupdateross.BuildConfig

object AppUserAgent {
    val value: String
        get() = "APKUpdaterOSS-v${BuildConfig.VERSION_NAME}"
}
