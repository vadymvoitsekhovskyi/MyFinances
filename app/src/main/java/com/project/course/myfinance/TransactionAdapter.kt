package com.project.course.myfinance

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.project.course.myfinance.models.Transaction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

class TransactionAdapter(
    private var transactions: List<Transaction>,
    private val onItemClick: (Transaction) -> Unit,
    private val onItemLongClick: (Transaction) -> Unit
) : RecyclerView.Adapter<TransactionAdapter.TransactionViewHolder>() {

    val selectedIds = mutableSetOf<String>()
    private val timeZone = TimeZone.getTimeZone("Europe/Kyiv")

    private val yearFormatter = SimpleDateFormat("yyyy", Locale("uk", "UA")).apply {
        this.timeZone = this@TransactionAdapter.timeZone
    }

    private val dayMonthFormatter = SimpleDateFormat("dd MMMM", Locale("uk", "UA")).apply {
        this.timeZone = this@TransactionAdapter.timeZone
    }

    private val exactDateFormatter = SimpleDateFormat("ddMMyyyy", Locale.getDefault()).apply {
        this.timeZone = this@TransactionAdapter.timeZone
    }

    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
        this.timeZone = this@TransactionAdapter.timeZone
    }

    class TransactionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val layoutDateHeader: LinearLayout = itemView.findViewById(R.id.layoutDateHeader)
        val tvDateHeader: TextView = itemView.findViewById(R.id.tvDateHeader)
        val tvCategoryEmoji: TextView = itemView.findViewById(R.id.tvCategoryEmoji)
        val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
        val tvTime: TextView = itemView.findViewById(R.id.tvTime)
        val ivCommentIcon: android.widget.ImageView = itemView.findViewById(R.id.ivCommentIcon)
        val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        val cardView: View = itemView.findViewById(R.id.cardTransaction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TransactionViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return TransactionViewHolder(view)
    }

    override fun onBindViewHolder(holder: TransactionViewHolder, position: Int) {
        val transaction = transactions[position]
        val timeZone = TimeZone.getTimeZone("Europe/Kyiv")
        val currentDateObj = Date(transaction.date)
        val currentYearStr = yearFormatter.format(currentDateObj)
        val currentDayMonthStr = dayMonthFormatter.format(currentDateObj)
        val currentExactDateStr = exactDateFormatter.format(currentDateObj)

        holder.layoutDateHeader.visibility = View.GONE

        var showYear = false
        var showDate = false

        if (position == 0) {
            showYear = true
            showDate = true
        } else {
            val prevDateObj = Date(transactions[position - 1].date)
            val prevYearStr = yearFormatter.format(prevDateObj)
            val prevExactDateStr = exactDateFormatter.format(prevDateObj)

            if (currentYearStr != prevYearStr) showYear = true
            if (currentExactDateStr != prevExactDateStr) showDate = true
        }

        if (showDate) {
            holder.layoutDateHeader.visibility = View.VISIBLE
            holder.tvDateHeader.text = currentDayMonthStr
        }

        holder.tvCategoryEmoji.text = getCategoryEmoji(transaction.category, transaction.type)

        val isDarkTheme = ThemeManager.isDarkThemeActive(holder.itemView.context)
        val activePalette = if (isDarkTheme) darkEmojiBgPalette else lightEmojiBgPalette

        val paletteIndex = if (transaction.iconColorIndex in activePalette.indices) {
            transaction.iconColorIndex
        } else {
            abs(transaction.id.hashCode()) % activePalette.size
        }

        val (fillHex, strokeHex) = activePalette[paletteIndex]
        val density = holder.itemView.resources.displayMetrics.density

        holder.tvCategoryEmoji.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(Color.parseColor(fillHex))
            setStroke((1 * density).toInt(), Color.parseColor(strokeHex))
        }

        holder.tvCategory.text = transaction.category
        holder.tvTime.text = timeFormatter.format(currentDateObj)
        holder.ivCommentIcon.visibility =
            if (transaction.comment.isNotBlank()) View.VISIBLE else View.GONE

        if (transaction.type == "income") {
            holder.tvAmount.text = String.format(Locale.US, "+ %.2f ₴", transaction.amount)
            holder.tvAmount.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    holder.itemView.context,
                    R.color.dark_green
                )
            )
        } else {
            holder.tvAmount.text = String.format(Locale.US, "- %.2f ₴", transaction.amount)
            holder.tvAmount.setTextColor(
                androidx.core.content.ContextCompat.getColor(
                    holder.itemView.context,
                    R.color.dark_red
                )
            )
        }

        if (selectedIds.contains(transaction.id)) {
            holder.cardView.setBackgroundResource(R.drawable.bg_glossy_card_selected)
        } else {
            holder.cardView.setBackgroundResource(R.drawable.bg_glossy_card)
        }

        holder.cardView.setOnClickListener {
            onItemClick(transaction)
        }

        holder.cardView.setOnLongClickListener {
            onItemLongClick(transaction)
            true
        }
    }

    private fun getCategoryEmoji(category: String, type: String): String {
        return when (category.trim().lowercase()) {
            "кава", "кав'ярня" -> "☕"
            "продукти", "їжа" -> "🛒"
            "надходження" -> "💵"
            "витрати" -> "💸"
            "борг" -> "🤝"
            "повернення" -> "🔄"
            "зарплата" -> "💰"
            "аптека" -> "💊"
            "техніка" -> "💻"
            "дім" -> "🏠"
            "краса та здоров'я", "краса", "здоров'я" -> "🩺"
            "одяг" -> "👕"
            "ресторан" -> "🍽️"
            "подарунки" -> "🎁"
            "транспорт", "таксі" -> "🚕"
            "розваги" -> "🎬"
            else -> if (type == "income") "💰" else "🧾"
        }
    }

    private val darkEmojiBgPalette = listOf(
        "#1B3B2B" to "#2E664A",
        "#1A2E44" to "#2D5075",
        "#3D2E17" to "#6B5128",
        "#2E1F42" to "#513773",
        "#3F1D24" to "#6E323E",
        "#17383B" to "#286166",
        "#232744" to "#3D4475"
    )

    private val lightEmojiBgPalette = listOf(
        "#E4F3EB" to "#B7DFCA",
        "#E3EFFB" to "#B5D2F2",
        "#FDF1DC" to "#F3D39E",
        "#EFE7FA" to "#D0B9F0",
        "#FCE6EA" to "#F2B8C3",
        "#E0F4F5" to "#AEDEE0",
        "#E8EAFA" to "#BFC5F0"
    )

    fun getTransactionAt(position: Int): Transaction? = transactions.getOrNull(position)

    override fun getItemCount(): Int = transactions.size

    fun updateData(newTransactions: List<Transaction>) {
        transactions = newTransactions
        notifyDataSetChanged()
    }

    fun toggleSelection(id: String) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id)
        } else {
            selectedIds.add(id)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    fun selectAll() {
        selectedIds.clear()
        selectedIds.addAll(transactions.map { it.id })
        notifyDataSetChanged()
    }
}