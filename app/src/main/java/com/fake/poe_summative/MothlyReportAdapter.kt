package com.fake.poe_summative

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MonthlyReportAdapter : RecyclerView.Adapter<MonthlyReportAdapter.ReportViewHolder>() {

    private var expenses: List<Expense> = emptyList()

    fun submitList(list: List<Expense>) {
        expenses = list
        notifyDataSetChanged()
    }

    /*Inflates the layout for the monthly expense
   * Code Attribution
   * RecyclerView.Adapter
   * Android Developer (2024)*/
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_monthly_expense, parent, false)
        return ReportViewHolder(view)
    }

    /*Observes expenses
   * Code Attribution
   * LiveData.observeForever()
   * Android Developer (2024)*/
    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount(): Int = expenses.size

    /*this is an inner class
    * holds references to the Expense item user interface
    * Code Attribution
    * ViewHolder Pattern
    * Android Developer (2024)*/
    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textName: TextView = itemView.findViewById(R.id.textExpensesName)
        private val textAmount: TextView = itemView.findViewById(R.id.textExpensesAmount)
        private val textDate: TextView = itemView.findViewById(R.id.textExpensesDate)

        fun bind(expense: Expense) {
            textName.text = expense.name
            textAmount.text = "R ${expense.amount}"
            textDate.text = expense.date
        }
    }
}