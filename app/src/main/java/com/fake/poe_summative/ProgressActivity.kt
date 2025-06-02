package com.fake.poe_summative

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
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
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.w3c.dom.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ProgressActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var textRange: TextView
    private lateinit var btnPickRange: Button
    private lateinit var spentAmountText: TextView
    private lateinit var labelStatus: TextView
    private lateinit var barChart: BarChart

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private var startDate: LocalDate = LocalDate.now().withDayOfMonth(1)
    private var endDate: LocalDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_progress)

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
        textRange = findViewById(R.id.textSelectedRange)
        btnPickRange = findViewById(R.id.btnSelectDateRange)
        spentAmountText = findViewById(R.id.textAmountSpent)
        labelStatus = findViewById(R.id.labelStatus)
        barChart = findViewById(R.id.barChart)
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        fetchUserName(textName)
        setupNavigation()

        updateDateRangeText()
        fetchAndDisplayProgress()

        btnPickRange.setOnClickListener {
            showDateRangePicker()
        }
    }

    private fun fetchUserName(textView: TextView){
        db.collection("user").document(currentUserId)
            .get().addOnSuccessListener {
                textView.text = "Hello, ${it.getString("name")}"
            }
    }

    private fun updateDateRangeText() {
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
        textRange.text = "${startDate.format(formatter)} to ${endDate.format(formatter)}"
    }

    private fun showDateRangePicker() {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select Date Range")
            .build()

        picker.show(supportFragmentManager, picker.toString())

        picker.addOnPositiveButtonClickListener { range ->
            val start = range.first ?: return@addOnPositiveButtonClickListener
            val end = range.second ?: return@addOnPositiveButtonClickListener

            startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
            endDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()

            updateDateRangeText()
            fetchAndDisplayProgress()
        }
    }

    private fun fetchAndDisplayProgress() {
        db.collection("user").document(currentUserId)
            .collection("categories")
            .get()
            .addOnSuccessListener { categorySnap ->
                val categoryIds = categorySnap.documents.map { it.id }

                var totalExpenses = 0.0
                var completed = 0

                if (categoryIds.isEmpty()) {
                    updateProgressChart(0.0, 0.0)
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

                            totalExpenses += filtered.sumOf { it.amount }
                        }
                        .addOnCompleteListener {
                            completed++
                            if (completed == categoryIds.size) {
                                fetchBudgetAndDisplay(totalExpenses)
                            }
                        }
                }
            }
    }

    private fun fetchBudgetAndDisplay(expenseTotal: Double) {
        db.collection("user").document(currentUserId)
            .collection("Budget").document("main")
            .get()
            .addOnSuccessListener { snap ->
                val budget = snap.toObject(Budget::class.java)
                val budgetAmount = budget?.monthlyBudget ?: 0.0
                updateProgressChart(budgetAmount, expenseTotal)
            }
    }

    private fun updateProgressChart(budgetAmount: Double, expensesAmount: Double) {
        spentAmountText.text = "R ${expensesAmount.toInt()}"

        if (budgetAmount == 0.0) {
            labelStatus.text = "No Budget Set"
            labelStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        } else {
            val percentSpent = (expensesAmount / budgetAmount) * 100
            if (percentSpent <= 100) {
                labelStatus.text = "On Track"
                labelStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                labelStatus.text = "Over Budget"
                labelStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
        }

        val entries = listOf(
            BarEntry(0f, budgetAmount.toFloat()),
            BarEntry(1f, expensesAmount.toFloat())
        )

        val dataSet = BarDataSet(entries, "Progress")
        dataSet.colors = listOf(
            ContextCompat.getColor(this, android.R.color.holo_blue_light),
            ContextCompat.getColor(this, android.R.color.holo_red_dark)
        )
        dataSet.valueTextSize = 16f

        val data = BarData(dataSet)
        data.barWidth = 0.5f

        barChart.data = data
        barChart.description.isEnabled = false
        barChart.legend.isEnabled = false
        barChart.setFitBars(true)

        val xAxis = barChart.xAxis
        xAxis.valueFormatter = IndexAxisValueFormatter(listOf("Budget", "Expenses"))
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.granularity = 1f
        xAxis.textSize = 16f

        barChart.axisLeft.axisMinimum = 0f
        barChart.axisRight.isEnabled = false
        barChart.animateY(1000)
        barChart.invalidate()
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.Budget -> startActivity(Intent(this, BudgetActivity::class.java))
                R.id.home -> startActivity(Intent(this, HomeActivity::class.java))
                //R.id.Profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.Progress -> recreate()
                else -> false
            }
            true
        }
    }
}