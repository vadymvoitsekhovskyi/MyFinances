package com.project.course.myfinance

import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.CountDownTimer
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.ceil

open class BaseNetworkActivity : AppCompatActivity() {
    private lateinit var contentContainer: View
    private lateinit var layoutNoInternet: FrameLayout
    private lateinit var btnRetryConnection: ImageButton
    private lateinit var tvRetryCountdown: TextView
    private var retryEnableTimer: CountDownTimer? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun setContentView(layoutResID: Int) {
        val rootContainer = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        contentContainer = layoutInflater.inflate(layoutResID, rootContainer, false)
        rootContainer.addView(contentContainer)

        val overlayView = layoutInflater.inflate(R.layout.layout_no_internet, rootContainer, false)
        rootContainer.addView(overlayView)

        super.setContentView(rootContainer)

        layoutNoInternet = overlayView.findViewById(R.id.layoutNoInternet)
        btnRetryConnection = overlayView.findViewById(R.id.btnRetryConnection)
        tvRetryCountdown = overlayView.findViewById(R.id.tvRetryCountdown)

        btnRetryConnection.setOnClickListener { retryManualReconnect() }
    }

    protected open fun onNetworkRestored() {}

    override fun onStart() {
        super.onStart()
        if (::layoutNoInternet.isInitialized) {
            registerNetworkMonitoring()
        }
    }

    override fun onStop() {
        super.onStop()
        retryEnableTimer?.cancel()
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {
            }
        }
        networkCallback = null
    }

    private fun hasActiveInternet(): Boolean {
        val cm = connectivityManager ?: return false
        val capabilities = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun registerNetworkMonitoring() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    val wasOffline = layoutNoInternet.visibility == View.VISIBLE
                    setNoInternetVisible(false)
                    if (wasOffline) {
                        Toast.makeText(
                            this@BaseNetworkActivity,
                            "З'єднання відновлено",
                            Toast.LENGTH_SHORT
                        ).show()
                        onNetworkRestored()
                    }
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    if (!hasActiveInternet()) {
                        setNoInternetVisible(true)
                    }
                }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        setNoInternetVisible(!hasActiveInternet())
    }

    private fun setNoInternetVisible(visible: Boolean) {
        val alreadyVisible = layoutNoInternet.visibility == View.VISIBLE
        layoutNoInternet.visibility = if (visible) View.VISIBLE else View.GONE

        if (visible) {
            if (!alreadyVisible) startRetryCooldownTimer()
        } else {
            retryEnableTimer?.cancel()
            btnRetryConnection.isEnabled = false
            btnRetryConnection.alpha = 0.35f
            tvRetryCountdown.visibility = View.INVISIBLE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            contentContainer.setRenderEffect(
                if (visible) {
                    RenderEffect.createBlurEffect(25f, 25f, Shader.TileMode.CLAMP)
                } else {
                    null
                }
            )
        }
    }

    private fun startRetryCooldownTimer() {
        retryEnableTimer?.cancel()
        btnRetryConnection.isEnabled = false
        btnRetryConnection.alpha = 0.35f
        tvRetryCountdown.visibility = View.VISIBLE

        retryEnableTimer = object : CountDownTimer(15_000L, 250L) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsLeft = ceil(millisUntilFinished / 1000.0).toInt()
                tvRetryCountdown.text = "Перепідключення через $secondsLeft с"
            }

            override fun onFinish() {
                if (layoutNoInternet.visibility == View.VISIBLE) {
                    btnRetryConnection.isEnabled = true
                    btnRetryConnection.alpha = 1.0f
                    tvRetryCountdown.visibility = View.INVISIBLE
                    Toast.makeText(
                        this@BaseNetworkActivity,
                        "Можна оновити вручну",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun retryManualReconnect() {
        btnRetryConnection.animate().rotationBy(360f).setDuration(450L).start()

        if (hasActiveInternet()) {
            setNoInternetVisible(false)
            Toast.makeText(this, "З'єднання відновлено", Toast.LENGTH_SHORT).show()
            onNetworkRestored()
        } else {
            Toast.makeText(this, "Немає з'єднання", Toast.LENGTH_SHORT).show()
            startRetryCooldownTimer()
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        (application as? MyFinanceApp)?.resetInactivityTimer()
        return super.dispatchTouchEvent(event)
    }
}