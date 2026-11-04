package com.project.course.myfinance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class PinAuthActivity : AppCompatActivity() {
    private var attempts = 0
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_auth)

        val etPin = findViewById<TextInputEditText>(R.id.etPin)

        etPin.transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()

        val tvError = findViewById<TextView>(R.id.tvError)

        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        val savedPin = prefs.getString("app_pin", null)

        if (savedPin == null) {
            proceed()
            return
        }

        checkLockoutState(etPin, tvError)

        etPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()

                if (tvError.visibility == View.VISIBLE && input.length < savedPin.length) {
                    tvError.visibility = View.GONE
                }

                if (input.length == savedPin.length) {
                    if (input == savedPin) {
                        prefs.edit().putInt("pin_attempts", 0)
                            .putLong("last_active_time", System.currentTimeMillis()).apply()
                        proceed()
                    } else {
                        etPin.text?.clear()
                        attempts = prefs.getInt("pin_attempts", 0) + 1
                        prefs.edit().putInt("pin_attempts", attempts).apply()

                        if (attempts >= 3) {
                            val lockTime = System.currentTimeMillis() + 60_000
                            prefs.edit().putLong("pin_lock_time", lockTime).apply()
                            checkLockoutState(etPin, tvError)
                        } else {
                            tvError.text = "Невірний PIN-код. Залишилось спроб: ${3 - attempts}"
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            }
        })
    }

    private fun checkLockoutState(etPin: TextInputEditText, tvError: TextView) {
        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        val lockTime = prefs.getLong("pin_lock_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime < lockTime) {
            etPin.isEnabled = false
            tvError.visibility = View.VISIBLE

            timer = object : CountDownTimer(lockTime - currentTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    tvError.text =
                        "Цей додаток заблоковано. Повторіть спробу через ${millisUntilFinished / 1000} с."
                }

                override fun onFinish() {
                    etPin.isEnabled = true
                    tvError.visibility = View.GONE
                    prefs.edit().putInt("pin_attempts", 0).apply()
                    attempts = 0
                }
            }.start()
        } else {
            etPin.isEnabled = true
            tvError.visibility = View.GONE
            prefs.edit().putInt("pin_attempts", 0).apply()
            attempts = 0
        }
    }

    private fun proceed() {
        if (intent.getBooleanExtra("EXTRA_FROM_BACKGROUND", false)) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}