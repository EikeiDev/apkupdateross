package com.apkupdateross.data.ui

data class GroupedAppUpdate(
    val packageName: String,
    val updates: List<AppUpdate>
) {
    val primary: AppUpdate = updates.first() // Assumes list is sorted by preference
    val id: Int = packageName.hashCode()
    
    val isInstalling: Boolean get() = updates.any { it.isInstalling }
    val isDownloading: Boolean get() = updates.any { it.isDownloading }
    val total: Long get() = updates.firstOrNull { it.isDownloading || it.isInstalling }?.total ?: primary.total
    val progress: Long get() = updates.firstOrNull { it.isDownloading || it.isInstalling }?.progress ?: primary.progress
}

fun List<GroupedAppUpdate>.indexOf(id: Int): Int {
    return indexOfFirst { grouped -> grouped.updates.any { it.id == id } }
}

fun List<GroupedAppUpdate>.setIsInstalling(id: Int, b: Boolean): List<GroupedAppUpdate> = map { grouped ->
    val subIndex = grouped.updates.indexOfFirst { it.id == id }
    if (subIndex != -1) {
        val updatedList = grouped.updates.toMutableList()
        val current = updatedList[subIndex]
        updatedList[subIndex] = current.copy(
            isInstalling = b,
            progress = if (b) 0L else current.progress,
            total = if (b) 0L else current.total
        )
        grouped.copy(updates = updatedList)
    } else grouped
}

fun List<GroupedAppUpdate>.setIsDownloading(id: Int, b: Boolean): List<GroupedAppUpdate> = map { grouped ->
    val subIndex = grouped.updates.indexOfFirst { it.id == id }
    if (subIndex != -1) {
        val updatedList = grouped.updates.toMutableList()
        val current = updatedList[subIndex]
        updatedList[subIndex] = current.copy(
            isDownloading = b,
            progress = if (b) 0L else current.progress,
            total = if (b) 0L else current.total
        )
        grouped.copy(updates = updatedList)
    } else grouped
}

fun List<GroupedAppUpdate>.removeId(id: Int): List<GroupedAppUpdate> = mapNotNull { grouped ->
    val subIndex = grouped.updates.indexOfFirst { it.id == id }
    if (subIndex != -1) {
        val updatedList = grouped.updates.toMutableList()
        updatedList.removeAt(subIndex)
        if (updatedList.isEmpty()) null else grouped.copy(updates = updatedList)
    } else grouped
}

fun List<GroupedAppUpdate>.removePackageName(packageName: String): List<GroupedAppUpdate> = filter { it.packageName != packageName }

fun List<GroupedAppUpdate>.setProgress(progress: com.apkupdateross.data.ui.AppInstallProgress): List<GroupedAppUpdate> = map { grouped ->
    val subIndex = grouped.updates.indexOfFirst { it.id == progress.id }
    if (subIndex != -1) {
        val updatedList = grouped.updates.toMutableList()
        val current = updatedList[subIndex]
        updatedList[subIndex] = current.copy(
            progress = progress.progress ?: current.progress,
            total = progress.total ?: current.total
        )
        grouped.copy(updates = updatedList)
    } else grouped
}
