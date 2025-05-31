package com.fake.poe_summative
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ExpenseAdapter : ListAdapter<Expense, ExpenseAdapter.ExpenseViewHolder>(ExpenseDiffCallback()) {

    inner class ExpenseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val txtExpenseName: TextView = itemView.findViewById(R.id.txtExpenseName)
        val txtExpenseAmount: TextView = itemView.findViewById(R.id.txtExpenseAmount)
        val txtExpenseDate: TextView = itemView.findViewById(R.id.txtExpenseDate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        val expense = getItem(position)
        holder.txtExpenseName.text = expense.name
        holder.txtExpenseAmount.text = "Amount: R %.2f".format(expense.amount)
        holder.txtExpenseDate.text = "Date: ${expense.date}"
    }

    class ExpenseDiffCallback : DiffUtil.ItemCallback<Expense>() {
        override fun areItemsTheSame(oldItem: Expense, newItem: Expense): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Expense, newItem: Expense): Boolean = oldItem == newItem

    }
}