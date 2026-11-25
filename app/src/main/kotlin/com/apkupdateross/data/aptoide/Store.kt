package com.apkupdateross.data.aptoide

import com.google.gson.annotations.SerializedName

data class Store(
	val name: String = "",
	@SerializedName("id") val id: Long = 0L
)
