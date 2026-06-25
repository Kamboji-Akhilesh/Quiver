package com.kamboji.quiver.screenshots.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.kamboji.quiver.R
import com.kamboji.quiver.screenshots.data.SettingsManager
import com.kamboji.quiver.screenshots.data.ThemeMode
import com.kamboji.quiver.screenshots.data.db.AppDatabase
import com.kamboji.quiver.screenshots.data.models.DeleteDelay
import com.kamboji.quiver.screenshots.data.models.DelayUnit
import com.kamboji.quiver.screenshots.service.ScreenshotService
import com.kamboji.quiver.screenshots.worker.CleanupWorker
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.launch

private const val KEY_PROMPTED_ALL_FILES = "prompted_all_files"

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var statusIcon: ImageView
    private lateinit var historySubtitle: TextView
    private lateinit var themeSubtitle: TextView
    private lateinit var permissionCard: MaterialCardView
    private lateinit var chipGroup: ChipGroup
    private lateinit var themeChipGroup: ChipGroup
    private lateinit var serviceToggle: MaterialSwitch

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme before super.onCreate
        SettingsManager.applyTheme(SettingsManager.getTheme(this))

        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContentView(R.layout.activity_main)

        // Initialize views
        statusText = findViewById(R.id.statusText)
        statusIcon = findViewById(R.id.statusIcon)
        historySubtitle = findViewById(R.id.historySubtitle)
        themeSubtitle = findViewById(R.id.themeSubtitle)
        permissionCard = findViewById(R.id.permissionCard)
        chipGroup = findViewById(R.id.delayChipGroup)
        themeChipGroup = findViewById(R.id.themeChipGroup)
        serviceToggle = findViewById(R.id.serviceToggle)

        // Start the foreground service if enabled
        val isServiceEnabled = SettingsManager.isServiceEnabled(this)
        serviceToggle.isChecked = isServiceEnabled
        if (isServiceEnabled) {
            startScreenshotService()
        }

        // Service toggle listener
        serviceToggle.setOnCheckedChangeListener { _, isChecked ->
            SettingsManager.saveServiceEnabled(this, isChecked)
            if (isChecked) {
                startScreenshotService()
                statusText.text = "Monitoring active"
                statusIcon.alpha = 1f
            } else {
                stopScreenshotService()
                statusText.text = "Monitoring paused"
                statusIcon.alpha = 0.5f
            }
        }

        // History card click
        findViewById<MaterialCardView>(R.id.historyCard).setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // Grant permission button
        findViewById<View>(R.id.grantPermissionBtn).setOnClickListener {
            requestStoragePermission()
        }

        // Load saved delay and set chip
        val saved = SettingsManager.getDelay(this)
        val chipId = when (saved) {
            DeleteDelay(2, DelayUnit.MINUTES) -> R.id.min2
            DeleteDelay(5, DelayUnit.MINUTES) -> R.id.min5
            DeleteDelay(30, DelayUnit.MINUTES) -> R.id.min30
            DeleteDelay(1, DelayUnit.HOURS) -> R.id.hr1
            DeleteDelay(5, DelayUnit.HOURS) -> R.id.hr5
            DeleteDelay(10, DelayUnit.HOURS) -> R.id.hr10
            DeleteDelay(1, DelayUnit.DAYS) -> R.id.day1
            DeleteDelay(2, DelayUnit.DAYS) -> R.id.day2
            else -> R.id.min2
        }
        chipGroup.check(chipId)

        // Chip selection listener
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val delay = when (checkedIds[0]) {
                    R.id.min2 -> DeleteDelay(2, DelayUnit.MINUTES)
                    R.id.min5 -> DeleteDelay(5, DelayUnit.MINUTES)
                    R.id.min30 -> DeleteDelay(30, DelayUnit.MINUTES)
                    R.id.hr1 -> DeleteDelay(1, DelayUnit.HOURS)
                    R.id.hr5 -> DeleteDelay(5, DelayUnit.HOURS)
                    R.id.hr10 -> DeleteDelay(10, DelayUnit.HOURS)
                    R.id.day1 -> DeleteDelay(1, DelayUnit.DAYS)
                    R.id.day2 -> DeleteDelay(2, DelayUnit.DAYS)
                    else -> DeleteDelay(2, DelayUnit.MINUTES)
                }
                SettingsManager.saveDelay(this, delay)
            }
        }

        // Load saved theme and set chip
        val savedTheme = SettingsManager.getTheme(this)
        val themeChipId = when (savedTheme) {
            ThemeMode.SYSTEM -> R.id.themeSystem
            ThemeMode.LIGHT -> R.id.themeLight
            ThemeMode.DARK -> R.id.themeDark
        }
        themeChipGroup.check(themeChipId)
        updateThemeSubtitle(savedTheme)

        // Theme chip selection listener
        themeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val theme = when (checkedIds[0]) {
                    R.id.themeLight -> ThemeMode.LIGHT
                    R.id.themeDark -> ThemeMode.DARK
                    else -> ThemeMode.SYSTEM
                }
                SettingsManager.saveTheme(this, theme)
                SettingsManager.applyTheme(theme)
                updateThemeSubtitle(theme)
            }
        }

        // Check permission on first launch
        checkStoragePermission()

        // Ensure old trash is pruned on a schedule, not only when History opens.
        CleanupWorker.schedule(this)

        // Proactively request permissions instead of making the user dig through
        // app settings (issue #2).
        requestPermissionsOnLaunch()
    }

    /**
     * Requests the runtime permissions (notifications, media read) up front, and
     * auto-opens the "All files access" prompt once on first launch — rather
     * than requiring the user to enable everything manually in settings.
     */
    private fun requestPermissionsOnLaunch() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!granted(Manifest.permission.POST_NOTIFICATIONS)) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (!granted(Manifest.permission.READ_MEDIA_IMAGES)) {
                    add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())

        // "All files access" can't be a runtime dialog (OS opens a settings
        // screen), so auto-prompt it once on first launch.
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val needsAllFiles = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            !Environment.isExternalStorageManager()
        if (needsAllFiles && !prefs.getBoolean(KEY_PROMPTED_ALL_FILES, false)) {
            prefs.edit().putBoolean(KEY_PROMPTED_ALL_FILES, true).apply()
            requestStoragePermission()
        }
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED

    private fun startScreenshotService() {
        val intent = Intent(this, ScreenshotService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopScreenshotService() {
        val intent = Intent(this, ScreenshotService::class.java)
        stopService(intent)
    }

    private fun updateThemeSubtitle(theme: ThemeMode) {
        themeSubtitle.text = when (theme) {
            ThemeMode.SYSTEM -> "System default"
            ThemeMode.LIGHT -> "Light mode"
            ThemeMode.DARK -> "Dark mode"
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                permissionCard.visibility = View.VISIBLE
            } else {
                permissionCard.visibility = View.GONE
            }
        } else {
            permissionCard.visibility = View.GONE
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Enable Auto-Delete")
                .setMessage("To automatically delete screenshots without confirmation prompts, please grant 'All files access' permission.\n\nThis allows the app to delete screenshots silently in the background.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                }
                .setNegativeButton("Maybe Later", null)
                .show()
        }
    }

    private fun updateStatus() {
        lifecycleScope.launch {
            val historyCount = AppDatabase.getDatabase(this@MainActivity).historyDao().getCount()
            val isServiceEnabled = SettingsManager.isServiceEnabled(this@MainActivity)

            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }

            if (!isServiceEnabled) {
                statusText.text = "Monitoring paused"
                statusIcon.alpha = 0.5f
            } else if (hasPermission) {
                statusText.text = "Auto-delete enabled • Monitoring active"
                statusIcon.alpha = 1f
            } else {
                statusText.text = "Manual confirmation required"
                statusIcon.alpha = 1f
            }

            serviceToggle.isChecked = isServiceEnabled

            historySubtitle.text = if (historyCount > 0) {
                "$historyCount screenshots in trash"
            } else {
                "View and restore deleted screenshots"
            }

            // Update permission card visibility
            permissionCard.visibility = if (!hasPermission) View.VISIBLE else View.GONE
        }
    }
}

