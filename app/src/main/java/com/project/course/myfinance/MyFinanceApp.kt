package com.project.course.myfinance

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

class MyFinanceApp : Application() {
    private var activitiesCount = 0
    private var currentActivity: Activity? = null
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        val pin = prefs.getString("app_pin", null)
        if (pin != null && currentActivity != null && currentActivity !is PinAuthActivity && currentActivity !is SplashActivity) {
            val intent = Intent(currentActivity, PinAuthActivity::class.java)
            intent.putExtra("EXTRA_FROM_BACKGROUND", true)
            currentActivity?.startActivity(intent)
        }
    }

    fun resetInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
        val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
        if (prefs.getString("app_pin", null) != null) {
            inactivityHandler.postDelayed(inactivityRunnable, 300_000)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                currentActivity = activity
                if (activitiesCount == 0) {
                    val prefs = getSharedPreferences("pin_prefs", Context.MODE_PRIVATE)
                    val pin = prefs.getString("app_pin", null)
                    val lastActive = prefs.getLong("last_active_time", 0L)
                    val currentTime = System.currentTimeMillis()

                    if (pin != null && (currentTime - lastActive > 300_000)
                        && activity !is PinAuthActivity && activity !is SplashActivity
                    ) {
                        val intent = Intent(activity, PinAuthActivity::class.java)
                        intent.putExtra("EXTRA_FROM_BACKGROUND", true)
                        activity.startActivity(intent)
                    }
                }
                activitiesCount++
                resetInactivityTimer()
            }

            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
                resetInactivityTimer()
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity == activity) {
                    currentActivity = null
                }
            }

            override fun onActivityStopped(activity: Activity) {
                activitiesCount--
                if (activitiesCount == 0) {
                    getSharedPreferences("pin_prefs", Context.MODE_PRIVATE).edit()
                        .putLong("last_active_time", System.currentTimeMillis()).apply()
                    inactivityHandler.removeCallbacks(inactivityRunnable)
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}