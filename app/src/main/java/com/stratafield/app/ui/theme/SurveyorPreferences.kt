package com.stratafield.app.ui.theme

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** App-wide default name used to prefill surveyor fields in newly filled forms. */
object SurveyorPreferences {
    private const val PREFS_NAME = "strata_prefs"
    private const val KEY_SURVEYOR_NAME = "default_surveyor_name"

    @Volatile
    private var nameFlow: MutableStateFlow<String>? = null

    private fun flow(context: Context): MutableStateFlow<String> = nameFlow ?: synchronized(this) {
        nameFlow ?: run {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            MutableStateFlow(prefs.getString(KEY_SURVEYOR_NAME, "").orEmpty()).also { nameFlow = it }
        }
    }

    fun surveyorName(context: Context): StateFlow<String> = flow(context)

    fun getName(context: Context): String = flow(context).value

    fun setName(context: Context, name: String) {
        val cleanName = name.trim()
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SURVEYOR_NAME, cleanName).apply()
        flow(context).value = cleanName
    }
}
