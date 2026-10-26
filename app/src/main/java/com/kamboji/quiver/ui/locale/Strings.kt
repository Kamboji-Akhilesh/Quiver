package com.kamboji.quiver.ui.locale

import android.content.Context
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/**
 * Strings from anywhere — composables, click handlers, services, widgets.
 * `stringResource` only works inside composable scope; this resolves from the
 * application's resources, attached once in [com.kamboji.quiver.QuiverApplication].
 */
object Strings {
    @Volatile private var resources: Resources? = null

    fun attach(context: Context) {
        resources = context.applicationContext.resources
    }

    internal fun get(): Resources = checkNotNull(resources) { "Strings.attach(context) wasn't called" }
}

fun tr(@StringRes id: Int, vararg args: Any): String =
    if (args.isEmpty()) Strings.get().getString(id) else Strings.get().getString(id, *args)

/** A plural with the count as its only argument: `trPlural(R.plurals.duration_days, 3)` → "3 days". */
fun trPlural(@PluralsRes id: Int, count: Int): String = Strings.get().getQuantityString(id, count, count)
