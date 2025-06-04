package com.fake.poe_summative

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private lateinit var streakTextView: TextView
    private lateinit var treeImageView: ImageView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_profile)

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
        streakTextView = findViewById(R.id.streakTextView)
        treeImageView = findViewById(R.id.treeImageView)
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)

        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        fetchUserName(textName)
        setupNavigation()
        updateDailyStreak()
        loadTreeGrowth()
    }

    private fun fetchUserName(textView: TextView){
        db.collection("user").document(currentUserId)
            .get().addOnSuccessListener {
                textView.text = "Hello, ${it.getString("name")}"
            }
    }

    //updates the daily streak
    private fun updateDailyStreak() {
        val prefs = getSharedPreferences("StreakPrefs", MODE_PRIVATE)
        val lastLogin = prefs.getString("last_login", null)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val yesterday = Calendar.getInstance().apply { add(Calendar.DATE, -1) }
        val yesterdayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(yesterday.time)

        val editor = prefs.edit()
        val currentStreak = prefs.getInt("streak", 0)

        when (lastLogin) {
            null -> {
                editor.putInt("streak", 1)
            }
            today -> {
                //already logged in
            }
            yesterdayStr -> {
                editor.putInt("streak", currentStreak + 1)
            }
            else -> {
                editor.putInt("streak", 1) // missed a day
            }

        }

        editor.putString("last_login", today)
        editor.apply()

        streakTextView.text = "Streak: ${prefs.getInt("streak", 1)} days"
    }

    //gets transaction count to update the tree images
    private fun loadTreeGrowth() {
        val userDoc = db.collection("user").document(currentUserId)

        userDoc.collection("categories").get().addOnSuccessListener { categorySnap ->
            val categoryIds = categorySnap.documents.map { it.id }
            var transactionCount = 0
            var completed = 0

            if (categoryIds.isEmpty()) {
                updateTreeImage(0)
                return@addOnSuccessListener
            }

            categoryIds.forEach { catId ->
                userDoc.collection("categories").document(catId)
                    .collection("expenses").get()
                    .addOnSuccessListener { expenseSnap ->
                        transactionCount += expenseSnap.size()
                    }
                    .addOnCompleteListener {
                        completed++
                        if (completed == categoryIds.size) {
                            updateTreeImage(transactionCount)
                        }
                    }
            }
        }
    }

    //updates the Tree image based off the number of transactions
    private fun updateTreeImage(transactionCount: Int) {
        val treeRes = when {
            transactionCount >= 30 -> R.mipmap.tree_stage_7
            transactionCount >= 25 -> R.mipmap.tree_stage_6
            transactionCount >= 20 -> R.mipmap.tree_stage_5
            transactionCount >= 15 -> R.mipmap.tree_stage_4_2
            transactionCount >= 10 -> R.mipmap.tree_stage_3
            transactionCount >= 5 -> R.mipmap.tree_stage_2
            else -> R.mipmap.tree_stage_1
        }

        treeImageView.setImageResource(treeRes)
    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.Budget -> startActivity(Intent(this, BudgetActivity::class.java))
                R.id.Progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.home -> startActivity(Intent(this, HomeActivity::class.java))
                R.id.Notification -> startActivity(Intent(this, NotificationActivity::class.java))
                R.id.Profile -> recreate()
                R.id.Report -> startActivity(Intent(this, MonthlyReportActivity::class.java))
                else -> false
            }
            true
        }
    }
}