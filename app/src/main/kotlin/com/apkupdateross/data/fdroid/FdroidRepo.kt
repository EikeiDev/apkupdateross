package com.apkupdateross.data.fdroid

import java.util.UUID

data class FdroidRepo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false
)
