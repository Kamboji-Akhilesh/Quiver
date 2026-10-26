package com.kamboji.quiver

import android.app.Application
import com.kamboji.quiver.ui.locale.Strings

/**
 * Runs before any activity, service, receiver or widget: makes app-language
 * strings ([com.kamboji.quiver.ui.locale.tr]) available everywhere, including
 * notifications and widgets that render while no screen is open.
 */
class QuiverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.attach(this)
    }
}
