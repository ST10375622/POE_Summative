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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

class MonthlyReportActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var recyclerView: RecyclerView
    private lateinit var textMonth: TextView
    private lateinit var textTotalSpent: TextView
    private lateinit var textTopCategory: TextView
    private lateinit var textTransactionCount: TextView

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private lateinit var adapter: MonthlyReportAdapter

    private var currentMonth = LocalDate.now().monthValue
    private var currentYear = LocalDate.now().year

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_monthly_report)

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
        textMonth = findViewById(R.id.textCurrentMonth)
        textTotalSpent = findViewById(R.id.txtTotalSpent)
        textTopCategory = findViewById(R.id.txtTopCategory)
        textTransactionCount = findViewById(R.id.txtTransactionCount)
        recyclerView = findViewById(R.id.recyclerMonthlyExpenses)
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        adapter = MonthlyReportAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<Button>(R.id.btnPreviousMonth).setOnClickListener {
            currentMonth--
            if (currentMonth < 1) {
                currentMonth = 12
                currentYear--
            }
            updateMonthText()
            loadMonthlyData()
        }

        findViewById<Button>(R.id.btnNextMonth).setOnClickListener {
            currentMonth++
            if (currentMonth > 12) {
                currentMonth = 1
                currentYear++
            }
            updateMonthText()
            loadMonthlyData()
        }

        findViewById<Button>(R.id.btnAllExpenses).setOnClickListener {
            Toast.makeText(this, "All Expenses", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AllExpensesActivity::class.java))
        }

        fetchUserName(textName)
        setupNavigation()
        updateMonthText()
        loadMonthlyData()

    }

    private fun fetchUserName(textView: TextView){
        db.collection("user").document(currentUserId)
            .get().addOnSuccessListener {
                textView.text = "Hello, ${it.getString("name")}"
            }
    }

    private fun updateMonthText() {
        val monthName = Month.of(currentMonth).getDisplayName(TextStyle.FULL, Locale.getDefault())
        textMonth.text = "$monthName $currentYear"
    }

    private fun loadMonthlyData() {
        val expenses = mutableListOf<Expense>()
        val categorySums = mutableMapOf<String, Double>()

        db.collection("user").document(currentUserId)
            .collection("categories")
            .get()
            .addOnSuccessListener { categorySnap ->
                val categoryDocs = categorySnap.documents
                if (categoryDocs.isEmpty()) {
                    adapter.submitList(emptyList())
                    updateStats(0.0, "-", 0)
                    return@addOnSuccessListener
                }

                var completed = 0
                categoryDocs.forEach { categoryDoc ->
                    val catId = categoryDoc.id
                    db.collection("user").document(currentUserId)
                        .collection("categories").document(catId)
                        .collection("expenses")
                        .get()
                        .addOnSuccessListener { expenseSnap ->
                            val list = expenseSnap.documents.mapNotNull { it.toObject(Expense::class.java) }
                            val filtered = list.filter {
                                val date = try {
                                    LocalDate.parse(it.date)
                                } catch (e: Exception) {
                                    null
                                }
                                date != null && date.monthValue == currentMonth && date.year == currentYear
                            }

                            for (exp in filtered) {
                                categorySums[exp.categoryId] = categorySums.getOrDefault(exp.categoryId, 0.0) + exp.amount
                            }

                            expenses.addAll(filtered)
                        }
                        .addOnCompleteListener {
                            completed++
                            if (completed == categoryDocs.size) {
                                adapter.submitList(expenses)
                                val total = expenses.sumOf { it.amount }
                                val top = categorySums.maxByOrNull { it.value }?.key
                                val topName = categoryDocs.find { it.id == top }?.getString("name") ?: "-"
                                val topValue = categorySums[top] ?: 0.0
                                updateStats(total, "$topName (R${topValue.toInt()})", expenses.size)
                            }
                        }
                }
            }
    }

    private fun updateStats(total: Double, topCategory: String, count: Int) {
        textTotalSpent.text = "Total Spent: R ${total.toInt()}"
        textTopCategory.text = "Top Category: $topCategory"
        textTransactionCount.text = "Transactions: $count"
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.Budget -> startActivity(Intent(this, BudgetActivity::class.java))
                R.id.Progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.home -> startActivity(Intent(this, HomeActivity::class.java))
                R.id.Notification -> startActivity(Intent(this, NotificationActivity::class.java))
                R.id.Profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.Report -> recreate()
                else -> false
            }
            true
        }
    }
}