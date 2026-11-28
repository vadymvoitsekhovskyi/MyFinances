package com.project.course.myfinance

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object AppUpdateManager {
    private const val GITHUB_USER = "vadymvoitsekhovskyi"
    private const val GITHUB_REPO = "MyFinances"

    private const val PREFS_NAME = "app_update_prefs"
    private const val KEY_AUTO_CHECK_ENABLED = "auto_check_enabled"
    private const val KEY_IGNORED_VERSION = "ignored_version"

    private var hasCheckedThisSession = false
    private var pendingApkFile: File? = null

    private data class ReleaseInfo(
        val versionName: String,
        val originalChangelog: String,
        val translatedChangelog: String,
        val publishedDate: String,
        val apkDownloadUrl: String
    )

    fun isAutoCheckEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_CHECK_ENABLED, true)
    }

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTO_CHECK_ENABLED, enabled)
            .apply()
        if (enabled) {
            hasCheckedThisSession = false
        }
    }

    fun checkForUpdates(activity: Activity) {
        if (!isAutoCheckEnabled(activity)) return
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

    fun openUpdateSettingsDialog(activity: Activity) {
        val lifecycleOwner = activity as? LifecycleOwner ?: return
        val density = activity.resources.displayMetrics.density
        val currentVersion = getInstalledVersion(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (22 * density).toInt(),
                (16 * density).toInt(),
                (22 * density).toInt(),
                (8 * density).toInt()
            )
        }

        val switchRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (6 * density).toInt())
        }

        val tvSwitchLabel = TextView(activity).apply {
            text = "Автоматичні сповіщення про оновлення"
            textSize = 15f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (12 * density).toInt()
                }
        }

        val switchAutoUpdate = SwitchMaterial(activity).apply {
            isChecked = isAutoCheckEnabled(activity)
            thumbTintList =
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.dark_green))
        }

        switchRow.addView(tvSwitchLabel)
        switchRow.addView(switchAutoUpdate)
        root.addView(switchRow)

        val tvAutoHint = TextView(activity).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(activity, R.color.text_muted))
            text = if (switchAutoUpdate.isChecked) {
                "Застосунок перевірятиме нові версії при запуску."
            } else {
                "Автосповіщення вимкнено. Ви можете оновлювати вручну нижче."
            }
            setPadding(0, 0, 0, (14 * density).toInt())
        }
        root.addView(tvAutoHint)

        switchAutoUpdate.setOnCheckedChangeListener { _, isChecked ->
            setAutoCheckEnabled(activity, isChecked)
            tvAutoHint.text = if (isChecked) {
                "Застосунок перевірятиме нові версії при запуску."
            } else {
                "Автосповіщення вимкнено. Ви можете оновлювати вручну нижче."
            }
        }

        val infoCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBoxBackground(activity)
            setPadding(
                (14 * density).toInt(),
                (12 * density).toInt(),
                (14 * density).toInt(),
                (12 * density).toInt()
            )
        }

        val tvCurrentVersion = TextView(activity).apply {
            text = "Поточна версія: v$currentVersion"
            textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        }

        val tvLatestVersion = TextView(activity).apply {
            text = "Остання актуальна версія: перевірка..."
            textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        val tvUpdateStatus = TextView(activity).apply {
            textSize = 13f
            setPadding(0, (6 * density).toInt(), 0, 0)
            visibility = View.GONE
        }

        val pbChecking = ProgressBar(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                (26 * density).toInt(),
                (26 * density).toInt()
            ).apply {
                topMargin = (8 * density).toInt()
            }
        }

        infoCard.addView(tvCurrentVersion)
        infoCard.addView(tvLatestVersion)
        infoCard.addView(tvUpdateStatus)
        infoCard.addView(pbChecking)
        root.addView(infoCard)

        val tvDetailsHeader = TextView(activity).apply {
            text = "Деталі останнього оновлення:"
            textSize = 14f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, (14 * density).toInt(), 0, (6 * density).toInt())
            visibility = View.GONE
        }
        root.addView(tvDetailsHeader)

        val detailsScroll = ScrollView(activity).apply {
            background = createRoundedBoxBackground(activity)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (150 * density).toInt()
            )
            visibility = View.GONE
        }

        val tvChangelog = TextView(activity).apply {
            textSize = 13.5f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            setPadding(
                (14 * density).toInt(),
                (12 * density).toInt(),
                (14 * density).toInt(),
                (12 * density).toInt()
            )
        }

        detailsScroll.addView(tvChangelog)
        root.addView(detailsScroll)

        val tvToggleOriginal = TextView(activity).apply {
            text = "Показати оригінал коміту"
            textSize = 12f
            setTextColor(ContextCompat.getColor(activity, R.color.dark_green))
            setPadding(0, (6 * density).toInt(), 0, 0)
            visibility = View.GONE
        }
        root.addView(tvToggleOriginal)

        var loadedRelease: ReleaseInfo? = null
        var showingOriginal = false

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Оновлення застосунку")
            .setView(root)
            .setPositiveButton("Оновити", null)
            .setNegativeButton("Закрити", null)
            .create()

        fun loadReleaseData() {
            pbChecking.visibility = View.VISIBLE
            tvUpdateStatus.visibility = View.GONE
            tvLatestVersion.text = "Остання актуальна версія: перевірка..."
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE

            lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val release = try {
                    fetchLatestRelease()
                } catch (_: Exception) {
                    null
                }

                withContext(Dispatchers.Main) {
                    if (activity.isFinishing || activity.isDestroyed || !dialog.isShowing) return@withContext
                    pbChecking.visibility = View.GONE

                    if (release == null) {
                        tvLatestVersion.text = "Остання актуальна версія: невідомо"
                        tvUpdateStatus.visibility = View.VISIBLE
                        tvUpdateStatus.setTextColor(
                            ContextCompat.getColor(
                                activity,
                                R.color.dark_red
                            )
                        )
                        tvUpdateStatus.text =
                            "Не вдалося перевірити оновлення. Перевірте підключення до інтернету."

                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            visibility = View.VISIBLE
                            text = "Повторити"
                            setOnClickListener { loadReleaseData() }
                        }
                        return@withContext
                    }

                    loadedRelease = release
                    val dateSuffix =
                        if (release.publishedDate.isNotBlank()) " (${release.publishedDate})" else ""
                    tvLatestVersion.text =
                        "Остання актуальна версія: v${release.versionName}$dateSuffix"

                    val hasUpdate = isNewerVersion(release.versionName, currentVersion)
                    tvUpdateStatus.visibility = View.VISIBLE
                    if (hasUpdate) {
                        tvUpdateStatus.setTextColor(
                            ContextCompat.getColor(
                                activity,
                                R.color.dark_green
                            )
                        )
                        tvUpdateStatus.text = "\uD83D\uDD14 Доступне нове оновлення!"
                    } else {
                        tvUpdateStatus.setTextColor(
                            ContextCompat.getColor(
                                activity,
                                R.color.text_secondary
                            )
                        )
                        tvUpdateStatus.text = "У вас встановлена найновіша версія."
                    }

                    val detailsText = release.translatedChangelog.ifBlank { "Опис змін відсутній." }
                    tvDetailsHeader.visibility = View.VISIBLE
                    detailsScroll.visibility = View.VISIBLE
                    tvChangelog.text = detailsText

                    if (release.originalChangelog.isNotBlank() &&
                        release.originalChangelog != release.translatedChangelog
                    ) {
                        tvToggleOriginal.visibility = View.VISIBLE
                        tvToggleOriginal.setOnClickListener {
                            showingOriginal = !showingOriginal
                            if (showingOriginal) {
                                tvChangelog.text = release.originalChangelog
                                tvToggleOriginal.text = "Показати переклад українською"
                            } else {
                                tvChangelog.text = release.translatedChangelog
                                tvToggleOriginal.text = "Показати оригінал коміту"
                            }
                        }
                    } else {
                        tvToggleOriginal.visibility = View.GONE
                    }

                    if (hasUpdate) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                            visibility = View.VISIBLE
                            text = "Оновити"
                            setOnClickListener {
                                dialog.dismiss()
                                loadedRelease?.let { downloadAndInstall(activity, it) }
                            }
                        }
                    }
                }
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
            loadReleaseData()
        }

        dialog.show()
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
            val rawChangelog = json.optString("body", "").trim()
            val publishedRaw = json.optString("published_at", "").trim()
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

            if (rawTag.isBlank() || apkUrl.isNullOrBlank()) {
                null
            } else {
                val translated = translateToUkrainian(rawChangelog)
                val formattedDate = formatReleaseDate(publishedRaw)
                ReleaseInfo(
                    versionName = rawTag,
                    originalChangelog = rawChangelog,
                    translatedChangelog = translated,
                    publishedDate = formattedDate,
                    apkDownloadUrl = apkUrl
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun formatReleaseDate(isoDate: String): String {
        if (isoDate.isBlank()) return ""
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = parser.parse(isoDate) ?: return ""
            val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("Europe/Kyiv")
            }
            formatter.format(date)
        } catch (_: Exception) {
            ""
        }
    }

    private fun translateToUkrainian(text: String): String {
        if (text.isBlank()) return ""

        var cyrillicCount = 0
        var latinCount = 0
        for (ch in text) {
            when {
                ch in '\u0400'..'\u04FF' -> cyrillicCount++
                ch in 'A'..'Z' || ch in 'a'..'z' -> latinCount++
            }
        }
        if (latinCount == 0 || cyrillicCount > latinCount * 2) {
            return text
        }

        return try {
            val encoded = URLEncoder.encode(text, "UTF-8")
            val url =
                "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=uk&dt=t&q=$encoded"
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0")
                connectTimeout = 5000
                readTimeout = 5000
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return text
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val rootArray = JSONArray(response)
                val segments = rootArray.getJSONArray(0)
                val translated = StringBuilder()
                for (i in 0 until segments.length()) {
                    val part = segments.getJSONArray(i).optString(0, "")
                    translated.append(part)
                }
                translated.toString().trim().ifBlank { text }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            text
        }
    }

    private fun showUpdatePromptDialog(activity: Activity, release: ReleaseInfo) {
        val density = activity.resources.displayMetrics.density
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (24 * density).toInt(),
                (14 * density).toInt(),
                (24 * density).toInt(),
                (4 * density).toInt()
            )
        }

        val scrollView = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val messageText = buildString {
            append("\uD83D\uDD14 Доступна нова версія ${release.versionName}.")
            if (release.translatedChangelog.isNotBlank()) {
                append("\n\nЩо нового:\n${release.translatedChangelog}")
            }
        }

        val tvMessage = TextView(activity).apply {
            text = messageText
            textSize = 15f
            setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
        }
        scrollView.addView(tvMessage)
        container.addView(scrollView)

        val cbDontShowAgain = CheckBox(activity).apply {
            text = "Більше не показувати"
            textSize = 14f
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
            buttonTintList =
                ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.dark_green))
            setPadding((3 * density).toInt(), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (12 * density).toInt()
                marginStart = (-6 * density).toInt()
            }
        }
        container.addView(cbDontShowAgain)

        AlertDialog.Builder(activity)
            .setTitle("Оновлення MyFinances")
            .setView(container)
            .setPositiveButton("Оновити") { _, _ ->
                downloadAndInstall(activity, release)
            }
            .setNegativeButton("Пізніше") { _, _ ->
                if (cbDontShowAgain.isChecked) {
                    setAutoCheckEnabled(activity, false)
                }
            }
            .setOnCancelListener {
                if (cbDontShowAgain.isChecked) {
                    setAutoCheckEnabled(activity, false)
                }
            }
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

    private fun createRoundedBoxBackground(context: Context): android.graphics.drawable.GradientDrawable {
        val density = context.resources.displayMetrics.density
        val baseColor = ContextCompat.getColor(context, R.color.text_muted)
        val strokeColor = androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, 75)
        val fillColor = androidx.core.graphics.ColorUtils.setAlphaComponent(baseColor, 18)

        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 12f * density
            setColor(fillColor)
            setStroke((1 * density).toInt().coerceAtLeast(1), strokeColor)
        }
    }
}