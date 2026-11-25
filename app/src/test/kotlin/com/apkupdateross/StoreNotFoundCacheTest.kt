package com.apkupdateross

import com.apkupdateross.util.isNotFoundCacheEntryValid
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreNotFoundCacheTest {

	@Test
	fun validatesTtlTimestamps() {
		val ttl = 10_000L
		assertTrue(isNotFoundCacheEntryValid(timestampMs = 5_000L, nowMs = 10_000L, ttlMs = ttl))
		assertTrue(isNotFoundCacheEntryValid(timestampMs = 5_000L, nowMs = 15_000L, ttlMs = ttl))
		assertFalse(isNotFoundCacheEntryValid(timestampMs = 5_000L, nowMs = 15_001L, ttlMs = ttl))
		assertFalse(isNotFoundCacheEntryValid(timestampMs = 0L, nowMs = 1_000L, ttlMs = ttl))
	}
}
