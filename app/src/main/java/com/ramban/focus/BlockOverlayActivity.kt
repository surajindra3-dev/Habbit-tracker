package com.ramban.focus

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class BlockOverlayActivity : AppCompatActivity() {

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block_overlay)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        val blockedPkg = intent.getStringExtra("blocked_package") ?: "App"
        val tvInfo = findViewById<TextView>(R.id.tvBlockedInfo)
        val tvRemaining = findViewById<TextView>(R.id.tvRemainingTime)
        val btnHome = findViewById<Button>(R.id.btnGoHome)

        tvInfo.text = "Blocked during focus session:\n$blockedPkg"

        val prefs = getSharedPreferences("ramban_prefs", Context.MODE_PRIVATE)
        val remainingMs = AppBlockerService.getRemainingMillis(prefs)

        if (remainingMs > 0L) {
            timer = object : CountDownTimer(remainingMs, 1000L) {
                override fun onTick(millisUntilFinished: Long) {
                    tvRemaining.text = "Remaining: ${AppBlockerService.formatDuration(millisUntilFinished)}"
                }

                override fun onFinish() {
                    tvRemaining.text = "Session Complete!"
                    finish()
                }
            }.start()
        } else {
            tvRemaining.text = "Scheduled Focus Active"
        }

        btnHome.setOnClickListener {
            goHome()
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
