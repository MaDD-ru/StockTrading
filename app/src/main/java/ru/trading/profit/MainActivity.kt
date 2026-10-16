package ru.trading.profit

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

private const val BG = 0xFF0B1018.toInt()
private const val PANEL = 0xFF151D29.toInt()
private const val PANEL2 = 0xFF1B2635.toInt()
private const val WHITE = 0xFFF3F6FA.toInt()
private const val MUTED = 0xFF9BA9BA.toInt()
private const val GREEN = 0xFF35D49A.toInt()
private const val RED = 0xFFFF647C.toInt()
private const val BLUE = 0xFF6EA8FE.toInt()

private data class Trade(
    val id: Long,
    val date: String,
    val buy: Double,
    val sell: Double,
    val quantity: Double,
    val buyFeeRate: Double,
    val sellFeeRate: Double,
    val taxRate: Double,
    val gross: Double,
    val buyFee: Double,
    val sellFee: Double,
    val beforeTax: Double,
    val tax: Double,
    val net: Double
)

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var buyInput: EditText
    private lateinit var sellInput: EditText
    private lateinit var qtyInput: EditText
    private lateinit var buyFeeInput: EditText
    private lateinit var sellFeeInput: EditText
    private lateinit var taxInput: EditText
    private lateinit var resultNet: TextView
    private lateinit var resultState: TextView
    private lateinit var resultRows: LinearLayout
    private lateinit var historyBox: LinearLayout
    private lateinit var saveButton: Button
    private var lastTrade: Trade? = null
    private val prefs by lazy { getSharedPreferences("trading_local", MODE_PRIVATE) }
    private val fmt = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale("ru", "RU")))
    private val fmtQty = DecimalFormat("#,##0.####", DecimalFormatSymbols(Locale("ru", "RU")))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        buildUi()
        listOf(buyInput, sellInput, qtyInput, buyFeeInput, sellFeeInput, taxInput).forEach { input ->
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { calculate() }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        calculate()
        renderHistory()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(18)) }
        header.addView(label("TRADING", 12, GREEN, true))
        header.addView(label("Расчёт прибыли акций", 27, WHITE, true).apply { setPadding(0, dp(5), 0, dp(4)) })
        header.addView(label("Калькулятор сделки • работает офлайн", 13, MUTED, false))
        root.addView(header)

        val summary = panel()
        summary.addView(label("ЧИСТАЯ ПРИБЫЛЬ ПОСЛЕ НАЛОГА", 11, MUTED, true))
        resultNet = label("0,00 ₽", 32, WHITE, true).apply { setPadding(0, dp(7), 0, dp(3)) }
        summary.addView(resultNet)
        resultState = label("Введите параметры сделки", 13, MUTED, false)
        summary.addView(resultState)
        root.addView(summary, params(bottom = 16))

        root.addView(sectionTitle("Параметры сделки"))
        val fields = panel()
        buyInput = addField(fields, "Цена покупки, ₽", "0", "100")
        sellInput = addField(fields, "Цена продажи, ₽", "0", "110")
        qtyInput = addField(fields, "Количество акций", "0", "250")
        root.addView(fields, params(bottom = 16))

        root.addView(sectionTitle("Комиссии и налог"))
        val fees = panel()
        buyFeeInput = addField(fees, "Комиссия за покупку, %", "0,3", "0,3")
        sellFeeInput = addField(fees, "Комиссия за продажу, %", "0,3", "0,3")
        taxInput = addField(fees, "Налог на прибыль, %", "13", "13")
        root.addView(fees, params(bottom = 16))

        root.addView(sectionTitle("Разбор расчёта"))
        resultRows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultRows, params(bottom = 16))

        saveButton = Button(this).apply {
            text = "Сохранить сделку в историю"
            isAllCaps = false
            setTextColor(0xFF06140F.toInt())
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(GREEN, 14)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { saveTrade() }
        }
        root.addView(saveButton, params(bottom = 22))

        val histHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        histHeader.addView(sectionTitle("История сделок"), LinearLayout.LayoutParams(0, dp(38), 1f))
        val clear = TextView(this).apply {
            text = "Очистить"; textSize = 12f; setTextColor(RED); gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), 0, 0, 0)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity).setTitle("Очистить историю?")
                    .setMessage("Все сохранённые сделки будут удалены с этого телефона.")
                    .setNegativeButton("Отмена", null).setPositiveButton("Удалить") { _, _ ->
                        prefs.edit().remove("trades").apply(); renderHistory()
                    }.show()
            }
        }
        histHeader.addView(clear)
        root.addView(histHeader)
        historyBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(historyBox)
        root.addView(label("Данные хранятся только на этом устройстве. Расчёт налога ориентировочный; фактический налог зависит от применимых правил и вашей налоговой ситуации.", 11, MUTED, false).apply { setPadding(0, dp(18), 0, 0) })
    }

    private fun addField(parent: LinearLayout, title: String, hint: String, value: String): EditText {
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(4), 0, dp(9)) }
        wrap.addView(label(title, 13, MUTED, false).apply { setPadding(0, 0, 0, dp(5)) })
        val edit = EditText(this).apply {
            setSingleLine(true); textSize = 16f; setTextColor(WHITE); setHintTextColor(0xFF718096.toInt())
            setHint(hint); setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            background = rounded(PANEL2, 10); setPadding(dp(12), dp(9), dp(12), dp(9)); minimumHeight = dp(46)
        }
        wrap.addView(edit)
        parent.addView(wrap)
        return edit
    }

    private fun calculate() {
        val buy = number(buyInput.text.toString())
        val sell = number(sellInput.text.toString())
        val qty = number(qtyInput.text.toString())
        val buyRate = number(buyFeeInput.text.toString()) / 100.0
        val sellRate = number(sellFeeInput.text.toString()) / 100.0
        val taxRate = number(taxInput.text.toString()) / 100.0
        val valid = buy >= 0 && sell >= 0 && qty >= 0 && buyRate >= 0 && sellRate >= 0 && taxRate >= 0 && buyRate <= 1 && sellRate <= 1 && taxRate <= 1
        if (!valid) {
            resultNet.text = "Проверьте значения"
            resultNet.setTextColor(RED)
            resultState.text = "Проценты должны быть от 0 до 100."
            saveButton.isEnabled = false
            return
        }
        val buyVolume = buy * qty
        val sellVolume = sell * qty
        val gross = sellVolume - buyVolume
        val buyFee = buyVolume * buyRate
        val sellFee = sellVolume * sellRate
        val beforeTax = gross - buyFee - sellFee
        val tax = max(beforeTax, 0.0) * taxRate
        val net = beforeTax - tax
        lastTrade = Trade(System.currentTimeMillis(), "", buy, sell, qty, buyRate, sellRate, taxRate, gross, buyFee, sellFee, beforeTax, tax, net)
        resultNet.text = money(net)
        resultNet.setTextColor(if (net > 0.000001) GREEN else if (net < -0.000001) RED else WHITE)
        resultState.text = when { net > 0.000001 -> "Прибыльная сделка"; net < -0.000001 -> "Убыточная сделка"; else -> "Без прибыли и убытка" }
        resultState.setTextColor(if (net < -0.000001) RED else if (net > 0.000001) GREEN else MUTED)
        saveButton.isEnabled = buy > 0 && sell > 0 && qty > 0
        saveButton.alpha = if (saveButton.isEnabled) 1f else 0.55f
        resultRows.removeAllViews()
        addResultRow("Объём покупки", money(buyVolume), WHITE)
        addResultRow("Объём продажи", money(sellVolume), WHITE)
        addResultRow("Прибыль до комиссий", money(gross), if (gross >= 0) GREEN else RED)
        addResultRow("Комиссия покупки", "− ${money(buyFee)}", MUTED)
        addResultRow("Комиссия продажи", "− ${money(sellFee)}", MUTED)
        addResultRow("Прибыль после комиссий", money(beforeTax), if (beforeTax >= 0) GREEN else RED)
        addResultRow("Налог", "− ${money(tax)}", MUTED)
        addResultRow("Чистая прибыль", money(net), if (net >= 0) GREEN else RED, true)
        val breakEven = if (qty > 0 && sellRate < 1) buy * (1 + buyRate) / (1 - sellRate) else Double.NaN
        addResultRow("Цена безубыточности", if (breakEven.isFinite()) money(breakEven) + " / акция" else "—", BLUE)
    }

    private fun addResultRow(title: String, value: String, color: Int, bold: Boolean = false) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(11), dp(14), dp(11)) }
        row.addView(label(title, 13, if (bold) WHITE else MUTED, bold), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label(value, 14, color, bold).apply { gravity = Gravity.RIGHT })
        row.background = rounded(if (bold) PANEL2 else PANEL, 9)
        resultRows.addView(row, params(bottom = 5))
    }

    private fun saveTrade() {
        val trade = lastTrade ?: return
        if (trade.buy <= 0 || trade.sell <= 0 || trade.quantity <= 0) return
        val saved = trade.copy(date = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru", "RU")).format(Date()))
        val arr = try { JSONArray(prefs.getString("trades", "[]")) } catch (_: Exception) { JSONArray() }
        val obj = JSONObject().apply {
            put("id", saved.id); put("date", saved.date); put("buy", saved.buy); put("sell", saved.sell); put("quantity", saved.quantity)
            put("buyFeeRate", saved.buyFeeRate); put("sellFeeRate", saved.sellFeeRate); put("taxRate", saved.taxRate)
            put("gross", saved.gross); put("buyFee", saved.buyFee); put("sellFee", saved.sellFee); put("beforeTax", saved.beforeTax); put("tax", saved.tax); put("net", saved.net)
        }
        arr.put(obj)
        prefs.edit().putString("trades", arr.toString()).apply()
        renderHistory()
        android.widget.Toast.makeText(this, "Сделка сохранена", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun renderHistory() {
        if (!::historyBox.isInitialized) return
        historyBox.removeAllViews()
        val arr = try { JSONArray(prefs.getString("trades", "[]")) } catch (_: Exception) { JSONArray() }
        if (arr.length() == 0) {
            val empty = panel()
            empty.addView(label("Пока нет сохранённых сделок. Рассчитайте сделку и нажмите кнопку сохранения.", 13, MUTED, false))
            historyBox.addView(empty)
            return
        }
        for (i in arr.length() - 1 downTo 0) {
            val o = arr.optJSONObject(i) ?: continue
            val net = o.optDouble("net")
            val card = panel()
            val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            top.addView(label(o.optString("date"), 12, MUTED, false), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            top.addView(label(money(net), 16, if (net >= 0) GREEN else RED, true))
            card.addView(top)
            card.addView(label("Покупка ${fmt.format(o.optDouble("buy"))} ₽  →  продажа ${fmt.format(o.optDouble("sell"))} ₽", 13, WHITE, false).apply { setPadding(0, dp(8), 0, dp(4)) })
            card.addView(label("Количество: ${fmtQty.format(o.optDouble("quantity"))}   •   Налог: ${money(o.optDouble("tax"))}", 12, MUTED, false))
            val remove = TextView(this).apply {
                text = "Удалить эту сделку"; textSize = 12f; setTextColor(RED); setPadding(0, dp(10), 0, 0)
                setOnClickListener {
                    val current = JSONArray(prefs.getString("trades", "[]")); val next = JSONArray()
                    for (j in 0 until current.length()) if (current.optJSONObject(j)?.optLong("id") != o.optLong("id")) next.put(current.getJSONObject(j))
                    prefs.edit().putString("trades", next.toString()).apply(); renderHistory()
                }
            }
            card.addView(remove)
            historyBox.addView(card, params(bottom = 8))
        }
    }

    private fun number(s: String): Double = s.trim().replace(" ", "").replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun money(v: Double): String = "${fmt.format(v)} ₽"
    private fun panel(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)); background = rounded(PANEL, 14)
    }
    private fun sectionTitle(s: String) = label(s, 17, WHITE, true).apply { setPadding(0, dp(2), 0, dp(9)) }
    private fun label(s: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun params(bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (bottom > 0) bottomMargin = dp(bottom) }
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
