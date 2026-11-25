package com.apkupdateross.util.play

import android.content.Context
import java.util.Locale

object PlayLocales {

    fun preferredLocale(context: Context): Locale =
        deviceLocales(context).firstOrNull() ?: Locale.US

    fun acceptLanguageHeader(context: Context): String =
        deviceLocales(context)
            .map { it.toLanguageTag() }
            .mapIndexed { index, tag ->
                if (index == 0) {
                    tag
                } else {
                    val quality = (10 - index).coerceAtLeast(1) / 10.0
                    "$tag;q=${String.format(Locale.US, "%.1f", quality)}"
                }
            }
            .joinToString(",")

    fun userLanguagesHeader(context: Context): String =
        deviceLocales(context)
            .map { it.toPlayLanguageTag() }
            .joinToString(",")

    fun deviceLanguageTags(context: Context): List<String> {
        val out = LinkedHashSet<String>()
        deviceLocales(context).forEach { locale ->
            out.add(locale.toPlayLanguageTag())
            out.add(locale.language)
        }
        return out.toList()
    }

    private fun deviceLocales(context: Context): List<Locale> {
        val locales = mutableListOf<Locale>()
        val configured = context.resources.configuration.locales
        for (i in 0 until configured.size()) {
            normalizeLocale(configured[i])?.let { locales.add(it) }
        }
        if (locales.isEmpty()) normalizeLocale(Locale.getDefault())?.let { locales.add(it) }
        return locales.distinctBy { it.toLanguageTag() }
    }

    private fun normalizeLocale(locale: Locale): Locale? {
        val language = locale.language.takeIf { it.isNotBlank() } ?: return null
        val country = locale.country.takeIf { it.isNotBlank() }
        return if (country == null) Locale(language) else Locale(language, country)
    }

    private fun Locale.toPlayLanguageTag(): String =
        if (country.isBlank()) language else "${language}_$country"
}
