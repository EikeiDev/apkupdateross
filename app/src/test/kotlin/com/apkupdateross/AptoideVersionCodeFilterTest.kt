package com.apkupdateross

import com.apkupdateross.repository.isAptoideEligibleVersionCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AptoideVersionCodeFilterTest {

	@Test
	fun rejectsOverflowAndNonPositiveVersionCodes() {
		assertFalse(isAptoideEligibleVersionCode(Long.MAX_VALUE))
		assertFalse(isAptoideEligibleVersionCode(0L))
		assertFalse(isAptoideEligibleVersionCode(-1L))
		assertTrue(isAptoideEligibleVersionCode(1L))
		assertTrue(isAptoideEligibleVersionCode(1310241503L))
	}
}
