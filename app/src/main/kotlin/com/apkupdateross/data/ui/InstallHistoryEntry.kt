package com.apkupdateross.data.ui

import java.util.UUID

enum class HistoryEventType {
	FOREGROUND_INSTALL,
	BACKGROUND_INSTALL,
	BACKGROUND_CHECK
}

data class InstallHistoryEntry(
	val id: String = UUID.randomUUID().toString(),
	val timestamp: Long = System.currentTimeMillis(),
	val packageName: String = "",
	val appName: String = "",
	val version: String = "",
	val sourceName: String = "",
	val eventType: HistoryEventType = HistoryEventType.FOREGROUND_INSTALL,
	val success: Boolean = true,
	val details: String = ""
)
