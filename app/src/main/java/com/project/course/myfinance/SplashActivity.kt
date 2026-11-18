package com.project.course.myfinance

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.view.animation.PathInterpolator

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        playSplashAnimation()

        Handler(Looper.getMainLooper()).postDelayed({
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                val prefs = getSharedPreferences("pin_prefs", android.content.Context.MODE_PRIVATE)
                if (prefs.getString("app_pin", null) != null) {
                    startActivity(Intent(this, PinAuthActivity::class.java))
                } else {
                    startActivity(Intent(this, MainActivity::class.java))
                }
            } else {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            finish()
        }, 2500)
    }

    private fun playSplashAnimation() {
        val logo = findViewById<ImageView>(R.id.ivSplashLogo)
        val titleRow = findViewById<LinearLayout>(R.id.llSplashTitle)
        val risePx = 8f * resources.displayMetrics.density

        logo.apply {
            alpha = 0f
            scaleX = 0.9f
            scaleY = 0.9f
            translationY = risePx
        }

        logo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .translationY(0f)
            .setDuration(900L)
            .setInterpolator(DecelerateInterpolator(1.4f))
            .start()

        val letterInterpolator = PathInterpolator(0.22f, 0f, 0f, 1f)

        val appName = getString(R.string.app_name)
        titleRow.contentDescription = appName

        appName.forEachIndexed { index, character ->
            val letter = TextView(this).apply {
                text = character.toString()
                textSize = 52f
                setTextColor(getColor(R.color.text_primary))
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                alpha = 0f
                scaleX = 0.94f
                scaleY = 0.94f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }

            titleRow.addView(
                letter,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            letter.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(500L + index * 42L)
                .setDuration(520L)
                .setInterpolator(letterInterpolator)
                .start()
        }
    }
}