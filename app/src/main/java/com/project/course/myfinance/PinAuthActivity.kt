package com.project.course.myfinance

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

class PinAuthActivity : AppCompatActivity() {
    private var attempts = 0
    private var timer: CountDownTimer? = null

    private var currentPin = ""
    private var isLocked = false
    private lateinit var tvPinDisplay: TextView
    private lateinit var savedPin: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_auth)

        tvPinDisplay = findViewById(R.id.tvPinDisplay)
        val tvError = findViewById<TextView>(R.id.tvError)

        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        val pin = prefs.getString("app_pin", null)

        if (pin == null) {
            proceed()
            return
        }
        savedPin = pin

        checkLockoutState(tvError)
        setupKeypad(tvError, prefs)
        setupBiometricAuth() // Одразу пропонуємо відбиток при вході
    }

    private fun setupKeypad(tvError: TextView, prefs: android.content.SharedPreferences) {
        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        numberButtons.forEach { id ->
            findViewById<View>(id).setOnClickListener { view ->
                if (!isLocked && currentPin.length < savedPin.length) {
                    val digit = (view as Button).text.toString()
                    currentPin += digit
                    updatePinDisplay()
                    tvError.visibility = View.GONE

                    if (currentPin.length == savedPin.length) {
                        verifyPin(tvError, prefs)
                    }
                }
            }
        }

        findViewById<View>(R.id.btnBackspace).setOnClickListener {
            if (!isLocked && currentPin.isNotEmpty()) {
                currentPin = currentPin.dropLast(1)
                updatePinDisplay()
            }
        }

        findViewById<View>(R.id.btnBiometric).setOnClickListener {
            if (!isLocked) setupBiometricAuth()
        }
    }

    private fun updatePinDisplay() {
        tvPinDisplay.text = "•".repeat(currentPin.length)
    }

    private fun verifyPin(tvError: TextView, prefs: android.content.SharedPreferences) {
        if (currentPin == savedPin) {
            prefs.edit().putInt("pin_attempts", 0)
                .putLong("last_active_time", System.currentTimeMillis()).apply()
            proceed()
        } else {
            currentPin = ""
            updatePinDisplay()
            attempts = prefs.getInt("pin_attempts", 0) + 1
            prefs.edit().putInt("pin_attempts", attempts).apply()

            if (attempts >= 3) {
                val lockTime = System.currentTimeMillis() + 60_000
                prefs.edit().putLong("pin_lock_time", lockTime).apply()
                checkLockoutState(tvError)
            } else {
                tvError.text = "Невірний PIN-код. Залишилось спроб: ${3 - attempts}"
                tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun checkLockoutState(tvError: TextView) {
        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        val lockTime = prefs.getLong("pin_lock_time", 0L)
        val currentTime = System.currentTimeMillis()

        if (currentTime < lockTime) {
            isLocked = true
            tvError.visibility = View.VISIBLE

            timer = object : CountDownTimer(lockTime - currentTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    tvError.text =
                        "Додаток заблоковано. Повторіть через ${millisUntilFinished / 1000} с."
                }

                override fun onFinish() {
                    isLocked = false
                    tvError.visibility = View.GONE
                    prefs.edit().putInt("pin_attempts", 0).apply()
                    attempts = 0
                }
            }.start()
        } else {
            isLocked = false
            tvError.visibility = View.GONE
            prefs.edit().putInt("pin_attempts", 0).apply()
            attempts = 0
        }
    }

    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("pin_attempts", 0)
                        .putLong("last_active_time", System.currentTimeMillis()).apply()
                    proceed()
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Розблокування MyFinances")
            .setSubtitle("Увійдіть за допомогою відбитка пальця")
            .setNegativeButtonText("Використати PIN-код")
            .build()

        biometricPrompt.authenticate(promptInfo)
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