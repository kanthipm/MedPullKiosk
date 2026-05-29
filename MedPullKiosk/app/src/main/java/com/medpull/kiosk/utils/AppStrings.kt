package com.medpull.kiosk.utils

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Resolves string resources using the application locale (respects LocaleManager). */
@Singleton
class AppStrings @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun get(@StringRes resId: Int): String = context.getString(resId)

    fun get(@StringRes resId: Int, vararg formatArgs: Any): String =
        context.getString(resId, *formatArgs)
}
