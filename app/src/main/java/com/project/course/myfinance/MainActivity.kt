package com.project.course.myfinance

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.graphics.drawable.Drawable
import android.widget.ImageButton
import androidx.vectordrawable.graphics.drawable.Animatable2Compat
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.lifecycleScope
import com.project.course.myfinance.currency.CurrencyRepository
import kotlinx.coroutines.launch
import android.os.CountDownTimer
import android.widget.ProgressBar

class MainActivity : BaseNetworkActivity() {
    private lateinit var tvBalanceVideo: android.view.TextureView
    private var balanceMediaPlayer: android.media.MediaPlayer? = null
    private lateinit var tvUsdRate: TextView
    private lateinit var tvEurRate: TextView
    private var currentUsdRate: Double = 0.0
    private var currentEurRate: Double = 0.0
    private lateinit var mainContent: androidx.constraintlayout.widget.ConstraintLayout
    private var allAvailableCategories = listOf<String>()
    private lateinit var btnResetFilters: TextView
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private lateinit var transactionAdapter: TransactionAdapter
    private lateinit var tvTotalBalance: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var tvEmptyState: TextView
    private lateinit var topPanel: LinearLayout
    private lateinit var selectionPanel: LinearLayout
    private lateinit var tvSelectedCount: TextView
    private lateinit var tvProfileLetter: TextView
    private lateinit var ivProfile: ImageView
    private var currentTransactions: List<Transaction> = emptyList()
    private var currentLimit: Long = 30L
    private var isAllLoaded = false
    private var isScrollDownRequested = false
    private var isSelectAllPending = false
    private var snapshotListener: ListenerRegistration? = null
    private var balanceListener: ListenerRegistration? = null
    private var currentTypeFilter = "all"
    private var currentCategoryFilter = "all"
    private var currentSortOrder = "desc"
    private var currentSpecificDateFilter: Long? = null
    private lateinit var btnFilterType: Button
    private lateinit var btnFilterCategory: Button
    private lateinit var btnFilterSpecificDate: Button
    private lateinit var btnSortDate: Button
    private var isBalanceVisible = false
    private var currentTotalBalance = 0.0
    private var dailyBalancesMap = mapOf<String, Double>()
    private var dailyExpensesMap = mapOf<String, Double>()
    private var dailyIncomesMap = mapOf<String, Double>()
    private var currentSearchQuery: String = ""
    private lateinit var layoutUndoDelete: LinearLayout
    private lateinit var pbUndoTimer: ProgressBar
    private lateinit var tvUndoSeconds: TextView
    private lateinit var tvUndoMessage: TextView
    private lateinit var btnUndoDelete: Button
    private var deleteCountDownTimer: CountDownTimer? = null
    private var pendingDeleteIds: Set<String> = emptySet()
    private var rawAllTransactions: List<Transaction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val tvAppTitle = findViewById<TextView>(R.id.tvAppTitle)
        tvAppTitle.typeface =
            android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
        tvAppTitle.paint.isFakeBoldText = true

        mainContent = findViewById(R.id.mainContent)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnAddTransaction = findViewById<ImageButton>(R.id.btnAddTransaction)
        val btnHome = findViewById<ImageButton>(R.id.btnHome)
        val btnFilterScroll = findViewById<Button>(R.id.btnFilterScroll)

        tvTotalBalance = findViewById(R.id.tvTotalBalance)
        rvTransactions = findViewById(R.id.rvTransactions)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        topPanel = findViewById(R.id.topPanel)
        selectionPanel = findViewById(R.id.selectionPanel)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        tvProfileLetter = findViewById(R.id.tvProfileLetter)
        ivProfile = findViewById(R.id.ivProfile)
        tvBalanceVideo = findViewById(R.id.tvBalanceVideo)
        btnResetFilters = findViewById(R.id.btnResetFilters)
        btnResetFilters.setOnClickListener { resetFilters() }

