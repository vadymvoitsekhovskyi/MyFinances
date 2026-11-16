package com.project.course.myfinance

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
        }

        val tvAppVersion = findViewById<TextView>(R.id.tvAppVersion)
        tvAppVersion.text = "Версія ${getAppVersion()}"
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        (application as? MyFinanceApp)?.resetInactivityTimer()
        return super.dispatchTouchEvent(event)
    }
}