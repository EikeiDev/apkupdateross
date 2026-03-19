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

fun MutableList<GroupedAppUpdate>.indexOf(id: Int): Int {
    return indexOfFirst { grouped -> grouped.updates.any { it.id == id } }
}

fun MutableList<GroupedAppUpdate>.setIsInstalling(id: Int, b: Boolean): List<GroupedAppUpdate> {
    val index = this.indexOf(id)
    if (index != -1) {
        val grouped = this[index]
        val updatedList = grouped.updates.toMutableList()
        val subIndex = updatedList.indexOfFirst { it.id == id }
        if (subIndex != -1) {
            val current = updatedList[subIndex]
            updatedList[subIndex] = current.copy(
                isInstalling = b,
                progress = if (b) 0L else current.progress,
                total = if (b) 0L else current.total
            )
            this[index] = grouped.copy(updates = updatedList)
        }
    }
    return this
}

fun MutableList<GroupedAppUpdate>.setIsDownloading(id: Int, b: Boolean): List<GroupedAppUpdate> {
    val index = this.indexOf(id)
    if (index != -1) {
        val grouped = this[index]
        val updatedList = grouped.updates.toMutableList()
        val subIndex = updatedList.indexOfFirst { it.id == id }
        if (subIndex != -1) {
            val current = updatedList[subIndex]
            updatedList[subIndex] = current.copy(
                isDownloading = b,
                progress = if (b) 0L else current.progress,
                total = if (b) 0L else current.total
            )
            this[index] = grouped.copy(updates = updatedList)
        }
    }
    return this
}

fun MutableList<GroupedAppUpdate>.removeId(id: Int): List<GroupedAppUpdate> {
    val index = this.indexOf(id)
    if (index != -1) {
        val grouped = this[index]
        val updatedList = grouped.updates.toMutableList()
        val subIndex = updatedList.indexOfFirst { it.id == id }
        if (subIndex != -1) {
            updatedList.removeAt(subIndex)
            if (updatedList.isEmpty()) {
                this.removeAt(index)
            } else {
                this[index] = grouped.copy(updates = updatedList)
            }
        }
    }
    return this
}

fun MutableList<GroupedAppUpdate>.removePackageName(packageName: String): List<GroupedAppUpdate> {
    this.removeAll { it.packageName == packageName }
    return this
}

fun MutableList<GroupedAppUpdate>.setProgress(progress: com.apkupdateross.data.ui.AppInstallProgress): List<GroupedAppUpdate> {
    val index = this.indexOf(progress.id)
    if (index != -1) {
        val grouped = this[index]
        val updatedList = grouped.updates.toMutableList()
        val subIndex = updatedList.indexOfFirst { it.id == progress.id }
        if (subIndex != -1) {
            val current = updatedList[subIndex]
            updatedList[subIndex] = current.copy(
                progress = progress.progress ?: current.progress,
                total = progress.total ?: current.total
            )
            this[index] = grouped.copy(updates = updatedList)
        }
    }
    return this
}
