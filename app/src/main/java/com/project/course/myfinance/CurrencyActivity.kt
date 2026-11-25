package com.project.course.myfinance

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.textfield.TextInputEditText
import com.project.course.myfinance.currency.CurrencyRepository
import com.project.course.myfinance.currency.HistoricalRatePoint
import kotlinx.coroutines.launch
import java.util.Locale

class CurrencyActivity : BaseNetworkActivity() {
    private lateinit var tvCardUsd: TextView
    private lateinit var tvCardEur: TextView
    private lateinit var tvRateDate: TextView
    private lateinit var etConvertAmount: TextInputEditText
    private lateinit var actvFromCurrency: AutoCompleteTextView
    private lateinit var actvToCurrency: AutoCompleteTextView
    private lateinit var tvConvertedResult: TextView
    private lateinit var tvCrossRateHint: TextView
    private lateinit var btnHistoryUsd: Button
    private lateinit var btnHistoryEur: Button
    private lateinit var currencyLineChart: LineChart
    private lateinit var layoutRateHistoryList: LinearLayout
    private lateinit var progressBar: ProgressBar
    private val ratesToUah = mutableMapOf("UAH" to 1.0)
    private var selectedHistoryCurrency = "USD"

    override fun onNetworkRestored() {
        loadRatesAndHistory()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_currency)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        tvCardUsd = findViewById(R.id.tvCardUsd)
        tvCardEur = findViewById(R.id.tvCardEur)
        tvRateDate = findViewById(R.id.tvRateDate)
        etConvertAmount = findViewById(R.id.etConvertAmount)
        actvFromCurrency = findViewById(R.id.actvFromCurrency)
        actvToCurrency = findViewById(R.id.actvToCurrency)
        tvConvertedResult = findViewById(R.id.tvConvertedResult)
        tvCrossRateHint = findViewById(R.id.tvCrossRateHint)
        btnHistoryUsd = findViewById(R.id.btnHistoryUsd)
        btnHistoryEur = findViewById(R.id.btnHistoryEur)
        currencyLineChart = findViewById(R.id.currencyLineChart)
        layoutRateHistoryList = findViewById(R.id.layoutRateHistoryList)
        progressBar = findViewById(R.id.progressBar)

        val passedUsd = intent.getDoubleExtra("EXTRA_USD_RATE", 0.0)
        val passedEur = intent.getDoubleExtra("EXTRA_EUR_RATE", 0.0)
        if (passedUsd > 0) {
            ratesToUah["USD"] = passedUsd
            tvCardUsd.text = String.format(Locale.US, "%.2f ₴", passedUsd)
        }
        if (passedEur > 0) {
            ratesToUah["EUR"] = passedEur
            tvCardEur.text = String.format(Locale.US, "%.2f ₴", passedEur)
        }

