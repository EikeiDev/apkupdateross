package com.apkupdateross.data.ui

import androidx.annotation.StringRes
import com.apkupdateross.R

enum class SwipeIgnoreDirection(@StringRes val labelRes: Int) {
    Right(R.string.swipe_direction_right),
    Left(R.string.swipe_direction_left),
    Both(R.string.swipe_direction_both);

    companion object {
        val Default = Both

        fun fromIndex(index: Int): SwipeIgnoreDirection =
            entries.getOrElse(index) { Default }
    }
}
