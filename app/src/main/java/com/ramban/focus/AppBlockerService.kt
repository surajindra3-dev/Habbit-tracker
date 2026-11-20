package com.ramban.focus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent
import java.util.Calendar

class AppBlockerService : AccessibilityService() {

    private var lastBlockedTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkgName = event.packageName?.toString() ?: return

        if (pkgName == packageName) return

        val prefs = getSharedPreferences("ramban_prefs", Context.MODE_PRIVATE)
        if (!isSessionActive(prefs)) return

        val strictMode = prefs.getBoolean("strict_mode", true)
        val blockedApps = prefs.getStringSet("blocked_apps", emptySet()) ?: emptySet()

        val isSettingsApp = strictMode && (
            pkgName == "com.android.settings" ||
                pkgName == "com.miui.securitycenter" ||
                pkgName == "com.android.packageinstaller" ||
                pkgName == "com.google.android.packageinstaller"
            )

        if (blockedApps.contains(pkgName) || isSettingsApp) {
            val now = System.currentTimeMillis()
            if (now - lastBlockedTime < 700L) return
            lastBlockedTime = now

            val intent = Intent(this, BlockOverlayActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
                putExtra("blocked_package", if (isSettingsApp) "Settings (Strict Lock)" else pkgName)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}

    companion object {
        fun isCountdownActive(prefs: SharedPreferences): Boolean {
            val active = prefs.getBoolean("countdown_active", false)
            if (!active) return false
            val endTime = prefs.getLong("countdown_end_ms", 0L)
            return if (System.currentTimeMillis() < endTime) {
                true
            } else {
                prefs.edit().putBoolean("countdown_active", false).apply()
                false
            }
        }

        fun isScheduleActive(prefs: SharedPreferences): Boolean {
            val enabled = prefs.getBoolean("schedule_enabled", false)
            if (!enabled) return false

            val startHour = prefs.getInt("schedule_start_hour", -1)
            val startMin = prefs.getInt("schedule_start_min", -1)
            val endHour = prefs.getInt("schedule_end_hour", -1)
            val endMin = prefs.getInt("schedule_end_min", -1)
            if (startHour < 0 || endHour < 0) return false

            val nowCal = Calendar.getInstance()
            val nowMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)
            val startMinutes = startHour * 60 + startMin
            val endMinutes = endHour * 60 + endMin

            return if (startMinutes <= endMinutes) {
                nowMinutes in startMinutes..endMinutes
            } else {
                nowMinutes >= startMinutes || nowMinutes <= endMinutes
            }
        }

        fun isSessionActive(prefs: SharedPreferences): Boolean {
            return isCountdownActive(prefs) || isScheduleActive(prefs)
        }

        fun getRemainingMillis(prefs: SharedPreferences): Long {
            if (!isCountdownActive(prefs)) return 0L
            val endTime = prefs.getLong("countdown_end_ms", 0L)
            return (endTime - System.currentTimeMillis()).coerceAtLeast(0L)
        }

        fun formatDuration(millis: Long): String {
            val totalSeconds = millis / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}

