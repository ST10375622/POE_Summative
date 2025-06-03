package com.fake.poe_summative

import android.content.Intent
import android.net.Uri
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
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import org.w3c.dom.Text
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AllExpensesActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var adapter: AllExpenseAdapter
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private lateinit var recyclerView: RecyclerView
    private lateinit var textDateRange: TextView
    private lateinit var btnPickDate: Button

    private var startDate = LocalDate.now().withDayOfMonth(1)
    private var endDate = LocalDate.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_all_expenses)

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
        textDateRange = findViewById(R.id.textSelectedRange)
        btnPickDate = findViewById(R.id.btnSelectDateRange)
        recyclerView = findViewById(R.id.RecyclerAllExpenses)
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        adapter = AllExpenseAdapter {receiptUrl ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(receiptUrl))
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateDateRangeText()
        fetchExpenses()
        fetchUserName(textName)
        setupNavigation()

        btnPickDate.setOnClickListener {
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
            fetchExpenses()
        }
    }

    private fun fetchExpenses() {
        db.collection("user").document(currentUserId)
            .collection("categories")
            .get()
            .addOnSuccessListener { categorySnap ->
                val categoryIds = categorySnap.documents.map {it.id}
                val allExpenses = mutableListOf<Expense>()
                var completed = 0

                if (categoryIds.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@addOnSuccessListener
                }

                categoryIds.forEach { catId ->
                    db.collection("user").document(currentUserId)
                        .collection("categories").document(catId)
                        .collection("expenses")
                        .get()
                        .addOnSuccessListener { expenseSnap ->
                            val expenses = expenseSnap.documents.mapNotNull {
                                it.toObject(Expense::class.java)
                            }.filter {
                                val date = try {
                                    LocalDate.parse(it.date)
                                } catch (e: Exception) {
                                    null
                                }
                                date != null && !date.isBefore(startDate) && !date.isAfter(endDate)
                            }

                            allExpenses += expenses
                        }
                        .addOnCompleteListener {
                            completed++
                            if (completed == categoryIds.size) {
                                adapter.submitList(allExpenses)
                            }
                        }
                }

            }
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
                R.id.Report -> startActivity(Intent(this, MonthlyReportActivity::class.java))
                else -> false
            }
            true
        }
    }
}