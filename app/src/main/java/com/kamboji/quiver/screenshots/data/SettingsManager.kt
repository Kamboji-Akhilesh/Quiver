package com.kamboji.quiver.screenshots.data

import android.content.Context
import androidx.core.content.edit
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.data.models.DelayUnit

object SettingsManager {

    private const val PREF = "settings"
    private const val KEY_VALUE = "delay_value"
    private const val KEY_UNIT = "delay_unit"
    private const val KEY_SERVICE_ENABLED = "service_enabled"

    fun saveDelay(context: Context, delay: DeleteDelay) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putLong(KEY_VALUE, delay.value)
                    .putString(KEY_UNIT, delay.unit.name)
            }
    }

    fun getDelay(context: Context): DeleteDelay {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val value = prefs.getLong(KEY_VALUE, 2) // DEFAULT = 2
        val unit = prefs.getString(KEY_UNIT, DelayUnit.MINUTES.name)!!
        return DeleteDelay(value, DelayUnit.valueOf(unit))
    }

    fun saveServiceEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit {
                putBoolean(KEY_SERVICE_ENABLED, enabled)
            }
    }

    fun isServiceEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SERVICE_ENABLED, true) // Default enabled
    }
}

