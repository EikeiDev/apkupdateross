package com.apkupdateross.repository

import com.apkupdateross.R
import com.apkupdateross.data.fdroid.FdroidRepo
import com.apkupdateross.data.fdroid.normalizeFdroidRepoUrl
import com.apkupdateross.data.ui.Source
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.service.FdroidService
import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap

class FdroidRepoRegistry(
	private val service: FdroidService,
	private val prefs: Prefs,
	private val gson: Gson
) {
	private val cache = ConcurrentHashMap<String, FdroidRepository>()

	fun get(repo: FdroidRepo): FdroidRepository? {
		val normalizedUrl = normalizeFdroidRepoUrl(repo.url) ?: return null
		val cacheKey = "${repo.id}|$normalizedUrl|${repo.name}"
		return cache.computeIfAbsent(cacheKey) {
			FdroidRepository(
				service = service,
				url = normalizedUrl,
				source = Source(repo.name, R.drawable.ic_fdroid),
				prefs = prefs,
				gson = gson
			)
		}
	}

	fun clearAllCaches() {
		cache.values.forEach { it.clearCache() }
	}
}
