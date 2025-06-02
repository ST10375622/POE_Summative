package com.fake.poe_summative

import android.content.Intent
import android.os.Bundle
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference

class HomeActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle

    private lateinit var storage: FirebaseStorage
    private lateinit var storageRef: StorageReference
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)


        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        storageRef = storage.reference
        currentUserId = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        val textBudget = findViewById<TextView>(R.id.textMonthlyBudget)
        val textLeft = findViewById<TextView>(R.id.textMoneyLeft)
        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

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
                //R.id.Profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.home -> recreate()
                else -> false
            }
            true
        }
    }

}