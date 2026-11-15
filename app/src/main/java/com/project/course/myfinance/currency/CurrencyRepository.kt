package com.project.course.myfinance.currency

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class NbuExchangeDto(
    @SerializedName("r030") val r030: Int,
    @SerializedName("txt") val name: String,
    @SerializedName("rate") val rate: Double,
    @SerializedName("cc") val code: String,
    @SerializedName("exchangedate") val exchangeDate: String
)

data class CurrencyRate(
    val code: String, val name: String, val rate: Double, val date: String
)

data class HistoricalRatePoint(
    val dateLabel: String, val fullDate: String, val rate: Double
)

object CurrencyRepository {
    private val gson = Gson()
    private val kyivTz = TimeZone.getTimeZone("Europe/Kyiv")
    private val supportedCodes = listOf("USD", "EUR", "PLN", "GBP", "CHF")

    suspend fun fetchCurrentRates(): List<CurrencyRate> = withContext(Dispatchers.IO) {
        try {
            val json =
                getRequest("https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?json")
            val type = object : TypeToken<List<NbuExchangeDto>>() {}.type
            val items: List<NbuExchangeDto> = gson.fromJson(json, type) ?: emptyList()

            items.filter { it.code in supportedCodes }.sortedBy { supportedCodes.indexOf(it.code) }
                .map {
                    CurrencyRate(
                        code = it.code, name = it.name, rate = it.rate, date = it.exchangeDate
                    )
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchLast7DaysHistory(currencyCode: String): List<HistoricalRatePoint> =
        coroutineScope {
            val apiDateFormat = SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = kyivTz }
            val shortLabelFormat = SimpleDateFormat("dd.MM", Locale.US).apply { timeZone = kyivTz }
            val fullDateFormat =
                SimpleDateFormat("dd.MM.yyyy", Locale.US).apply { timeZone = kyivTz }

            val deferredPoints = (6 downTo 0).map { daysAgo ->
                async(Dispatchers.IO) {
                    try {
                        val cal = Calendar.getInstance(kyivTz).apply {
                            add(Calendar.DAY_OF_YEAR, -daysAgo)
                        }
                        val queryDate = apiDateFormat.format(cal.time)
                        val url =
                            "https://bank.gov.ua/NBUStatService/v1/statdirectory/exchange?valcode=$currencyCode&date=$queryDate&json"
                        val json = getRequest(url)
                        val type = object : TypeToken<List<NbuExchangeDto>>() {}.type
                        val list: List<NbuExchangeDto> = gson.fromJson(json, type) ?: emptyList()

                        list.firstOrNull()?.let { dto ->
                            HistoricalRatePoint(
                                dateLabel = shortLabelFormat.format(cal.time),
                                fullDate = fullDateFormat.format(cal.time),
                                rate = dto.rate
                            )
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            deferredPoints.awaitAll().filterNotNull()
        }

    private fun getRequest(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}