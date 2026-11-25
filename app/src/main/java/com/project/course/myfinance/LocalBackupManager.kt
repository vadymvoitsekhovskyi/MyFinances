package com.project.course.myfinance

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import com.project.course.myfinance.models.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LocalBackupManager {
    private const val PREFS_NAME = "backup_files_prefs"
    private const val DEFAULT_NAME = "myfinance_transactions"
    private val gson = Gson()

    fun getBaseFileName(context: Context, format: String): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("name_$format", DEFAULT_NAME) ?: DEFAULT_NAME
    }

    fun getFullFileName(context: Context, format: String): String {
        return "${getBaseFileName(context, format)}.$format"
    }

    fun getBackupFile(context: Context, format: String): File {
        val dir = File(context.cacheDir, "shared_backups").apply {
            if (!exists()) mkdirs()
        }
        return File(dir, getFullFileName(context, format))
    }

    fun renameFile(context: Context, format: String, rawNewName: String): Boolean {
        val cleanName = rawNewName.trim()
            .removeSuffix(".$format")
            .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        if (cleanName.isBlank()) return false

        val oldFile = getBackupFile(context, format)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString("name_$format", cleanName)
            .apply()

        val newFile = getBackupFile(context, format)
        if (oldFile.exists() && oldFile.absolutePath != newFile.absolutePath) {
            oldFile.renameTo(newFile)
        }
        return true
    }

    fun syncFilesAsync(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            syncFilesSuspend(appContext)
        }
    }

    suspend fun syncFilesSuspend(context: Context) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users").document(user.uid)
                .collection("transactions")
                .orderBy("date", Query.Direction.DESCENDING)
                .get()
                .await()

            val transactions = snapshot.toObjects(Transaction::class.java)

            val csvFile = getBackupFile(context, "csv")
            csvFile.outputStream().use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(buildCsvString(transactions).toByteArray(Charsets.UTF_8))
            }

            val jsonFile = getBackupFile(context, "json")
            jsonFile.outputStream().use { out ->
                out.write(buildJsonString(transactions).toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildJsonString(transactions: List<Transaction>): String {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        }
        val jsonList = transactions.map { t ->
            mapOf(
                "id" to t.id,
                "type" to t.type,
                "category" to t.category,
                "amount" to t.amount,
                "date" to sdf.format(Date(t.date)),
                "comment" to t.comment
            )
        }
        return gson.toJson(jsonList)
    }

    private fun buildCsvString(transactions: List<Transaction>): String {
        val builder = StringBuilder("id,type,category,amount,date,comment\n")
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        }
        transactions.forEach { t ->
            val safeCategory = t.category.replace("\"", "\"\"").let { "\"$it\"" }
            val safeComment = t.comment.replace("\"", "\"\"").let { "\"$it\"" }
            builder.append("${t.id},${t.type},$safeCategory,${t.amount},${sdf.format(Date(t.date))},$safeComment\n")
        }
        return builder.toString()
    }
}