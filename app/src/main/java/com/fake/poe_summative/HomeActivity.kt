package com.fake.poe_summative

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.setPadding
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class HomeActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var pieChart: PieChart
    private lateinit var legendContainer: LinearLayout
    private lateinit var textTotalExpense: TextView
    private lateinit var textDateRange: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private var startDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var endDate: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        //Initialize Firebase
        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        pieChart = findViewById(R.id.pieChart)
        legendContainer = findViewById(R.id.legendContainer)
        textTotalExpense = findViewById(R.id.textTotalExpenses)
        textDateRange = findViewById(R.id.textSelectedRange)

        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        val textBudget = findViewById<TextView>(R.id.textMonthlyBudget)
        val textLeft = findViewById<TextView>(R.id.textMoneyLeft)
        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()


        // Set initial date range text
        updateDateRangeText()

        // Date Range Picker setup
        textDateRange.setOnClickListener {
            showDateRangePicker()
        }

        fetchAndDisplayExpenses()

        fetchUserName(textName)
        fetchUserBudget(textBudget, textLeft)
        setupNavigation()
    }

    private fun fetchUserName(textView: TextView){
        db.collection("user").document(currentUserId)
            .get().addOnSuccessListener {
                textView.text = "Hello, ${it.getString("name")}"
            }
    }

    private fun updateDateRangeText() {
        val formatter = DateTimeFormatter.ofPattern("dd MMM YYYY")
        textDateRange.text = "${startDate.format(formatter)} to ${endDate.format(formatter)}"
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("select Period")
            .build()

        picker.show(supportFragmentManager, picker.toString())

        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first ?: return@addOnPositiveButtonClickListener
            val end = range.second ?: return@addOnPositiveButtonClickListener

            startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
            endDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()

            updateDateRangeText()
            fetchAndDisplayExpenses()
        }
    }

    private fun fetchAndDisplayExpenses() {
        db.collection("user").document(currentUserId)
            .collection("categories")
            .get()
            .addOnSuccessListener { categorySnap ->
                val categoryIds = categorySnap.documents.map {it.id}
                val categoryMap = categorySnap.associate { it.id to it.getString("name").orEmpty() }

                val allExpenses = mutableListOf<Expense>()
                var completed = 0

                if (categoryIds.isEmpty()) {
                    updateChart(emptyList(), categoryMap)
                    return@addOnSuccessListener
                }

                categoryIds.forEach { catId ->
                    db.collection("user").document(currentUserId)
                        .collection("categories").document(catId)
                        .collection("expenses")
                        .get()
                        .addOnSuccessListener { expenseSnap ->
                            val filtered = expenseSnap.documents.mapNotNull {
                                it.toObject(Expense::class.java)
                            }.filter {
                                try {
                                    val expenseDate = LocalDate.parse(it.date)
                                    !expenseDate.isBefore(startDate) && !expenseDate.isAfter(endDate)
                                } catch (e: Exception) {
                                    false
                                }
                            }

                            allExpenses.addAll(filtered)
                        }
                        .addOnCompleteListener {
                            completed++
                            if (completed == categoryIds.size) {
                                updateChart(allExpenses, categoryMap)
                            }
                        }
                }

            }
    }

    private fun updateChart(expenses: List<Expense>, categoryMap: Map<String, String>) {
        val grouped = expenses.groupBy { it.categoryId }
        val entries = grouped.map {
            PieEntry(it.value.sumOf { e -> e.amount }.toFloat())
        }

        val labels = grouped.map { categoryMap[it.key] ?: "Unkown" }
        val total = expenses.sumOf { it.amount }

        textTotalExpense.text = "R$total\nYour Total Expenses in range"

        val dataSet = PieDataSet(entries, "")
        dataSet.colors = ColorTemplate.MATERIAL_COLORS.toList()
        dataSet.valueFormatter = PercentFormatter(pieChart)
        dataSet.setDrawValues(true)

        val data = PieData(dataSet)
        data.setValueTextSize(14f)

        pieChart.data = data
        pieChart.setUsePercentValues(true)
        pieChart.invalidate()

        pieChart.legend.apply {
            isEnabled = false
        }

        legendContainer.removeAllViews()
        labels.forEachIndexed { i, label ->
            val view = TextView(this).apply {
                text = "$label: R${"%.2f".format(entries[i].value)}"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.black))
                setPadding(8,12,8,12)
            }
            legendContainer.addView(view)
        }
    }

    private fun fetchUserBudget(textBudget: TextView, textLeft: TextView) {
        val userDoc = db.collection("user").document(currentUserId)

        // Listen to budget changes
        userDoc.collection("Budget").document("main")
            .addSnapshotListener { snapshot, _ ->
                val budget = snapshot?.toObject(Budget::class.java)
                budget?.let {
                    textBudget.text = "Budget: R ${it.monthlyBudget}"

                    // Calculate total expenses again
                    userDoc.collection("categories").get()
                        .addOnSuccessListener { categorySnap ->
                            val categoryIds = categorySnap.documents.map { it.id }
                            var totalExpenses = 0.0
                            var completed = 0

                            if (categoryIds.isEmpty()) {
                                updateBudgetAmountLeft(it, 0.0)
                                textLeft.text = "Left: R ${it.monthlyBudget}"
                                return@addOnSuccessListener
                            }

                            categoryIds.forEach { catId ->
                                userDoc.collection("categories").document(catId)
                                    .collection("expenses").get()
                                    .addOnSuccessListener { expenseSnap ->
                                        val sum = expenseSnap.documents.mapNotNull { doc ->
                                            doc.toObject(Expense::class.java)?.amount
                                        }.sum()
                                        totalExpenses += sum
                                    }
                                    .addOnCompleteListener {
                                        completed++
                                        if (completed == categoryIds.size) {
                                            val left = budget.monthlyBudget - totalExpenses
                                            textLeft.text = "Left: R $left"
                                            updateBudgetAmountLeft(budget, totalExpenses)
                                        }
                                    }
                            }
                        }
                }
            }
    }

    private fun updateBudgetAmountLeft(budget: Budget, totalExpenses: Double) {
        val updatedBudget = budget.copy(amountLeft = budget.monthlyBudget - totalExpenses)
        db.collection("user").document(currentUserId)
            .collection("Budget").document("main")
            .set(updatedBudget)
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.Budget -> startActivity(Intent(this, BudgetActivity::class.java))
                R.id.Progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.Report -> startActivity(Intent(this, MonthlyReportActivity::class.java))
                R.id.Notification -> startActivity(Intent(this, NotificationActivity::class.java))
                //R.id.Profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.home -> recreate()
                else -> false
            }
            true
        }
    }

}