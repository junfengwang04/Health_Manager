package com.pbz.healthmanager.alarm

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object OppoAlarmPermissionGuide {
    private const val PREFS = "alarm_permission_guide_prefs"
    private const val KEY_GUIDED = "oppo_guided_v1"

    fun maybeGuide(context: Context) {
        if (!Build.MANUFACTURER.equals("OPPO", ignoreCase = true)) return
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (sp.getBoolean(KEY_GUIDED, false)) return
        val opened = openAutoStartSettings(context) ||
            openBatterySettings(context) ||
            openNotificationSettings(context) ||
            openAppDetails(context)
        if (opened) {
            sp.edit().putBoolean(KEY_GUIDED, true).apply()
        }
    }

    private fun openAutoStartSettings(context: Context): Boolean {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
        )
        return candidates.any { component ->
            runCatching {
                val intent = Intent().apply {
                    this.component = component
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
    }

    private fun openBatterySettings(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun openNotificationSettings(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun openAppDetails(context: Context): Boolean {
        return runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }
}
