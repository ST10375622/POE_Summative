package com.fake.poe_summative

import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import android.view.LayoutInflater



//makes use of recyclerview to display a list of expenses
//Code Attribution
//RecyclerView.Adapter
//Android Developers(2024)
class AllExpenseAdapter (
    private val onReceiptClick: (String) -> Unit
) : RecyclerView.Adapter<AllExpenseAdapter.ExpenseViewHolder>() {

    private var expenses: List<Expense> = emptyList()

    //updates the adapters dataset with the new list of expenses and also refreshes the User Interface
    fun submitList(list: List<Expense>) {
        expenses = list
        notifyDataSetChanged()
    }

    //inflates the layout - item_expense.xml for each list item
    //returns a new view holder
    //Code Attribution
    //LayoutInflater
    //Android Developers(2024)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExpenseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_expense, parent, false)
        return ExpenseViewHolder(view)
    }

    //binds data to each item in the recyclerView
    //Code Attribution
    //RecyclerView.Adapter
    //Android Developers(2024)
    override fun onBindViewHolder(holder: ExpenseViewHolder, position: Int) {
        holder.bind(expenses[position])
    }

    override fun getItemCount() = expenses.size

    //viewholder to hold the UI references for a single item view
    //Code Attribution
    //ViewHolder pattern
    //Android Developers(2024)
    inner class ExpenseViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private val name = itemView.findViewById<TextView>(R.id.textExpenseName)
        private val amount = itemView.findViewById<TextView>(R.id.textExpenseAmount)
        private val date = itemView.findViewById<TextView>(R.id.textExpenseDate)
        private val receipt = itemView.findViewById<ImageView>(R.id.imageReceipt)

        fun bind(expense: Expense) {
            name.text = expense.name
            amount.text = "R ${expense.amount}"
            date.text = expense.date

            //if the specific expense has an image glide is used to load the image into ImageView
            //Code Attribution
            //Glide
            //Bumptech (2024)
            if (!expense.receiptImage.isNullOrBlank()) {
                receipt.visibility = View.VISIBLE

                if (expense.receiptImage.startsWith("http")) {
                    // Firebase Storage URL
                    Glide.with(itemView.context)
                        .load(expense.receiptImage)
                        .into(receipt)
                } else {
                    // Assume it's Base64
                    try {
                        val decodedBytes = Base64.decode(expense.receiptImage, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        receipt.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        receipt.visibility = View.GONE
                    }
                }

                receipt.setOnClickListener {
                    onReceiptClick(expense.receiptImage)
                }
            } else {
                receipt.visibility = View.GONE
            }

        }
    }
}