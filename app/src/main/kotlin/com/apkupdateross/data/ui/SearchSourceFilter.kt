package com.apkupdateross.data.ui

import androidx.annotation.StringRes
import com.apkupdateross.R

enum class SearchSourceFilter(@StringRes val labelRes: Int) {
    APKMIRROR(R.string.search_filter_apkmirror),
    FDROID(R.string.search_filter_fdroid),
    APTOIDE(R.string.search_filter_aptoide),
    APKPURE(R.string.search_filter_apkpure),
    GITHUB(R.string.search_filter_github),
    GITLAB(R.string.search_filter_gitlab),
    PLAY(R.string.search_filter_play),
    RUSTORE(R.string.search_filter_rustore)
    ;

    companion object {
        val defaultSelection: Set<SearchSourceFilter>
            get() = entries.toSet()
    }
}
