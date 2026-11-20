package com.ramban.focus

import android.app.AppOpsManager
import android.app.TimePickerDialog
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var tvStatus: TextView
    private lateinit var btnStartTime: Button
    private lateinit var btnEndTime: Button
    private lateinit var btnStopFocus: Button
    private lateinit var switchStrictMode: SwitchMaterial
    private lateinit var containerApps: LinearLayout

    private var startHour = -1
    private var startMin = -1
    private var endHour = -1
    private var endMin = -1
    private var uiTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("ramban_prefs", Context.MODE_PRIVATE)
        tvStatus = findViewById(R.id.tvStatus)
        btnStartTime = findViewById(R.id.btnStartTime)
        btnEndTime = findViewById(R.id.btnEndTime)
        btnStopFocus = findViewById(R.id.btnStopFocus)
        switchStrictMode = findViewById(R.id.switchStrictMode)
        containerApps = findViewById(R.id.containerApps)

        val etMinutes = findViewById<EditText>(R.id.etCustomMinutes)
        switchStrictMode.isChecked = prefs.getBoolean("strict_mode", true)
        switchStrictMode.setOnCheckedChangeListener { _, isChecked ->
            val activeStrict = AppBlockerService.isSessionActive(prefs) && prefs.getBoolean("strict_mode", true)
            if (activeStrict && !isChecked) {
                switchStrictMode.isChecked = true
                Toast.makeText(this, "Strict Mode cannot be turned OFF during an active session!", Toast.LENGTH_SHORT).show()
            } else {
                prefs.edit().putBoolean("strict_mode", isChecked).apply()
                refreshSessionUI()
            }
        }

        findViewById<Button>(R.id.btnPermUsage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<Button>(R.id.btnPermAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnPermOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnPermAdmin).setOnClickListener {
            val adminComponent = ComponentName(this, RambanAdminReceiver::class.java)
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstalling Ramban during study sessions.")
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.btnPreset30).setOnClickListener { etMinutes.setText("30") }
        findViewById<Button>(R.id.btnPreset60).setOnClickListener { etMinutes.setText("60") }
        findViewById<Button>(R.id.btnPreset120).setOnClickListener { etMinutes.setText("120") }

        btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                startHour = h
                startMin = m
                btnStartTime.text = String.format("Start Time: %02d:%02d", h, m)
            }, 9, 0, true).show()
        }

        btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                endHour = h
                endMin = m
                btnEndTime.text = String.format("End Time: %02d:%02d", h, m)
            }, 13, 0, true).show()
        }

        findViewById<Button>(R.id.btnStartFocus).setOnClickListener {
            val minutesText = etMinutes.text.toString().trim()
            val customMins = minutesText.toIntOrNull()

            var startedAny = false
            if (customMins != null && customMins > 0) {
                val endMs = System.currentTimeMillis() + customMins * 60_000L
                prefs.edit()
                    .putBoolean("countdown_active", true)
                    .putLong("countdown_end_ms", endMs)
                    .apply()
                startedAny = true
            }

            if (startHour >= 0 && endHour >= 0) {
                prefs.edit()
                    .putBoolean("schedule_enabled", true)
                    .putInt("schedule_start_hour", startHour)
                    .putInt("schedule_start_min", startMin)
                    .putInt("schedule_end_hour", endHour)
                    .putInt("schedule_end_min", endMin)
                    .apply()
                startedAny = true
            }

            if (!startedAny) {
                Toast.makeText(this, "Enter countdown minutes or pick Start & End schedule time.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit().putBoolean("strict_mode", switchStrictMode.isChecked).apply()
            Toast.makeText(this, "Focus session started!", Toast.LENGTH_SHORT).show()
            refreshSessionUI()
        }

        btnStopFocus.setOnClickListener {
            if (AppBlockerService.isSessionActive(prefs) && prefs.getBoolean("strict_mode", true)) {
                Toast.makeText(this, "Strict Mode ON: You cannot stop until the timer finishes!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            prefs.edit()
                .putBoolean("countdown_active", false)
                .putBoolean("schedule_enabled", false)
                .apply()
            refreshSessionUI()
        }

        findViewById<Button>(R.id.btnRefreshUsage).setOnClickListener {
            loadUsageAndApps()
        }

        loadUsageAndApps()
        refreshSessionUI()
    }

    override fun onResume() {
        super.onResume()
        refreshSessionUI()
    }

    private fun hasUsagePermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refreshSessionUI() {
        uiTimer?.cancel()
        val active = AppBlockerService.isSessionActive(prefs)
        val strict = prefs.getBoolean("strict_mode", true)
        val remaining = AppBlockerService.getRemainingMillis(prefs)

        btnStopFocus.isEnabled = !(active && strict)

        if (active && remaining > 0L) {
            uiTimer = object : CountDownTimer(remaining, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    val modeLabel = if (strict) "STRICT" else "NORMAL"
                    tvStatus.text = "Status: ACTIVE ($modeLabel) — ${AppBlockerService.formatDuration(millisUntilFinished)}"
                }

                override fun onFinish() {
                    refreshSessionUI()
                }
            }.start()
        } else if (active) {
            tvStatus.text = "Status: ACTIVE (Scheduled Window)"
        } else {
            tvStatus.text = "Status: Ready"
        }
    }

    private fun loadUsageAndApps() {
        containerApps.removeAllViews()
        if (!hasUsagePermission()) {
            val tv = TextView(this).apply {
                text = "Grant Usage Access first to see today's app screen time."
            }
            containerApps.addView(tv)
            return
        }

        val usageManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val statsList = usageManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        ) ?: emptyList()

        val usageMap = HashMap<String, Long>()
        for (u in statsList) {
            if (u.totalTimeInForeground > 0L) {
                usageMap[u.packageName] = (usageMap[u.packageName] ?: 0L) + u.totalTimeInForeground
            }
        }

        val pm = packageManager
        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveList = pm.queryIntentActivities(launchIntent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedByDescending { usageMap[it.activityInfo.packageName] ?: 0L }

        val savedBlocked = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()

        for (resolveInfo in resolveList) {
            val pkg = resolveInfo.activityInfo.packageName
            val appLabel = resolveInfo.loadLabel(pm).toString()
            val mins = (usageMap[pkg] ?: 0L) / 60_000L

            val cb = CheckBox(this).apply {
                text = "$appLabel — ${mins}m today"
                isChecked = savedBlocked.contains(pkg)
                setOnCheckedChangeListener { _, isChecked ->
                    val current = prefs.getStringSet("blocked_apps", emptySet())?.toMutableSet() ?: mutableSetOf()
                    if (isChecked) current.add(pkg) else current.remove(pkg)
                    prefs.edit().putStringSet("blocked_apps", current).apply()
                }
            }
            containerApps.addView(cb)
        }
    }

    override fun onDestroy() {
        uiTimer?.cancel()
        super.onDestroy()
    }
}
