package com.apkupdateross.util

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LocaleHelper {
	const val LANG_SYSTEM = ""
	const val LANG_EN = "en"
	const val LANG_RU = "ru"

	fun applyLanguage(context: Context, languageTag: String) {
		val normalized = normalizeTag(languageTag)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			val manager = context.getSystemService(LocaleManager::class.java)
			manager?.applicationLocales = if (normalized.isEmpty()) {
				LocaleList.getEmptyLocaleList()
			} else {
				LocaleList.forLanguageTags(normalized)
			}
		} else {
			val locale = if (normalized.isEmpty()) Locale.getDefault() else Locale.forLanguageTag(normalized)
			Locale.setDefault(locale)
		}
	}

	fun wrapContext(base: Context, languageTag: String): Context {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
		val normalized = normalizeTag(languageTag)
		if (normalized.isEmpty()) return base
		val locale = Locale.forLanguageTag(normalized)
		Locale.setDefault(locale)
		val config = Configuration(base.resources.configuration)
		config.setLocale(locale)
		return base.createConfigurationContext(config)
	}

	fun normalizeTag(tag: String): String = when (tag.trim().lowercase(Locale.ROOT)) {
		LANG_EN -> LANG_EN
		LANG_RU -> LANG_RU
		else -> LANG_SYSTEM
	}

	fun indexForTag(tag: String): Int = when (normalizeTag(tag)) {
		LANG_EN -> 1
		LANG_RU -> 2
		else -> 0
	}

	fun tagForIndex(index: Int): String = when (index) {
		1 -> LANG_EN
		2 -> LANG_RU
		else -> LANG_SYSTEM
	}
}
