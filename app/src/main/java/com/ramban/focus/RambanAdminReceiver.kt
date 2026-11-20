package com.ramban.focus

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class RambanAdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val prefs = context.getSharedPreferences("ramban_prefs", Context.MODE_PRIVATE)
        val isActive = AppBlockerService.isSessionActive(prefs)
        val strict = prefs.getBoolean("strict_mode", true)
        return if (isActive && strict) {
            Toast.makeText(context, "Ramban Strict Mode is active! Cannot deactivate right now.", Toast.LENGTH_LONG).show()
            "Focus session is still running. Complete your study session first."
        } else {
            "Deactivating Ramban Device Admin."
        }
    }
}
