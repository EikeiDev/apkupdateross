package com.apkupdateross.data.ui

import androidx.annotation.StringRes
import com.apkupdateross.R

enum class SearchSourceFilter(@StringRes val labelRes: Int) {
    APK20(R.string.search_filter_apk20),
    APKMIRROR(R.string.search_filter_apkmirror),
    APKPURE(R.string.search_filter_apkpure),
    HUAWEI(R.string.search_filter_huawei),
    APTOIDE(R.string.search_filter_aptoide),
    CODEBERG(R.string.search_filter_codeberg),
    FDROID(R.string.search_filter_fdroid),
    SAMSUNG(R.string.source_samsung_galaxy_store),
    GITHUB(R.string.search_filter_github),
    GITLAB(R.string.search_filter_gitlab),
    PLAY(R.string.search_filter_play),
    RUSTORE(R.string.search_filter_rustore),
    UPTODOWN(R.string.search_filter_uptodown),
    FOURPDA(R.string.search_filter_4pda)
    ;

    companion object {
        val defaultSelection: Set<SearchSourceFilter>
            get() = entries.toSet()
    }
}
