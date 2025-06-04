package com.fake.poe_summative

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.LegendEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import com.google.firebase.Timestamp
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.Date
import java.util.Locale

class BudgetActivity : AppCompatActivity(), ExpenseActionHandler {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var imagePickerLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var currentUserId: String

    private var selectedBitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget)

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
        val imageProfile = findViewById<ImageView>(R.id.imageProfile)
        val textName = findViewById<TextView>(R.id.textProfileName)
        val textBudget = findViewById<TextView>(R.id.textMonthlyBudget)
        val textLeft = findViewById<TextView>(R.id.textMoneyLeft)
        val btnSetBudget = findViewById<Button>(R.id.btSetBudget)
        val pieChart = findViewById<PieChart>(R.id.pieChartDaily)
        val addCategory = findViewById<FloatingActionButton>(R.id.AddCategory)
        val recyclerView = findViewById<RecyclerView>(R.id.recyclerCategories)

        setSupportActionBar(toolbar)

        //directs the user to the specific screen
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        categoryAdapter = CategoryAdapter(this, this, currentUserId)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = categoryAdapter as RecyclerView.Adapter<*>

        cameraLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val bitmap = result.data?.extras?.get("data") as? Bitmap
                if (bitmap != null) {
                    selectedBitmap = bitmap
                    Toast.makeText(this, "Image captured", Toast.LENGTH_SHORT).show()
                }
            }
        }

        imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    selectedBitmap = bitmap
                    Toast.makeText(this, "Image selected", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
                    selectedBitmap = null
                }
            }
        }

        fetchUserName(textName)
        fetchUserBudget(textBudget, textLeft)
        fetchCategoriesAndExpenses(pieChart)

        btnSetBudget.setOnClickListener { showBudgetDialog() }
        addCategory.setOnClickListener { showAddCategoryDialog() }

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

                                            Log.d("NotificationDebug", "TotalExpenses: $totalExpenses, MinBudget: ${budget.minimumBudget}")

                                            if (totalExpenses > budget.minimumBudget) {
                                                Log.d("NotificationDebug", "Condition met — total > minimum. Adding notification.")
                                                checkAndAddBudgetExceededNotification()
                                            }
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


    private fun fetchCategoriesAndExpenses(pieChart: PieChart) {
        db.collection("user").document(currentUserId)
            .collection("categories")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val categories = snapshot.documents.mapNotNull {
                        it.toObject(Category::class.java)?.copy(id = it.id)
                    }

                    categoryAdapter.submitList(categories)
                    val categoryMap = categories.associateBy({ it.id }, { it.name })

                    val allExpenses = mutableListOf<Expense>()
                    var completedFetches = 0

                    if (categories.isEmpty()) {
                        updatePieChart(pieChart, emptyList(), categoryMap)
                        return@addSnapshotListener
                    }

                    categories.forEach { category ->
                        db.collection("user").document(currentUserId)
                            .collection("categories").document(category.id)
                            .collection("expenses")
                            .get()
                            .addOnSuccessListener { expenseSnap ->
                                allExpenses += expenseSnap.documents.mapNotNull {
                                    it.toObject(Expense::class.java)
                                }
                            }
                            .addOnCompleteListener {
                                completedFetches++
                                if (completedFetches == categories.size){
                                    updatePieChart(pieChart, allExpenses, categoryMap)
                                }
                            }
                    }
                }
            }
    }

    private fun showBudgetDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_set_budget, null)
        val monthlyBudgetInput = dialogView.findViewById<EditText>(R.id.editMonthlyBudget)
        val minimumBudgetInput = dialogView.findViewById<EditText>(R.id.editMinimumBudget)

        AlertDialog.Builder(this)
            .setTitle("Set Budget")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val monthly = monthlyBudgetInput.text.toString().toDoubleOrNull()
                val minimum = minimumBudgetInput.text.toString().toDoubleOrNull()

                if (monthly != null && minimum != null) {
                    db.collection("user").document(currentUserId)
                        .collection("categories")
                        .get()
                        .addOnSuccessListener { categorySnapshot ->
                            val categoryIds = categorySnapshot.documents.map { it.id }

                            var totalExpenses = 0.0
                            var fetchedCount = 0

                            if (categoryIds.isEmpty()) {
                                saveBudget(monthly, minimum, totalExpenses)
                                return@addOnSuccessListener
                            }

                            categoryIds.forEach { categoryId ->
                                db.collection("user").document(currentUserId)
                                    .collection("categories").document(categoryId)
                                    .collection("expenses")
                                    .get()
                                    .addOnSuccessListener { expenseSnap ->
                                        val sum = expenseSnap.documents.mapNotNull {
                                            it.toObject(Expense::class.java)?.amount
                                        }.sum()
                                        totalExpenses += sum
                                    }
                                    .addOnCompleteListener {
                                        fetchedCount++
                                        if (fetchedCount == categoryIds.size) {
                                            saveBudget(monthly, minimum, totalExpenses)
                                        }
                                    }
                            }
                        }
                } else {
                    Toast.makeText(this, "Invalid Input", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("cancel", null)
            .show()
    }

    private fun saveBudget(monthly: Double, minimum: Double, totalExpenses: Double) {
        val budget = Budget(
            id = "main",
            userId = currentUserId,
            monthlyBudget = monthly,
            amountLeft = monthly - totalExpenses,
            minimumBudget = minimum
        )

        db.collection("user").document(currentUserId)
            .collection("Budget").document("main")
            .set(budget)
            .addOnSuccessListener {
                Toast.makeText(this, "Budget saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save budget", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddCategoryDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("New Category")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString()
                if (name.isNotBlank()) {
                    val id = db.collection("user").document(currentUserId)
                        .collection("categories").document().id
                    val category = Category(id = id, userId = currentUserId, name = name)
                    db.collection("user").document(currentUserId)
                        .collection("categories").document(id)
                        .set(category)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddExpenseDialog(categoryId: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_expense, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editExpenseName)
        val descriptionInput = dialogView.findViewById<EditText>(R.id.editExpenseDesc)
        val amountInput = dialogView.findViewById<EditText>(R.id.editExpenseAmount)
        val dateText = dialogView.findViewById<TextView>(R.id.textExpenseDate)
        val uploadButton = dialogView.findViewById<Button>(R.id.btnUploadReciept)

        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateText.text = dateFormat.format(calendar.time)

        dateText.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                calendar.set(y, m, d)
                dateText.text = dateFormat.format(calendar.time)
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        uploadButton.setOnClickListener {
            val options = arrayOf("Take Photo", "Choose from Gallery")
            AlertDialog.Builder(this)
                .setTitle("Upload Receipt")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> openCamera()
                        1 -> pickFromGallery()
                    }
                }
                .show()
        }


        val dialog = AlertDialog.Builder(this)
            .setTitle("Add Expense")
            .setView(dialogView)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = nameInput.text.toString().trim()
                val desc = descriptionInput.text.toString().trim()
                val amount = amountInput.text.toString().toDoubleOrNull()
                val date = dateText.text.toString()

                if (name.isEmpty() || amount == null) {
                    Toast.makeText(this, "Please enter valid name and amount", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val receiptBase64 = selectedBitmap?.let { encodeImageToBase64(it) }

                val expenseId = db.collection("user").document(currentUserId)
                    .collection("categories").document(categoryId)
                    .collection("expenses").document().id

                val expense = Expense(
                    id = expenseId,
                    categoryId = categoryId,
                    userId = currentUserId,
                    name = name,
                    description = desc,
                    amount = amount,
                    date = date,
                    receiptImage = receiptBase64
                )

                db.collection("user").document(currentUserId)
                    .collection("categories").document(categoryId)
                    .collection("expenses").document(expenseId)
                    .set(expense)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Expense added", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed to save expense", Toast.LENGTH_SHORT).show()
                    }
            }
        }

        dialog.show()
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        cameraLauncher.launch(intent)
    }

    private fun pickFromGallery() {
        imagePickerLauncher.launch("image/*")
    }


    private fun encodeImageToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.DEFAULT)
    }

    private fun decodeBase64ToBitmap(base64Str: String): Bitmap {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    }

    private fun updatePieChart(pieChart: PieChart, expenses: List<Expense>, categoryMap: Map<String, String>) {
        val grouped = expenses.groupBy { it.categoryId }
        val entries = grouped.map { PieEntry(it.value.sumOf { e -> e.amount }.toFloat()) }
        val labels = grouped.map { categoryMap[it.key] ?: "Unknown" }

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
            isEnabled = true
            form = Legend.LegendForm.CIRCLE
            textSize = 14f

            xEntrySpace = 30f
            yEntrySpace = 30f
            formToTextSpace = 20f

            setCustom(labels.mapIndexed { i, label ->
                LegendEntry(label, Legend.LegendForm.CIRCLE, 10f, 2f, null, dataSet.colors[i])
            })
        }
    }

    private fun checkAndAddBudgetExceededNotification() {
        val notification = Notification(
            message = "Alert! You've exceeded your minimum budget.",
            timestamp = Timestamp.now(),
            read = false
        )

        val userDoc = db.collection("user").document(currentUserId)

        userDoc.collection("notifications")
            .add(notification)
            .addOnSuccessListener {
                Log.d("NotificationTest", "Auto-notification added")
            }
            .addOnFailureListener {
                Log.e("NotificationTest", "Failed to add notification", it)
            }


    }

    private fun setupNavigation() {
        navView.setNavigationItemSelectedListener { menuItem ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (menuItem.itemId) {
                R.id.home -> startActivity(Intent(this, HomeActivity::class.java))
                R.id.Progress -> startActivity(Intent(this, ProgressActivity::class.java))
                R.id.Report -> startActivity(Intent(this, MonthlyReportActivity::class.java))
                R.id.Notification -> startActivity(Intent(this, NotificationActivity::class.java))
                R.id.Profile -> startActivity(Intent(this, ProfileActivity::class.java))
                R.id.Budget -> recreate()
                else -> false
            }
            true
        }
    }

    override fun onAddExpenseClick(categoryId: String) {
        showAddExpenseDialog(categoryId)
    }
}