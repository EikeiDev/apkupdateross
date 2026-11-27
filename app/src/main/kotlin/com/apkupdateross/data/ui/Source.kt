package com.apkupdateross.data.ui

import com.apkupdateross.R


data class Source(
    val name: String,
    val resourceId: Int
)

val ApkMirrorSource = Source("ApkMirror", R.drawable.ic_apkmirror)
val GitHubSource = Source("GitHub", R.drawable.ic_github)
val CodebergSource = Source("Codeberg", R.drawable.ic_codeberg)
val FdroidSource = Source("F-Droid (Main)", R.drawable.ic_fdroid)
val IzzySource = Source("F-Droid (Izzy)", R.drawable.ic_izzy)
val AptoideSource = Source("Aptoide", R.drawable.ic_aptoide)
val ApkPureSource = Source("ApkPure", R.drawable.ic_apkpure)
val Apk20Source = Source("APK20", R.drawable.ic_apk20)
val UptodownSource = Source("Uptodown", R.drawable.ic_uptodown)
val GitLabSource = Source("GitLab", R.drawable.ic_gitlab)
val PlaySource = Source("Play", R.drawable.ic_play)
val RuStoreSource = Source("RuStore", R.drawable.ic_rustore)
val HuaweiSource = Source("AppGallery", R.drawable.ic_huawei)
val SamsungGalaxyStoreSource = Source("Galaxy Store", R.drawable.ic_samsung_galaxy_store)
val FourPdaSource = Source("4PDA", R.drawable.ic_4pda)

val defaultSourcePriorityOrder: List<String> = listOf(
    PlaySource.name,
    SamsungGalaxyStoreSource.name,
    GitHubSource.name,
    CodebergSource.name,
    GitLabSource.name,
    ApkMirrorSource.name,
    FdroidSource.name,
    IzzySource.name,
    RuStoreSource.name,
    FourPdaSource.name,
    ApkPureSource.name,
    Apk20Source.name,
    UptodownSource.name,
    AptoideSource.name,
    HuaweiSource.name
)

fun Source.priority(filterRuStore: Boolean = false, customOrder: List<String> = emptyList()): Int {
    if (customOrder.isNotEmpty()) {
        val index = customOrder.indexOf(name)
        if (index >= 0) return (customOrder.size - index) * 10 + 1000
    }
    return when (name) {
        "RuStore" -> if (filterRuStore) 110 else 65
        "Play" -> 100
        "GitHub" -> 90
        "Codeberg" -> 90
        "GitLab" -> 90
        "ApkMirror" -> 80
        "F-Droid (Main)" -> 70
        "F-Droid (Izzy)" -> 70
        "ApkPure" -> 60
        "APK20" -> 55
        "Uptodown" -> 53
        "Aptoide" -> 50
        "AppGallery" -> 45
        "Galaxy Store" -> 95
        "4PDA" -> 62
        else -> 0
    }
}
