package com.apkupdateross

import com.apkupdateross.util.LocaleHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class LocaleHelperTest {

	@Test
	fun normalizesLanguageTagsAndIndices() {
		assertEquals("", LocaleHelper.normalizeTag("  "))
		assertEquals("en", LocaleHelper.normalizeTag("EN"))
		assertEquals("ru", LocaleHelper.normalizeTag(" ru "))
		assertEquals("", LocaleHelper.normalizeTag("fr"))
		assertEquals(0, LocaleHelper.indexForTag(""))
		assertEquals(1, LocaleHelper.indexForTag("en"))
		assertEquals(2, LocaleHelper.indexForTag("ru"))
		assertEquals("", LocaleHelper.tagForIndex(0))
		assertEquals("en", LocaleHelper.tagForIndex(1))
		assertEquals("ru", LocaleHelper.tagForIndex(2))
	}
}
