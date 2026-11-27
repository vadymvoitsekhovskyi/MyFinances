package com.project.course.myfinance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object AppUpdateManager {
    private const val GITHUB_USER = "vadymvoitsekhovskyi"
    private const val GITHUB_REPO = "MyFinances"

    private var hasCheckedThisSession = false
    private var pendingApkFile: File? = null

    private data class ReleaseInfo(
        val versionName: String,
        val changelog: String,
        val apkDownloadUrl: String
    )

    fun checkForUpdates(activity: Activity) {
        if (hasCheckedThisSession) return
        hasCheckedThisSession = true

        val lifecycleOwner = activity as? LifecycleOwner ?: return

        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val release = fetchLatestRelease() ?: return@launch
                val currentVersion = getInstalledVersion(activity)

                if (isNewerVersion(release.versionName, currentVersion)) {
                    withContext(Dispatchers.Main) {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            showUpdatePromptDialog(activity, release)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    fun resumePendingInstallIfPermitted(activity: Activity) {
        val apk = pendingApkFile ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingApkFile = null
            launchApkInstaller(activity, apk)
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        val apiUrl = "https://api.github.com/repos/$GITHUB_USER/$GITHUB_REPO/releases/latest"
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MyFinances-Android")
            connectTimeout = 8000
            readTimeout = 8000
        }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val rawTag = json.optString("tag_name", "").removePrefix("v").trim()
            val changelog = json.optString("body", "").trim()
            val assets = json.optJSONArray("assets") ?: return null

            var apkUrl: String? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url", "")
                    break
                }
            }

            if (rawTag.isBlank() || apkUrl.isNullOrBlank()) null
            else ReleaseInfo(rawTag, changelog, apkUrl)
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdatePromptDialog(activity: Activity, release: ReleaseInfo) {
        val message = buildString {
            append("Доступна нова версія ${release.versionName}.")
            if (release.changelog.isNotBlank()) {
                append("\n\nЩо нового:\n${release.changelog}")
            }
        }

        AlertDialog.Builder(activity)
            .setTitle("Оновлення MyFinances")
            .setMessage(message)
            .setPositiveButton("Оновити") { _, _ ->
                downloadAndInstall(activity, release)
            }
            .setNegativeButton("Пізніше", null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, release: ReleaseInfo) {
        val lifecycleOwner = activity as? LifecycleOwner ?: return
        val density = activity.resources.displayMetrics.density

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (20 * density).toInt(),
                (24 * density).toInt(),
                (16 * density).toInt()
            )
        }

        val progressBar =
            ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = 0
            }

        val tvStatus = TextView(activity).apply {
            text = "Завантаження: 0%"
            textSize = 14f
            setPadding(0, (10 * density).toInt(), 0, 0)
        }

        container.addView(progressBar)
        container.addView(tvStatus)

        val progressDialog = AlertDialog.Builder(activity)
            .setTitle("Завантаження v${release.versionName}")
            .setView(container)
            .setCancelable(false)
            .create()

        progressDialog.show()

        lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val targetDir = File(activity.cacheDir, "shared_backups").apply {
                    if (!exists()) mkdirs()
                }
                val apkFile = File(targetDir, "MyFinances-update.apk")

                downloadWithRedirects(release.apkDownloadUrl, apkFile) { percent ->
                    activity.runOnUiThread {
                        if (percent >= 0) {
                            progressBar.isIndeterminate = false
                            progressBar.progress = percent
                            tvStatus.text = "Завантаження: $percent%"
                        } else {
                            progressBar.isIndeterminate = true
                            tvStatus.text = "Завантаження..."
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    installApkWithPermissionCheck(activity, apkFile)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (progressDialog.isShowing) progressDialog.dismiss()
                    Toast.makeText(
                        activity,
                        "Помилка завантаження: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun downloadWithRedirects(
        startUrl: String,
        destination: File,
        onProgress: (Int) -> Unit
    ) {
        var currentUrl = startUrl
        var redirects = 0
        lateinit var connection: HttpURLConnection

        while (redirects < 6) {
            connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MyFinances-Android")
                connectTimeout = 15000
                readTimeout = 30000
            }
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val next = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                currentUrl = next
                redirects++
            } else {
                break
            }
        }

        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong
            var downloadedBytes = 0L

            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        if (totalBytes > 0) {
                            val percent = ((downloadedBytes * 100) / totalBytes).toInt()
                            onProgress(percent)
                        } else {
                            onProgress(-1)
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun installApkWithPermissionCheck(activity: Activity, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            pendingApkFile = apkFile
            AlertDialog.Builder(activity)
                .setTitle("Потрібен дозвіл")
                .setMessage("Надайте MyFinances одноразовий дозвіл на встановлення оновлень у налаштуваннях Android.")
                .setPositiveButton("Налаштування") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .setNegativeButton("Скасувати") { _, _ ->
                    pendingApkFile = null
                }
                .show()
            return
        }

        launchApkInstaller(activity, apkFile)
    }

    private fun launchApkInstaller(activity: Activity, apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        activity.startActivity(installIntent)
    }

    private fun getInstalledVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            (info.versionName ?: "1.0")
                .replace(Regex("\\s*\\(.*\\)"), "")
                .removePrefix("v")
                .trim()
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = remote.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val currentParts = current.split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}