package com.apkupdateross.data.ui

import androidx.annotation.StringRes
import com.apkupdateross.R
import java.util.Locale

enum class ReleaseType(@StringRes val labelRes: Int) {
    Stable(R.string.release_type_stable),
    Beta(R.string.release_type_beta),
    Alpha(R.string.release_type_alpha),
    PreRelease(R.string.release_type_pre_release);

    companion object {
        fun from(
            version: String,
            title: String? = null,
            explicitPreRelease: Boolean = false
        ): ReleaseType {
            val text = listOf(version, title.orEmpty())
                .joinToString(separator = " ")
                .lowercase(Locale.ROOT)

            return when {
                alphaRegex.containsMatchIn(text) -> Alpha
                betaRegex.containsMatchIn(text) -> Beta
                explicitPreRelease || preReleaseRegex.containsMatchIn(text) -> PreRelease
                else -> Stable
            }
        }

        private val alphaRegex = Regex("(^|[^a-z0-9])alpha([0-9]+|[^a-z0-9]|$)")
        private val betaRegex = Regex("(^|[^a-z0-9])beta([0-9]+|[^a-z0-9]|$)")
        private val preReleaseRegex = Regex("(^|[^a-z0-9])(pre[-_ ]?release|preview|rc[0-9]+|rc|canary|nightly|snapshot|dev|unstable)([^a-z0-9]|$)")
    }
}
