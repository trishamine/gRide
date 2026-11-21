package org.devbucket.gRide.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList

/**
 * The app's language, using Android's per-app language setting (Android 13+). Android stores the
 * choice itself, shows it under Settings → Apps → gRide → Language, and restarts the screen with it.
 */
object AppLanguage {
    /** Language tag → name shown in that language ("" = follow the phone). */
    val options = listOf("" to null, "en" to "English", "de" to "Deutsch", "ru" to "Русский")

    fun current(context: Context): String {
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        return if (locales == null || locales.isEmpty) "" else locales[0].language
    }

    fun set(context: Context, tag: String) {
        if (tag == current(context)) return
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}
