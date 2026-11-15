package com.project.course.myfinance

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.max

class StatisticsActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val kyivTz = TimeZone.getTimeZone("Europe/Kyiv")

    private var allTransactions: List<Transaction> = emptyList()
    private var selectedPeriod = "30d"
    private var selectedYear = Calendar.getInstance(kyivTz).get(Calendar.YEAR)

    private lateinit var btnPeriod30Days: Button
    private lateinit var btnPeriodMonth: Button
    private lateinit var btnPeriodYear: Button
    private lateinit var btnPeriodAll: Button

    private lateinit var tvReportPeriodTitle: TextView
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    private lateinit var tvReportAvgDaily: TextView

    private lateinit var cardTrend: CardView
    private lateinit var ivTrendArrow: ImageView
    private lateinit var tvTrendText: TextView

    private lateinit var cardIncomePie: CardView
    private lateinit var cardExpensePie: CardView
    private lateinit var incomePieChart: PieChart
    private lateinit var expensePieChart: PieChart
    private lateinit var layoutIncomeBreakdown: LinearLayout
    private lateinit var layoutExpenseBreakdown: LinearLayout

    private lateinit var barChart: BarChart
    private lateinit var tvSelectedDayInfo: TextView

    private lateinit var yearlyBarChart: BarChart
    private lateinit var tvSelectedYear: TextView
    private lateinit var tvSelectedMonthInfo: TextView
    private lateinit var progressBar: ProgressBar

    private val expensePalette = listOf(
        Color.parseColor("#B71C1C"),
        Color.parseColor("#D84315"),
        Color.parseColor("#EF6C00"),
        Color.parseColor("#F9A825"),
        Color.parseColor("#6A1B9A"),
        Color.parseColor("#283593"),
        Color.parseColor("#00838F"),
        Color.parseColor("#4E342E"),
        Color.parseColor("#37474F"),
        Color.parseColor("#AD1457")
    )

    private val incomePalette = listOf(
        Color.parseColor("#1B5E20"),
        Color.parseColor("#2E7D32"),
        Color.parseColor("#00695C"),
        Color.parseColor("#0277BD"),
        Color.parseColor("#558B2F"),
        Color.parseColor("#1565C0"),
        Color.parseColor("#00838F"),
        Color.parseColor("#4527A0")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        btnPeriod30Days = findViewById(R.id.btnPeriod30Days)
        btnPeriodMonth = findViewById(R.id.btnPeriodMonth)
        btnPeriodYear = findViewById(R.id.btnPeriodYear)
        btnPeriodAll = findViewById(R.id.btnPeriodAll)

        tvReportPeriodTitle = findViewById(R.id.tvReportPeriodTitle)
        tvReportIncome = findViewById(R.id.tvReportIncome)
        tvReportExpense = findViewById(R.id.tvReportExpense)
        tvReportNet = findViewById(R.id.tvReportNet)
        tvReportAvgDaily = findViewById(R.id.tvReportAvgDaily)

        cardTrend = findViewById(R.id.cardTrend)
        ivTrendArrow = findViewById(R.id.ivTrendArrow)
        tvTrendText = findViewById(R.id.tvTrendText)

        cardIncomePie = findViewById(R.id.cardIncomePie)
        cardExpensePie = findViewById(R.id.cardExpensePie)
        incomePieChart = findViewById(R.id.incomePieChart)
        expensePieChart = findViewById(R.id.expensePieChart)
        layoutIncomeBreakdown = findViewById(R.id.layoutIncomeBreakdown)
        layoutExpenseBreakdown = findViewById(R.id.layoutExpenseBreakdown)

        barChart = findViewById(R.id.barChart)
        tvSelectedDayInfo = findViewById(R.id.tvSelectedDayInfo)

        yearlyBarChart = findViewById(R.id.yearlyBarChart)
        tvSelectedYear = findViewById(R.id.tvSelectedYear)
        tvSelectedMonthInfo = findViewById(R.id.tvSelectedMonthInfo)
        progressBar = findViewById(R.id.progressBar)

        tvSelectedYear.text = selectedYear.toString()

        setupPeriodButtons()
        setupYearSwitcher()
        setupCharts()
        loadData()
    }

    private fun setupPeriodButtons() {
        val periodMap = mapOf(
            "30d" to btnPeriod30Days,
            "month" to btnPeriodMonth,
            "year" to btnPeriodYear,
            "all" to btnPeriodAll
        )

        periodMap.forEach { (periodKey, button) ->
            button.setOnClickListener {
                if (selectedPeriod != periodKey) {
                    selectedPeriod = periodKey
                    updatePeriodButtonStyles(periodMap)
                    refreshPeriodAnalytics()
                }
            }
        }
        updatePeriodButtonStyles(periodMap)
    }

    private fun updatePeriodButtonStyles(periodMap: Map<String, Button>) {
        periodMap.forEach { (key, button) ->
            if (key == selectedPeriod) {
                button.setBackgroundResource(R.drawable.bg_glossy_green)
                button.setTextColor(ContextCompat.getColor(this, R.color.white_soft))
            } else {
                button.setBackgroundResource(R.drawable.bg_glossy_outline)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            }
        }
    }

    private fun setupYearSwitcher() {
        findViewById<TextView>(R.id.btnPrevYear).setOnClickListener {
            selectedYear--
            tvSelectedYear.text = selectedYear.toString()
            buildYearlyBarChart(allTransactions, selectedYear)
        }
        findViewById<TextView>(R.id.btnNextYear).setOnClickListener {
            selectedYear++
            tvSelectedYear.text = selectedYear.toString()
            buildYearlyBarChart(allTransactions, selectedYear)
        }
    }

    private fun setupCharts() {
        listOf(incomePieChart, expensePieChart).forEach { chart ->
            chart.description.isEnabled = false
            chart.isDrawHoleEnabled = true
            chart.holeRadius = 56f
            chart.transparentCircleRadius = 61f
            chart.setHoleColor(Color.TRANSPARENT)
            chart.setDrawCenterText(true)
            chart.setCenterTextSize(14f)
            chart.setCenterTextColor(ContextCompat.getColor(this, R.color.text_primary))
            chart.setUsePercentValues(true)
            chart.setDrawEntryLabels(false)
            chart.legend.isEnabled = false
            chart.setExtraOffsets(8f, 8f, 8f, 8f)
        }

        listOf(barChart, yearlyBarChart).forEach { chart ->
            chart.description.isEnabled = false
            chart.setDrawGridBackground(false)
            chart.setDrawBarShadow(false)
            chart.setPinchZoom(false)
            chart.isDoubleTapToZoomEnabled = false
            chart.axisRight.isEnabled = false
            chart.axisLeft.axisMinimum = 0f
            chart.axisLeft.gridColor = Color.parseColor("#E0E4E8")
            chart.axisLeft.textColor = ContextCompat.getColor(this, R.color.text_secondary)
            chart.xAxis.position = XAxis.XAxisPosition.BOTTOM
            chart.xAxis.setDrawGridLines(false)
            chart.xAxis.granularity = 1f
            chart.xAxis.textColor = ContextCompat.getColor(this, R.color.text_secondary)
            chart.legend.textColor = ContextCompat.getColor(this, R.color.text_primary)
        }
    }

    private fun loadData() {
        val user = auth.currentUser ?: return

        db.collection("users").document(user.uid).collection("transactions")
            .orderBy("date", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                allTransactions = snapshot.toObjects(Transaction::class.java)

                if (allTransactions.isEmpty()) {
                    Toast.makeText(this, "Немає даних для статистики", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                refreshPeriodAnalytics()
                buildYearlyBarChart(allTransactions, selectedYear)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT).show()
            }
    }

    private fun getFilteredTransactionsForPeriod(): Pair<List<Transaction>, Int> {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance(kyivTz)

        return when (selectedPeriod) {
            "30d" -> {
                tvReportPeriodTitle.text = "Звіт за останні 30 днів"
                val start = now - 30L * 24 * 60 * 60 * 1000
                allTransactions.filter { it.date >= start } to 30
            }

            "month" -> {
                val monthName = SimpleDateFormat("LLLL yyyy", Locale("uk", "UA")).apply {
                    timeZone = kyivTz
                }.format(Date(now)).replaceFirstChar { it.uppercase() }
                tvReportPeriodTitle.text = "Звіт за $monthName"

                val currentMonth = cal.get(Calendar.MONTH)
                val currentYear = cal.get(Calendar.YEAR)
                val daysPassed = max(1, cal.get(Calendar.DAY_OF_MONTH))

                val filtered = allTransactions.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR) == currentYear && cal.get(Calendar.MONTH) == currentMonth
                }
                filtered to daysPassed
            }

            "year" -> {
                val currentYear = cal.get(Calendar.YEAR)
                tvReportPeriodTitle.text = "Звіт за $currentYear рік"
                val daysPassed = max(1, cal.get(Calendar.DAY_OF_YEAR))

                val filtered = allTransactions.filter {
                    cal.timeInMillis = it.date
                    cal.get(Calendar.YEAR) == currentYear
                }
                filtered to daysPassed
            }

            else -> {
                tvReportPeriodTitle.text = "Звіт за весь період"
                val minDate = allTransactions.minOfOrNull { it.date } ?: now
                val daysSpan = max(1, ((now - minDate) / (24L * 60 * 60 * 1000)).toInt() + 1)
                allTransactions to daysSpan
            }
        }
    }

    private fun refreshPeriodAnalytics() {
        val (filtered, daysCount) = getFilteredTransactionsForPeriod()

        val totalIncome = filtered.filter { it.type == "income" }.sumOf { it.amount }
        val totalExpense = filtered.filter { it.type == "expense" }.sumOf { it.amount }
        val netResult = totalIncome - totalExpense
        val avgDailyExpense = if (daysCount > 0) totalExpense / daysCount else 0.0

        tvReportIncome.text = String.format(Locale.US, "+ %.2f ₴", totalIncome)
        tvReportExpense.text = String.format(Locale.US, "- %.2f ₴", totalExpense)

        if (netResult >= 0) {
            tvReportNet.text = String.format(Locale.US, "+ %.2f ₴", netResult)
            tvReportNet.setTextColor(ContextCompat.getColor(this, R.color.dark_green))
        } else {
            tvReportNet.text = String.format(Locale.US, "- %.2f ₴", abs(netResult))
            tvReportNet.setTextColor(ContextCompat.getColor(this, R.color.dark_red))
        }

        tvReportAvgDaily.text = String.format(Locale.US, "%.2f ₴", avgDailyExpense)

        buildSmartInsight(filtered, totalIncome, totalExpense, netResult)
        buildPieSection(
            transactions = filtered,
            type = "expense",
            chart = expensePieChart,
            cardView = cardExpensePie,
            breakdownContainer = layoutExpenseBreakdown,
            palette = expensePalette,
            centerLabel = "Витрати"
        )
        buildPieSection(
            transactions = filtered,
            type = "income",
            chart = incomePieChart,
            cardView = cardIncomePie,
            breakdownContainer = layoutIncomeBreakdown,
            palette = incomePalette,
            centerLabel = "Доходи"
        )
        buildDailyBarChart(filtered)
    }

    private fun buildSmartInsight(
        transactions: List<Transaction>,
        totalIncome: Double,
        totalExpense: Double,
        netResult: Double
    ) {
        if (transactions.isEmpty()) {
            cardTrend.visibility = View.GONE
            return
        }

        cardTrend.visibility = View.VISIBLE
        val topExpense = transactions.filter { it.type == "expense" }
            .groupBy { it.category }
            .mapValues { it.value.sumOf { t -> t.amount } }
            .maxByOrNull { it.value }

        val topCatSuffix = if (topExpense != null && totalExpense > 0) {
            val share = (topExpense.value / totalExpense) * 100
            String.format(Locale.US, "\nНайбільша витрата: %s (%.0f%%).", topExpense.key, share)
        } else {
            ""
        }

        if (netResult >= 0) {
            val savingsRate =
                if (totalIncome > 0) ((netResult / totalIncome) * 100).toInt() else 100
            ivTrendArrow.setImageResource(R.drawable.ic_arrow_up_flat)
            ivTrendArrow.setColorFilter(ContextCompat.getColor(this, R.color.dark_green))
            tvTrendText.text = String.format(
                Locale.US,
                "Профіцит +%.2f ₴ (збережено %d%% доходу).%s",
                netResult,
                savingsRate,
                topCatSuffix
            )
        } else {
            ivTrendArrow.setImageResource(R.drawable.ic_arrow_down_flat)
            ivTrendArrow.setColorFilter(ContextCompat.getColor(this, R.color.dark_red))
            tvTrendText.text = String.format(
                Locale.US,
                "Витрати перевищують доходи на %.2f ₴.%s",
                abs(netResult),
                topCatSuffix
            )
        }
    }

    private fun buildPieSection(
        transactions: List<Transaction>,
        type: String,
        chart: PieChart,
        cardView: CardView,
        breakdownContainer: LinearLayout,
        palette: List<Int>,
        centerLabel: String
    ) {
        val sortedCategories = transactions.filter { it.type == type }
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .filter { it.value > 0 }
            .toList()
            .sortedByDescending { it.second }

        if (sortedCategories.isEmpty()) {
            cardView.visibility = View.GONE
            return
        }

        cardView.visibility = View.VISIBLE
        val totalSum = sortedCategories.sumOf { it.second }

        val entries = ArrayList<PieEntry>()
        val sliceColors = ArrayList<Int>()

        sortedCategories.forEachIndexed { index, (category, amount) ->
            entries.add(PieEntry(amount.toFloat(), category))
            sliceColors.add(palette[index % palette.size])
        }

        val dataSet = PieDataSet(entries, "").apply {
            colors = sliceColors
            sliceSpace = 2.5f
            selectionShift = 6f
            yValuePosition = PieDataSet.ValuePosition.INSIDE_SLICE
        }

        val data = PieData(dataSet).apply {
            setValueTextSize(11f)
            setValueTextColor(Color.WHITE)
            setValueFormatter(object : ValueFormatter() {
                override fun getPieLabel(value: Float, pieEntry: PieEntry?): String {
                    return if (value >= 6f) String.format(Locale.US, "%.0f%%", value) else ""
                }
            })
        }

        chart.centerText = String.format(Locale.US, "%s\n%.2f ₴", centerLabel, totalSum)
        chart.data = data
        chart.highlightValues(null)
        chart.invalidate()
        chart.animateY(700)

        breakdownContainer.removeAllViews()
        val density = resources.displayMetrics.density

        sortedCategories.forEachIndexed { index, (category, amount) ->
            val color = sliceColors[index]
            val percent = if (totalSum > 0) (amount / totalSum) * 100 else 0.0

            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                setOnClickListener {
                    chart.highlightValue(index.toFloat(), 0, false)
                }
            }

            val topLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val dot = View(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams((12 * density).toInt(), (12 * density).toInt())
                        .apply {
                            marginEnd = (10 * density).toInt()
                        }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            }

            val tvCatName = TextView(this).apply {
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = category
                textSize = 15f
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_primary))
            }

            val tvPercent = TextView(this).apply {
                text = String.format(Locale.US, "%.1f%%", percent)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_muted))
                setPadding(0, 0, (10 * density).toInt(), 0)
            }

            val tvCatAmount = TextView(this).apply {
                text = String.format(Locale.US, "%.2f ₴", amount)
                textSize = 15f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@StatisticsActivity, R.color.text_primary))
            }

            topLine.addView(dot)
            topLine.addView(tvCatName)
            topLine.addView(tvPercent)
            topLine.addView(tvCatAmount)

            val progressBg = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    (5 * density).toInt()
                ).apply {
                    topMargin = (6 * density).toInt()
                }
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(Color.parseColor("#E0E4E8"))
                }
            }

            val progressFill = View(this).apply {
                val fraction = (percent / 100.0).toFloat().coerceIn(0.02f, 1f)
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, fraction)
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(color)
                }
            }

            val progressEmpty = View(this).apply {
                val remainder = (1f - (percent / 100.0).toFloat()).coerceAtLeast(0f)
                layoutParams =
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, remainder)
            }

            progressBg.addView(progressFill)
            progressBg.addView(progressEmpty)

            rowLayout.addView(topLine)
            rowLayout.addView(progressBg)
            breakdownContainer.addView(rowLayout)
        }
    }

    private fun buildDailyBarChart(transactions: List<Transaction>) {
        if (transactions.isEmpty()) {
            barChart.clear()
            tvSelectedDayInfo.text = "Немає операцій за обраний період"
            return
        }

        val dayKeyFormat =
            SimpleDateFormat("yyyyMMdd", Locale.getDefault()).apply { timeZone = kyivTz }
        val dayLabelFormat =
            SimpleDateFormat("dd.MM", Locale.getDefault()).apply { timeZone = kyivTz }

        val groupedByDay = transactions.groupBy { dayKeyFormat.format(Date(it.date)) }.toSortedMap()

        val incomeEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()
        val xAxisLabels = ArrayList<String>()
        val dayStatsList = ArrayList<Triple<String, Float, Float>>()

        var index = 0f
        for ((_, dayTransactions) in groupedByDay) {
            val label = dayLabelFormat.format(Date(dayTransactions.first().date))
            val dIncome =
                dayTransactions.filter { it.type == "income" }.sumOf { it.amount }.toFloat()
            val dExpense =
                dayTransactions.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()

            xAxisLabels.add(label)
            dayStatsList.add(Triple(label, dIncome, dExpense))
            incomeEntries.add(BarEntry(index, dIncome))
            expenseEntries.add(BarEntry(index, dExpense))
            index++
        }

        val incomeDataSet = BarDataSet(incomeEntries, "Дохід").apply {
            color = ContextCompat.getColor(this@StatisticsActivity, R.color.dark_green)
            setDrawValues(false)
        }
        val expenseDataSet = BarDataSet(expenseEntries, "Витрата").apply {
            color = ContextCompat.getColor(this@StatisticsActivity, R.color.dark_red)
            setDrawValues(false)
        }

        val barWidth = 0.32f
        val barSpace = 0.04f
        val groupSpace = 0.28f

        val data = BarData(incomeDataSet, expenseDataSet).apply {
            this.barWidth = barWidth
        }

        barChart.data = data
        barChart.xAxis.valueFormatter = IndexAxisValueFormatter(xAxisLabels)
        barChart.xAxis.setCenterAxisLabels(true)
        barChart.xAxis.axisMinimum = 0f
        barChart.xAxis.axisMaximum = xAxisLabels.size.toFloat()

        barChart.groupBars(0f, groupSpace, barSpace)
        barChart.setVisibleXRangeMaximum(7f)
        barChart.moveViewToX(max(0f, xAxisLabels.size - 7f))

        tvSelectedDayInfo.text = "Натисніть на стовпчик для деталей"
        barChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val idx = e?.x?.toInt() ?: return
                if (idx in dayStatsList.indices) {
                    val (dateLbl, inc, exp) = dayStatsList[idx]
                    tvSelectedDayInfo.text = String.format(
                        Locale.US,
                        "%s:  Дохід +%.2f ₴  |  Витрата -%.2f ₴",
                        dateLbl,
                        inc,
                        exp
                    )
                }
            }

            override fun onNothingSelected() {
                tvSelectedDayInfo.text = "Натисніть на стовпчик для деталей"
            }
        })

        barChart.invalidate()
        barChart.animateY(600)
    }

    private fun buildYearlyBarChart(transactions: List<Transaction>, year: Int) {
        val cal = Calendar.getInstance(kyivTz)
        val yearlyTransactions = transactions.filter {
            cal.timeInMillis = it.date
            cal.get(Calendar.YEAR) == year
        }

        val monthNames = arrayOf(
            "Січ", "Лют", "Бер", "Кві", "Тра", "Чер",
            "Лип", "Сер", "Вер", "Жов", "Лис", "Гру"
        )
        val incomeEntries = ArrayList<BarEntry>()
        val expenseEntries = ArrayList<BarEntry>()
        val monthlyTotals = ArrayList<Pair<Float, Float>>()

        for (month in 0..11) {
            val monthTransactions = yearlyTransactions.filter {
                cal.timeInMillis = it.date
                cal.get(Calendar.MONTH) == month
            }
            val mIncome =
                monthTransactions.filter { it.type == "income" }.sumOf { it.amount }.toFloat()
            val mExpense =
                monthTransactions.filter { it.type == "expense" }.sumOf { it.amount }.toFloat()

            monthlyTotals.add(mIncome to mExpense)
            incomeEntries.add(BarEntry(month.toFloat(), mIncome))
            expenseEntries.add(BarEntry(month.toFloat(), mExpense))
        }

        val incomeDataSet = BarDataSet(incomeEntries, "Дохід").apply {
            color = ContextCompat.getColor(this@StatisticsActivity, R.color.dark_green)
            setDrawValues(false)
        }
        val expenseDataSet = BarDataSet(expenseEntries, "Витрата").apply {
            color = ContextCompat.getColor(this@StatisticsActivity, R.color.dark_red)
            setDrawValues(false)
        }

        val barWidth = 0.32f
        val barSpace = 0.04f
        val groupSpace = 0.28f

        val data = BarData(incomeDataSet, expenseDataSet).apply {
            this.barWidth = barWidth
        }

        yearlyBarChart.data = data
        yearlyBarChart.xAxis.valueFormatter = IndexAxisValueFormatter(monthNames)
        yearlyBarChart.xAxis.setCenterAxisLabels(true)
        yearlyBarChart.xAxis.labelCount = 12
        yearlyBarChart.xAxis.axisMinimum = 0f
        yearlyBarChart.xAxis.axisMaximum = 12f

        yearlyBarChart.groupBars(0f, groupSpace, barSpace)
        tvSelectedMonthInfo.text = "Натисніть на місяць для перегляду сум"

        yearlyBarChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val monthIdx = e?.x?.toInt() ?: return
                if (monthIdx in 0..11) {
                    val (mInc, mExp) = monthlyTotals[monthIdx]
                    tvSelectedMonthInfo.text = String.format(
                        Locale.US,
                        "%s %d:  Дохід +%.2f ₴  |  Витрата -%.2f ₴",
                        monthNames[monthIdx],
                        year,
                        mInc,
                        mExp
                    )
                }
            }

            override fun onNothingSelected() {
                tvSelectedMonthInfo.text = "Натисніть на місяць для перегляду сум"
            }
        })

        yearlyBarChart.invalidate()
        yearlyBarChart.animateY(700)
    }
}