        setupConverter()
        setupLineChart()
        setupHistoryToggle()
        loadRatesAndHistory()
    }

    private fun setupConverter() {
        val currencies = listOf("UAH", "USD", "EUR", "PLN", "GBP", "CHF")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        actvFromCurrency.setAdapter(adapter)
        actvToCurrency.setAdapter(adapter)

        actvFromCurrency.setOnItemClickListener { _, _, _, _ -> recalculate() }
        actvToCurrency.setOnItemClickListener { _, _, _, _ -> recalculate() }

        findViewById<Button>(R.id.btnSwapCurrencies).setOnClickListener {
            val from = actvFromCurrency.text.toString()
            val to = actvToCurrency.text.toString()
            actvFromCurrency.setText(to, false)
            actvToCurrency.setText(from, false)
            recalculate()
        }

        etConvertAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                recalculate()
            }
        })
    }

    private fun recalculate() {
        val amount = etConvertAmount.text.toString().replace(",", ".").toDoubleOrNull() ?: 0.0
        val fromCode = actvFromCurrency.text.toString().ifBlank { "USD" }
        val toCode = actvToCurrency.text.toString().ifBlank { "UAH" }

        val fromRate = ratesToUah[fromCode] ?: return
        val toRate = ratesToUah[toCode] ?: return

        val crossRate = fromRate / toRate
        val converted = amount * crossRate

        tvConvertedResult.text = String.format(Locale.US, "%.2f %s", converted, toCode)
        tvCrossRateHint.text =
            String.format(Locale.US, "1 %s = %.4f %s", fromCode, crossRate, toCode)
    }

    private fun setupLineChart() {
        currencyLineChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setPinchZoom(false)
            isDoubleTapToZoomEnabled = false
            legend.isEnabled = false
            axisRight.isEnabled = false
            axisLeft.gridColor = Color.parseColor("#E0E4E8")
            axisLeft.textColor =
                ContextCompat.getColor(this@CurrencyActivity, R.color.text_secondary)
            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f
            xAxis.textColor = ContextCompat.getColor(this@CurrencyActivity, R.color.text_secondary)
        }
    }

    private fun setupHistoryToggle() {
        btnHistoryUsd.setOnClickListener {
            if (selectedHistoryCurrency != "USD") {
                selectedHistoryCurrency = "USD"
                updateHistoryButtons()
                loadHistoryForCurrency("USD")
            }
        }
        btnHistoryEur.setOnClickListener {
            if (selectedHistoryCurrency != "EUR") {
                selectedHistoryCurrency = "EUR"
                updateHistoryButtons()
                loadHistoryForCurrency("EUR")
            }
        }
        updateHistoryButtons()
    }

    private fun updateHistoryButtons() {
        val activeColor = ContextCompat.getColor(this, R.color.white_soft)
        val inactiveColor = ContextCompat.getColor(this, R.color.text_secondary)

        if (selectedHistoryCurrency == "USD") {
            btnHistoryUsd.setBackgroundResource(R.drawable.bg_glossy_green)
            btnHistoryUsd.setTextColor(activeColor)
            btnHistoryEur.setBackgroundResource(R.drawable.bg_glossy_outline)
            btnHistoryEur.setTextColor(inactiveColor)
        } else {
            btnHistoryEur.setBackgroundResource(R.drawable.bg_glossy_green)
            btnHistoryEur.setTextColor(activeColor)
            btnHistoryUsd.setBackgroundResource(R.drawable.bg_glossy_outline)
            btnHistoryUsd.setTextColor(inactiveColor)
        }
    }

    private fun loadRatesAndHistory() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val currentRates = CurrencyRepository.fetchCurrentRates()
            if (currentRates.isNotEmpty()) {
                currentRates.forEach { ratesToUah[it.code] = it.rate }

                val usd = currentRates.find { it.code == "USD" }
                val eur = currentRates.find { it.code == "EUR" }

                usd?.let {
                    tvCardUsd.text = String.format(Locale.US, "%.2f ₴", it.rate)
                    tvRateDate.text = "на ${it.date}"
                }
                eur?.let {
                    tvCardEur.text = String.format(Locale.US, "%.2f ₴", it.rate)
                }
                recalculate()
            } else {
                Toast.makeText(
                    this@CurrencyActivity,
                    "Не вдалося оновити курси валют",
                    Toast.LENGTH_SHORT
                ).show()
            }

            loadHistoryForCurrency(selectedHistoryCurrency)
            progressBar.visibility = View.GONE
        }
    }

    private fun loadHistoryForCurrency(currencyCode: String) {
        lifecycleScope.launch {
            val points = CurrencyRepository.fetchLast7DaysHistory(currencyCode)
            if (points.isNotEmpty()) {
                renderChart(points, currencyCode)
                renderHistoryList(points)
            }
        }
    }

    private fun renderChart(points: List<HistoricalRatePoint>, currencyCode: String) {
        val entries = points.mapIndexed { index, point ->
            Entry(index.toFloat(), point.rate.toFloat())
        }
        val labels = points.map { it.dateLabel }

        val darkGreen = ContextCompat.getColor(this, R.color.dark_green)
        val dataSet = LineDataSet(entries, currencyCode).apply {
            color = darkGreen
            setCircleColor(darkGreen)
            lineWidth = 2.5f
            circleRadius = 4f
            setDrawValues(false)
            mode = LineDataSet.Mode.CUBIC_BEZIER
            setDrawFilled(true)
            fillColor = darkGreen
            fillAlpha = 35
        }

        currencyLineChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        currencyLineChart.xAxis.labelCount = labels.size
        currencyLineChart.data = LineData(dataSet)
        currencyLineChart.invalidate()
        currencyLineChart.animateX(500)
    }

    private fun renderHistoryList(points: List<HistoricalRatePoint>) {
        layoutRateHistoryList.removeAllViews()
        val density = resources.displayMetrics.density

        for (i in points.indices.reversed()) {
            val current = points[i]
            val prev = if (i > 0) points[i - 1] else null
            val diff = if (prev != null) current.rate - prev.rate else 0.0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            }

            val tvDate = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = current.fullDate
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@CurrencyActivity, R.color.text_secondary))
            }

            val tvDelta = TextView(this).apply {
                textSize = 13f
                setPadding(0, 0, (12 * density).toInt(), 0)
                when {
                    diff > 0.0001 -> {
                        text = String.format(Locale.US, "+%.2f ₴", diff)
                        setTextColor(
                            ContextCompat.getColor(
                                this@CurrencyActivity,
                                R.color.dark_red
                            )
                        )
                    }

                    diff < -0.0001 -> {
                        text = String.format(Locale.US, "%.2f ₴", diff)
                        setTextColor(
                            ContextCompat.getColor(
                                this@CurrencyActivity,
                                R.color.dark_green
                            )
                        )
                    }

                    else -> {
                        text = "0.00 ₴"
                        setTextColor(
                            ContextCompat.getColor(
                                this@CurrencyActivity,
                                R.color.text_muted
                            )
                        )
                    }
                }
            }

            val tvRate = TextView(this).apply {
                text = String.format(Locale.US, "%.2f ₴", current.rate)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@CurrencyActivity, R.color.text_primary))
            }

            row.addView(tvDate)
            row.addView(tvDelta)
            row.addView(tvRate)
            layoutRateHistoryList.addView(row)
        }
    }
}