package com.fake.poe_summative

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ListAdapter
import com.google.firebase.firestore.FirebaseFirestore

interface ExpenseActionHandler {
    fun onAddExpenseClick(categoryId: String)
}

//RecycleView.Adapter for displaying expense categories and their respective expenses
//makes use of recyclerview to display a list of expenses
//Code Attribution
//RecyclerView.Adapter
//Android Developers(2024)
class CategoryAdapter(
    private val context: Context,
    private val expenseHandler: ExpenseActionHandler,
    private val userId: String
) : ListAdapter<Category, CategoryAdapter.CategoryViewHolder>(CategoryDiffCallback()) {

    /*this is an inner class
   * holds references to the category item user interface
   * Code Attribution
   * ViewHolder Pattern
   * Android Developer (2024)*/
    inner class CategoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val categoryName: TextView = itemView.findViewById(R.id.txtCategoryName)
        val btnAddExpense: ImageButton = itemView.findViewById(R.id.btnAddExpense)
        val recyclerExpenses: RecyclerView = itemView.findViewById(R.id.recyclerExpenses)
    }

    /*Inflates the layout for each category item
    * Code Attribution
    * RecyclerView.Adapter
    * Android Developer (2024)*/
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category, parent, false)
        return CategoryViewHolder(view)
    }

    /*Observes expenses by category and updates the list dynamically
   * Code Attribution
   * LiveData.observeForever()
   * Android Developer (2024)*/
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = getItem(position)
        holder.categoryName.text = category.name

        holder.btnAddExpense.setOnClickListener {
            expenseHandler.onAddExpenseClick(category.id)
        }

        //setup nested RecyclerView for expenses
        val expenseAdapter = ExpenseAdapter()
        holder.recyclerExpenses.layoutManager = LinearLayoutManager(context)
        holder.recyclerExpenses.adapter = expenseAdapter

        //fetch and observe exenses from firbase
        FirebaseFirestore.getInstance()
            .collection("user").document(userId)
            .collection("categories").document(category.id)
            .collection("expenses")
            .addSnapshotListener { snapshot, _ ->
                val expenses = snapshot?.documents?.mapNotNull {
                    it.toObject(Expense::class.java)
                } ?: emptyList()

                expenseAdapter.submitList(expenses)
            }
    }
}

/*Code Attribution
   * DiffUtil.ItemCallback
   * Android Developer (2024)*/
class CategoryDiffCallback : DiffUtil.ItemCallback<Category>() {
    override fun areItemsTheSame(oldItem: Category, newItem: Category): Boolean =
        oldItem.id == newItem.id

    override fun areContentsTheSame(oldItem: Category, newItem: Category): Boolean =
        oldItem == newItem
}