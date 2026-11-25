package com.apkupdateross.util

import com.apkupdateross.prefs.Prefs

class StoreNotFoundCache(
	private val prefs: Prefs,
	private val ttlMs: Long = DEFAULT_TTL_MS
) {
	companion object {
		const val DEFAULT_TTL_MS = 7L * 24L * 60L * 60L * 1000L
		const val STORE_HUAWEI = "Huawei"
		const val STORE_SAMSUNG = "Samsung"
		const val STORE_UPTODOWN = "Uptodown"
		const val STORE_APK20 = "APK20"
	}

	private val lock = Any()

	fun isNotFound(store: String, packageName: String, nowMs: Long = System.currentTimeMillis()): Boolean {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get()
			val storeMap = all[store] ?: return false
			val ts = storeMap[packageName] ?: return false
			return nowMs - ts <= ttlMs
		}
	}

	fun markNotFound(store: String, packageName: String, nowMs: Long = System.currentTimeMillis()) {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get().toMutableMap()
			val storeMap = (all[store] ?: emptyMap())
				.filterValues { nowMs - it <= ttlMs }
				.toMutableMap()
			storeMap[packageName] = nowMs
			all[store] = storeMap
			prefs.storeNotFoundCache.put(all)
		}
	}

	fun clearFound(store: String, packageName: String) {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get()
			val storeMap = all[store] ?: return
			if (packageName !in storeMap) return
			val updatedAll = all.toMutableMap()
			updatedAll[store] = storeMap - packageName
			prefs.storeNotFoundCache.put(updatedAll)
		}
	}

	fun clearPackage(packageName: String) {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get()
			if (all.values.none { packageName in it }) return
			val updated = all.mapValues { (_, map) -> map - packageName }
			prefs.storeNotFoundCache.put(updated)
		}
	}

	fun clearStore(store: String) {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get()
			if (store !in all) return
			prefs.storeNotFoundCache.put(all - store)
		}
	}

	fun totalCount(nowMs: Long = System.currentTimeMillis()): Int {
		synchronized(lock) {
			val all = prefs.storeNotFoundCache.get()
			return all.values.sumOf { storeMap ->
				storeMap.values.count { ts -> isNotFoundCacheEntryValid(ts, nowMs, ttlMs) }
			}
		}
	}

	fun clearAll() {
		synchronized(lock) {
			prefs.storeNotFoundCache.put(emptyMap())
		}
	}
}

internal fun isNotFoundCacheEntryValid(timestampMs: Long, nowMs: Long, ttlMs: Long = StoreNotFoundCache.DEFAULT_TTL_MS): Boolean =
	timestampMs > 0L && nowMs >= timestampMs && (nowMs - timestampMs) <= ttlMs
