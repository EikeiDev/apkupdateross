package com.apkupdateross.util

import android.os.Build

object AppUserAgent {
    val value: String
        get() = "Dalvik/2.1.0 (Linux; U; Android ${Build.VERSION.RELEASE}; " +
            "${Build.MODEL} Build/${Build.ID})"
}