        val cvProfile = findViewById<CardView>(R.id.cvProfile)
        val layoutBalanceText = findViewById<LinearLayout>(R.id.layoutBalanceText)
        val btnCloseSelection = findViewById<ImageView>(R.id.btnCloseSelection)
        val btnSelectAll = findViewById<ImageView>(R.id.btnSelectAll)
        val btnDeleteSelected = findViewById<ImageView>(R.id.btnDeleteSelected)

        layoutUndoDelete = findViewById(R.id.layoutUndoDelete)
        pbUndoTimer = findViewById(R.id.pbUndoTimer)
        tvUndoSeconds = findViewById(R.id.tvUndoSeconds)
        tvUndoMessage = findViewById(R.id.tvUndoMessage)
        btnUndoDelete = findViewById(R.id.btnUndoDelete)

        btnUndoDelete.setOnClickListener {
            cancelScheduledDeletion()
        }

        btnFilterType = findViewById(R.id.btnFilterType)
        btnFilterCategory = findViewById(R.id.btnFilterCategory)
        btnFilterSpecificDate = findViewById(R.id.btnFilterSpecificDate)
        btnSortDate = findViewById(R.id.btnSortDate)

        val etSearch =
            findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearch)

        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                if (currentSearchQuery.isNotBlank()) {
                    isAllLoaded = true
                    loadTransactions()
                } else {
                    applyFiltersAndSort()
                }
            }
        })

        tvTotalBalance.text = "••••• ₴"

        tvTotalBalance.setOnClickListener {
            toggleBalanceVisibility()
        }

        tvUsdRate = findViewById(R.id.tvUsdRate)
        tvEurRate = findViewById(R.id.tvEurRate)

        btnFilterType.setOnClickListener { showTypeFilterDialog() }
        btnFilterCategory.setOnClickListener { showCategoryFilterDialog() }
        btnFilterSpecificDate.setOnClickListener { showSpecificDatePicker() }
        btnSortDate.setOnClickListener { showSortDialog() }

        cvProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        val btnStatistics = findViewById<ImageView>(R.id.btnStatistics)
        btnStatistics.setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java).apply {
                putExtra("EXTRA_USD_RATE", currentUsdRate)
                putExtra("EXTRA_EUR_RATE", currentEurRate)
            }
            startActivity(intent)
        }

        btnHome.setOnClickListener {
            if (transactionAdapter.itemCount > 0) {
                rvTransactions.smoothScrollToPosition(0)
            }
        }

        btnFilterScroll.setOnClickListener {
            showScrollNavigationDialog()
        }

        btnAddTransaction.setOnClickListener {
            startActivity(Intent(this, AddTransactionActivity::class.java))
        }

        transactionAdapter = TransactionAdapter(emptyList(), onItemClick = { transaction ->
            if (transactionAdapter.selectedIds.isNotEmpty()) {
                toggleSelection(transaction.id)
            } else {
                showTransactionDetailsBottomSheet(transaction)
            }
        }, onItemLongClick = { transaction ->
            toggleSelection(transaction.id)
        })

        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionAdapter
        setupSwipeActions()

        rvTransactions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (!recyclerView.canScrollVertically(1) && newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (!isAllLoaded) {
                        currentLimit += 10L
                        loadTransactions()
                    }
                }
            }
        })

        btnCloseSelection.setOnClickListener { clearSelection() }

        btnSelectAll.setOnClickListener {
            if (!isAllLoaded) {
                isSelectAllPending = true
                isAllLoaded = true
                loadTransactions()
            } else {
                transactionAdapter.selectAll()
                updateSelectionUI()
            }
        }

        btnDeleteSelected.setOnClickListener {
            showDeleteConfirmationDialog(transactionAdapter.selectedIds.toList())
        }

        loadTotalBalance()
        loadCurrencyRates()
        loadTransactions()
        updateProfileIcon()
        setupBalanceVideo()
    }

    private fun showScrollNavigationDialog() {
        val options = arrayOf("На початок списку (↑)", "В самий низ списку (↓)")

        AlertDialog.Builder(this)
            .setTitle("Швидкий перехід")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> scrollToTop()
                    1 -> scrollToBottom()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun scrollToTop() {
        rvTransactions.scrollToPosition(0)

        val hasFilters = currentTypeFilter != "all" ||
                currentCategoryFilter != "all" ||
                currentSpecificDateFilter != null ||
                currentSearchQuery.isNotBlank() ||
                currentSortOrder != "desc"

        if (isAllLoaded && !hasFilters && transactionAdapter.selectedIds.isEmpty()) {
            isAllLoaded = false
            currentLimit = 30L
            loadTransactions()
        }
    }

    private fun scrollToBottom() {
        isScrollDownRequested = true
        isAllLoaded = true
        loadTransactions()
    }

    private fun setupBalanceVideo() {
        tvBalanceVideo.surfaceTextureListener =
            object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surfaceTexture: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                    val surface = android.view.Surface(surfaceTexture)
                    try {
                        val uri =
                            android.net.Uri.parse("android.resource://$packageName/${R.raw.mycard}")
                        balanceMediaPlayer = android.media.MediaPlayer().apply {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                setAudioAttributes(
                                    android.media.AudioAttributes.Builder()
                                        .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MOVIE)
                                        .build()
                                )
                            }
                            setDataSource(this@MainActivity, uri)
                            setSurface(surface)
                            isLooping = true
                            setVolume(0f, 0f)
                            setOnVideoSizeChangedListener { _, w, h ->
                                if (w > 0 && h > 0) {
                                    val params =
                                        tvBalanceVideo.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                                    params.dimensionRatio = "$w:$h"
                                    tvBalanceVideo.layoutParams = params
                                }
                            }
                            setOnPreparedListener { mp ->
                                mp.start()
                            }
                            prepareAsync()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: android.graphics.SurfaceTexture,
                    width: Int,
                    height: Int
                ) {
                }

                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                    balanceMediaPlayer?.release()
                    balanceMediaPlayer = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
            }
    }

    private fun loadCurrencyRates() {
        lifecycleScope.launch {
            val rates = CurrencyRepository.fetchCurrentRates()
            val usd = rates.find { it.code == "USD" }?.rate
            val eur = rates.find { it.code == "EUR" }?.rate

            if (usd != null && eur != null) {
                currentUsdRate = usd
                currentEurRate = eur
                tvUsdRate.text = String.format(Locale.US, "\uD83C\uDDFA\uD83C\uDDF8 %.2f ₴", usd)
                tvEurRate.text = String.format(Locale.US, "\uD83C\uDDEA\uD83C\uDDFA %.2f ₴", eur)
            }
        }
    }

    private fun toggleBalanceVisibility() {
        if (isBalanceVisible) {
            isBalanceVisible = false
            tvTotalBalance.text = "••••• ₴"
        } else {
            isBalanceVisible = true
            tvTotalBalance.text = String.format(Locale.US, "%.2f ₴", currentTotalBalance)
        }
    }

    private fun showTypeFilterDialog() {
        val options = arrayOf("Всі", "Витрати", "Доходи")
        val checkedItem = when (currentTypeFilter) {
            "expense" -> 1; "income" -> 2; else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Оберіть тип")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentTypeFilter = when (which) {
                    1 -> "expense"; 2 -> "income"; else -> "all"
                }
                btnFilterType.text = "Тип: ${options[which]}"
                isAllLoaded = true
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun showCategoryFilterDialog() {
        val categories = allAvailableCategories.toMutableList()
        categories.add(0, "Всі")
        val options = categories.toTypedArray()
        val checkedItem =
            if (currentCategoryFilter == "all") 0 else options.indexOf(currentCategoryFilter)
                .takeIf { it != -1 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Оберіть категорію")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentCategoryFilter = if (which == 0) "all" else options[which]

                var shortCat = options[which]
                if (shortCat.length > 10 && which != 0) shortCat = shortCat.substring(0, 10) + "..."

                btnFilterCategory.text = if (which == 0) "Категорія: Всі" else shortCat
                isAllLoaded = true
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun showSpecificDatePicker() {
        val calendar = Calendar.getInstance()

        if (currentSpecificDateFilter != null) {
            calendar.timeInMillis = currentSpecificDateFilter!!
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePickerDialog =
            DatePickerDialog(this, { _, selectedYear, selectedMonth, selectedDay ->
                val selectedCalendar = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                currentSpecificDateFilter = selectedCalendar.timeInMillis

                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                btnFilterSpecificDate.text = "Дата: ${sdf.format(selectedCalendar.time)}"

                isAllLoaded = true
                loadTransactions()
            }, year, month, day)

        datePickerDialog.datePicker.maxDate = System.currentTimeMillis()

        datePickerDialog.setButton(DatePickerDialog.BUTTON_NEUTRAL, "Очистити") { _, _ ->
            currentSpecificDateFilter = null
            btnFilterSpecificDate.text = "Дата: Всі"
            isAllLoaded = true
            loadTransactions()
        }

        datePickerDialog.show()
    }

    private fun showSortDialog() {
        val options = arrayOf("Спочатку нові (↓)", "Спочатку старі (↑)", "Без коментарів")
        val checkedItem = when (currentSortOrder) {
            "desc" -> 0
            "asc" -> 1
            "no_comments" -> 2
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("Сортування за датою")
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                currentSortOrder = when (which) {
                    0 -> "desc"
                    1 -> "asc"
                    2 -> "no_comments"
                    else -> "desc"
                }
                btnSortDate.text = when (which) {
                    0 -> "Сорт: Нові"
                    1 -> "Сорт: Старі"
                    2 -> "Без коментарів"
                    else -> "Сорт: Нові"
                }
                isAllLoaded = true
                loadTransactions()
                dialog.dismiss()
            }.show()
    }

    private fun applyFiltersAndSort() {
        var result = currentTransactions.filter { it.id !in pendingDeleteIds }

        if (currentTypeFilter != "all") {
            result = result.filter { it.type == currentTypeFilter }
        }

        if (currentCategoryFilter != "all") {
            result = result.filter { it.category == currentCategoryFilter }
        }

        if (currentSearchQuery.isNotBlank()) {
            val query = currentSearchQuery.lowercase()
            result = result.filter { transaction ->
                transaction.category.lowercase().contains(query) ||
                        transaction.comment.lowercase().contains(query) ||
                        transaction.amount.toString().contains(query)
            }
        }

        result = when (currentSortOrder) {
            "desc" -> result.sortedByDescending { it.date }
            "asc" -> result.sortedBy { it.date }
            "no_comments" -> result.filter { it.comment.isBlank() }.sortedByDescending { it.date }
            else -> result.sortedByDescending { it.date }
        }

        transactionAdapter.updateData(result)
        updateFilterButtonsStyle()

        if (result.isEmpty()) {
            rvTransactions.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
        } else {
            rvTransactions.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }

        val hasFilters = currentTypeFilter != "all" ||
                currentCategoryFilter != "all" ||
                currentSpecificDateFilter != null ||
                currentSearchQuery.isNotBlank() ||
                currentSortOrder != "desc"

        btnResetFilters.visibility = if (hasFilters) View.VISIBLE else View.GONE
    }

    private fun resetFilters() {
        currentTypeFilter = "all"
        currentCategoryFilter = "all"
        currentSpecificDateFilter = null
        currentSearchQuery = ""
        currentSortOrder = "desc"

        findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etSearch).setText(
            ""
        )
        btnFilterType.text = "Тип: Всі"
        btnFilterCategory.text = "Категорія: Всі"
        btnFilterSpecificDate.text = "Дата: Всі"
        btnSortDate.text = "Сорт: Нові"

        currentLimit = 30L
        isAllLoaded = false
        loadTransactions()
        Toast.makeText(this, "Фільтри скинуто", Toast.LENGTH_SHORT).show()
    }

    private fun updateFilterButtonsStyle() {
        val buttons = listOf(
            btnFilterType to (currentTypeFilter != "all"),
            btnFilterCategory to (currentCategoryFilter != "all"),
            btnFilterSpecificDate to (currentSpecificDateFilter != null),
            btnSortDate to (currentSortOrder != "desc")
        )

        for ((button, isActive) in buttons) {
            if (isActive) {
                button.setBackgroundResource(R.drawable.bg_glossy_green)
                button.setTextColor(Color.parseColor("#F4F6F8"))
            } else {
                button.setBackgroundResource(R.drawable.bg_glossy_outline)
                button.setTextColor(Color.parseColor("#545B64"))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        } else {
            updateProfileIcon()
            if (balanceMediaPlayer?.isPlaying == false) {
                balanceMediaPlayer?.start()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (balanceMediaPlayer?.isPlaying == true) {
            balanceMediaPlayer?.pause()
        }
    }

    override fun onDestroy() {
        balanceMediaPlayer?.release()
        balanceMediaPlayer = null

        super.onDestroy()

        deleteCountDownTimer?.cancel()
        if (pendingDeleteIds.isNotEmpty()) {
            deleteTransactions(pendingDeleteIds.toList())
            pendingDeleteIds = emptySet()
        }
        snapshotListener?.remove()
        balanceListener?.remove()
    }

    override fun onNetworkRestored() {
        loadTotalBalance()
        loadTransactions()
        loadCurrencyRates()
    }

    private fun updateProfileIcon() {
        val user = auth.currentUser ?: return
        val nameToUse = user.displayName.takeIf { !it.isNullOrBlank() } ?: user.email ?: "U"

        if (nameToUse.isNotEmpty()) {
            tvProfileLetter.text = nameToUse.first().uppercase()
        }

        db.collection("users").document(user.uid).get().addOnSuccessListener { document ->
            if (document != null && document.contains("avatarBase64")) {
                val base64String = document.getString("avatarBase64")
                if (!base64String.isNullOrEmpty()) {
                    try {
                        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                        val decodedBitmap =
                            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        ivProfile.setImageBitmap(decodedBitmap)
                        ivProfile.visibility = View.VISIBLE
                        tvProfileLetter.visibility = View.GONE
                    } catch (e: Exception) {
                    }
                } else {
                    ivProfile.visibility = View.GONE
                    tvProfileLetter.visibility = View.VISIBLE
                }
            } else {
                ivProfile.visibility = View.GONE
                tvProfileLetter.visibility = View.VISIBLE
            }
        }
    }

    private fun toggleSelection(id: String) {
        transactionAdapter.toggleSelection(id)
        updateSelectionUI()
    }

    private fun clearSelection() {
        transactionAdapter.clearSelection()
        updateSelectionUI()
    }

    private fun updateSelectionUI() {
        val count = transactionAdapter.selectedIds.size
        if (count > 0) {
            topPanel.visibility = View.GONE
            selectionPanel.visibility = View.VISIBLE
            tvSelectedCount.text = "Вибрано: $count"
        } else {
            topPanel.visibility = View.VISIBLE
            selectionPanel.visibility = View.GONE
        }
    }

    private fun loadTotalBalance() {
        val currentUser = auth.currentUser ?: return

        balanceListener?.remove()
        balanceListener =
            db.collection("users").document(currentUser.uid).collection("transactions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) return@addSnapshotListener

                    rawAllTransactions =
                        snapshot.documents.mapNotNull { it.toObject(Transaction::class.java) }
                    recalculateBalances()
                }
    }

    private fun recalculateBalances() {
        val activeTrans = rawAllTransactions.filter { it.id !in pendingDeleteIds }
        val sortedTrans = activeTrans.sortedBy { it.date }
        allAvailableCategories = activeTrans.map { it.category }.distinct().sorted()

        var totalBalance = 0.0
        val balancesMap = mutableMapOf<String, Double>()
        val expensesMap = mutableMapOf<String, Double>()
        val incomesMap = mutableMapOf<String, Double>()

        val exactDateFormatter =
            SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply {
                this.timeZone = TimeZone.getTimeZone("Europe/Kyiv")
            }

        for (t in sortedTrans) {
            val dateStr = exactDateFormatter.format(Date(t.date))

            if (t.type == "income") {
                totalBalance += t.amount
                incomesMap[dateStr] = (incomesMap[dateStr] ?: 0.0) + t.amount
            } else {
                totalBalance -= t.amount
                expensesMap[dateStr] = (expensesMap[dateStr] ?: 0.0) + t.amount
            }

            balancesMap[dateStr] = totalBalance
        }

        currentTotalBalance = totalBalance
        dailyBalancesMap = balancesMap
        dailyExpensesMap = expensesMap
        dailyIncomesMap = incomesMap

        if (isBalanceVisible) {
            tvTotalBalance.text =
                String.format(Locale.US, "%.2f ₴", currentTotalBalance)
        }

        if (::transactionAdapter.isInitialized) {
            transactionAdapter.updateDailyStats(
                dailyBalancesMap,
                dailyExpensesMap,
                dailyIncomesMap
            )
        }
    }

    private fun loadTransactions() {
        val currentUser = auth.currentUser ?: return

        snapshotListener?.remove()

        var query: Query =
            db.collection("users").document(currentUser.uid).collection("transactions")

        if (currentSpecificDateFilter != null) {
            val startOfDay = currentSpecificDateFilter!!
            val endOfDay = startOfDay + (24L * 60L * 60L * 1000L) - 1L
            query = query.whereGreaterThanOrEqualTo("date", startOfDay)
                .whereLessThanOrEqualTo("date", endOfDay)
        }

        val orderedQuery = query.orderBy("date", Query.Direction.DESCENDING)
        val limitedQuery = if (isAllLoaded) orderedQuery else orderedQuery.limit(currentLimit)

        snapshotListener = limitedQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                isSelectAllPending = false
                Toast.makeText(this, "Помилка завантаження даних", Toast.LENGTH_SHORT)
                    .show()
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val transactionsList = mutableListOf<Transaction>()

                for (document in snapshot.documents) {
                    val transaction = document.toObject(Transaction::class.java)
                    if (transaction != null) {
                        transactionsList.add(transaction)
                    }
                }

                currentTransactions = transactionsList
                applyFiltersAndSort()

                if (isSelectAllPending) {
                    isSelectAllPending = false
                    transactionAdapter.selectAll()
                }

                if (isScrollDownRequested) {
                    isScrollDownRequested = false
                    val lastIndex = transactionAdapter.itemCount - 1
                    if (lastIndex >= 0) {
                        rvTransactions.post {
                            rvTransactions.scrollToPosition(lastIndex)
                        }
                    }
                }

                val validSelectedIds =
                    transactionAdapter.selectedIds.filter { id -> transactionsList.any { it.id == id } }
                transactionAdapter.selectedIds.clear()
                transactionAdapter.selectedIds.addAll(validSelectedIds)
                updateSelectionUI()
            }
        }
    }

    private fun showTransactionDetailsBottomSheet(transaction: Transaction) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_transaction, null)
        bottomSheetDialog.setContentView(view)

        val bsType = view.findViewById<TextView>(R.id.bsType)
        val bsAmount = view.findViewById<TextView>(R.id.bsAmount)
        val bsCategoryValue = view.findViewById<TextView>(R.id.bsCategoryValue)
        val bsDateValue = view.findViewById<TextView>(R.id.bsDateValue)
        val bsCommentValue = view.findViewById<TextView>(R.id.bsCommentValue)
        val bsCommentLabel = view.findViewById<TextView>(R.id.bsCommentLabel)
        val divider3 = view.findViewById<View>(R.id.divider3)
        val btnEdit = view.findViewById<Button>(R.id.btnEdit)
        val btnDelete = view.findViewById<Button>(R.id.btnDelete)

        bsCategoryValue.text = transaction.category

        val fullDateFormatter = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("uk", "UA"))
        fullDateFormatter.timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        bsDateValue.text = fullDateFormatter.format(Date(transaction.date))

        if (transaction.comment.isNotBlank()) {
            bsCommentValue.text = transaction.comment
            bsCommentValue.visibility = View.VISIBLE
            bsCommentLabel.visibility = View.VISIBLE
            divider3.visibility = View.VISIBLE
        } else {
            bsCommentValue.visibility = View.GONE
            bsCommentLabel.visibility = View.GONE
            divider3.visibility = View.GONE
        }

        if (transaction.type == "income") {
            bsType.text = "Дохід"
            bsType.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.dark_green
                )
            )
            bsAmount.text = String.format(Locale.US, "+ %.2f ₴", transaction.amount)
            bsAmount.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.dark_green
                )
            )
        } else {
            bsType.text = "Витрата"
            bsType.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.dark_red
                )
            )
            bsAmount.text = String.format(Locale.US, "- %.2f ₴", transaction.amount)
            bsAmount.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    this,
                    R.color.text_primary
                )
            )
        }

        btnEdit.setOnClickListener {
            bottomSheetDialog.dismiss()
            openEditTransaction(transaction)
        }

        btnDelete.setOnClickListener {
            bottomSheetDialog.dismiss()
            showDeleteConfirmationDialog(listOf(transaction.id))
        }

        bottomSheetDialog.show()
    }

    private fun openEditTransaction(transaction: Transaction) {
        val intent = Intent(this, AddTransactionActivity::class.java).apply {
            putExtra("EXTRA_ID", transaction.id)
            putExtra("EXTRA_TYPE", transaction.type)
            putExtra("EXTRA_AMOUNT", transaction.amount)
            putExtra("EXTRA_CATEGORY", transaction.category)
            putExtra("EXTRA_COMMENT", transaction.comment)
            putExtra("EXTRA_DATE", transaction.date)
            putExtra("EXTRA_COLOR_INDEX", transaction.iconColorIndex)
        }
        startActivity(intent)
    }

    private fun setupSwipeActions() {
        val swipeCallback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val clipPath = android.graphics.Path()
            private val cardRect = android.graphics.RectF()

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                if (transactionAdapter.selectedIds.isNotEmpty()) return 0
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return
                val transaction = transactionAdapter.getTransactionAt(position) ?: return

                transactionAdapter.notifyItemChanged(position)

                if (direction == androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
                    showDeleteConfirmationDialog(listOf(transaction.id))
                } else if (direction == androidx.recyclerview.widget.ItemTouchHelper.RIGHT) {
                    openEditTransaction(transaction)
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val holder = viewHolder as? TransactionAdapter.TransactionViewHolder ?: return
                val cardView = holder.cardView
                val itemView = holder.itemView

                if (dX != 0f) {
                    val density = resources.displayMetrics.density
                    val cornerRadius = 14f * density
                    val iconMargin = (18 * density).toInt()

                    cardRect.set(
                        (itemView.left + cardView.left).toFloat(),
                        (itemView.top + cardView.top).toFloat(),
                        (itemView.left + cardView.right).toFloat(),
                        (itemView.top + cardView.bottom).toFloat()
                    )

                    c.save()
                    clipPath.reset()
                    clipPath.addRoundRect(
                        cardRect,
                        cornerRadius,
                        cornerRadius,
                        android.graphics.Path.Direction.CW
                    )
                    c.clipPath(clipPath)

                    if (dX > 0) {
                        paint.color = androidx.core.content.ContextCompat.getColor(
                            this@MainActivity,
                            R.color.dark_green
                        )
                        c.drawRect(cardRect, paint)

                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@MainActivity,
                            android.R.drawable.ic_menu_edit
                        )
                        icon?.let {
                            it.setTint(Color.WHITE)
                            val size = it.intrinsicHeight
                            val top = (cardRect.centerY() - size / 2f).toInt()
                            val left = (cardRect.left + iconMargin).toInt()
                            it.setBounds(left, top, left + size, top + size)
                            it.draw(c)
                        }
                    } else {
                        paint.color = Color.parseColor("#C62828")
                        c.drawRect(cardRect, paint)

                        val icon = androidx.core.content.ContextCompat.getDrawable(
                            this@MainActivity,
                            android.R.drawable.ic_menu_delete
                        )
                        icon?.let {
                            it.setTint(Color.WHITE)
                            val size = it.intrinsicHeight
                            val top = (cardRect.centerY() - size / 2f).toInt()
                            val right = (cardRect.right - iconMargin).toInt()
                            it.setBounds(right - size, top, right, top + size)
                            it.draw(c)
                        }
                    }
                    c.restore()
                }

                getDefaultUIUtil().onDraw(
                    c,
                    recyclerView,
                    cardView,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                val holder = viewHolder as? TransactionAdapter.TransactionViewHolder
                if (holder != null) {
                    getDefaultUIUtil().clearView(holder.cardView)
                } else {
                    super.clearView(recyclerView, viewHolder)
                }
            }
        }

        androidx.recyclerview.widget.ItemTouchHelper(swipeCallback)
            .attachToRecyclerView(rvTransactions)
    }

    private fun showDeleteConfirmationDialog(transactionIds: List<String>) {
        val message = if (transactionIds.size == 1) {
            "Ви впевнені, що хочете видалити цю транзакцію?"
        } else {
            "Ви впевнені, що хочете видалити ${transactionIds.size} транзакцій?"
        }

        AlertDialog.Builder(this).setTitle("Підтвердження видалення").setMessage(message)
            .setPositiveButton("Видалити") { _, _ ->
                scheduleTransactionsDeletion(transactionIds)
            }.setNegativeButton("Скасувати", null).show()
    }

    private fun scheduleTransactionsDeletion(transactionIds: List<String>) {
        deleteCountDownTimer?.cancel()
        if (pendingDeleteIds.isNotEmpty()) {
            deleteTransactions(pendingDeleteIds.toList())
        }

        clearSelection()
        pendingDeleteIds = transactionIds.toSet()
        recalculateBalances()
        applyFiltersAndSort()

        tvUndoMessage.text = if (transactionIds.size == 1) {
            "Транзакцію видалено"
        } else {
            "Видалено транзакцій: ${transactionIds.size}"
        }

        pbUndoTimer.max = 5000
        pbUndoTimer.progress = 5000
        tvUndoSeconds.text = "5"
        layoutUndoDelete.visibility = View.VISIBLE

        deleteCountDownTimer = object : CountDownTimer(5000L, 50L) {
            override fun onTick(millisUntilFinished: Long) {
                pbUndoTimer.progress = millisUntilFinished.toInt()
                val secondsLeft = kotlin.math.ceil(millisUntilFinished / 1000.0).toInt()
                tvUndoSeconds.text = secondsLeft.toString()
            }

            override fun onFinish() {
                pbUndoTimer.progress = 0
                tvUndoSeconds.text = "0"
                layoutUndoDelete.visibility = View.GONE

                val idsToCommit = pendingDeleteIds.toList()
                pendingDeleteIds = emptySet()
                if (idsToCommit.isNotEmpty()) {
                    deleteTransactions(idsToCommit)
                }
            }
        }.start()
    }

    private fun cancelScheduledDeletion() {
        deleteCountDownTimer?.cancel()
        pendingDeleteIds = emptySet()
        layoutUndoDelete.visibility = View.GONE
        recalculateBalances()
        applyFiltersAndSort()
        Toast.makeText(this, "Видалення скасовано", Toast.LENGTH_SHORT).show()
    }

    private fun deleteTransactions(transactionIds: List<String>) {
        val currentUser = auth.currentUser ?: return
        val batch = db.batch()

        transactionIds.forEach { id ->
            val docRef = db.collection("users").document(currentUser.uid).collection("transactions")
                .document(id)
            batch.delete(docRef)
        }

        batch.commit().addOnSuccessListener {
            Toast.makeText(this, "Успішно видалено", Toast.LENGTH_SHORT).show()
            LocalBackupManager.syncFilesAsync(applicationContext)
            clearSelection()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Помилка видалення: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        (application as? MyFinanceApp)?.resetInactivityTimer()

        if (event.action == android.view.MotionEvent.ACTION_DOWN) {
            val currentFocus = currentFocus
            if (currentFocus is com.google.android.material.textfield.TextInputEditText) {
                val outRect = android.graphics.Rect()
                currentFocus.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    currentFocus.clearFocus()
                    val imm =
                        getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }
}