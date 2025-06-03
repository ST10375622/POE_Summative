package com.fake.poe_summative

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.view.LayoutInflater

class AllExpenseAdapter (
    private val onReceiptClick: (String) -> Unit
) : RecyclerView.Adapter<AllExpenseAdapter.ExpenseViewHolder>() {

    private var expenses: List<Expense> = emptyList()

    fun submitList(list: List<Expense>) {
        expenses = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount() = expenses.size

    inner class ExpenseViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.textExpenseName)
        private val amount = itemView.findViewById<TextView>(R.id.textExpenseAmount)
        private val date = itemView.findViewById<TextView>(R.id.textExpenseDate)
        private val receipt = itemView.findViewById<ImageView>(R.id.imageReceipt)

        fun bind(expense: Expense) {
            name.text = expense.name
            amount.text = "R ${expense.amount}"
            date.text = expense.date

            if(!expense.receiptUri.isNullOrBlank()) {
                receipt.visibility = View.VISIBLE
                Glide.with(itemView.context)
                    .load(expense.receiptUri)
                    .into(receipt)

                receipt.setOnClickListener {
                    onReceiptClick(expense.receiptUri)
                }
            } else {
                receipt.visibility = View.GONE
            }
        }
    }
}