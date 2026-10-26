package com.kamboji.quiver

import android.app.Application
import com.kamboji.quiver.focus.FocusGate
import com.kamboji.quiver.move.steps.Steps
import com.kamboji.quiver.ui.locale.Strings
import com.kamboji.quiver.ui.theme.ThemeSettings

/**
 * Runs before any activity, service, receiver or widget: makes string
 * resources ([com.kamboji.quiver.ui.locale.tr]) available everywhere, including
 * notifications and widgets that render while no screen is open.
 */
class QuiverApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Strings.attach(this)
        ThemeSettings.init(this)
        FocusGate.sync(this)
        Steps.start(this)
    }
}
