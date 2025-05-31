package com.fake.poe_summative

data class Expense (
    val id: String = "",
    val categoryId: String = "",
    val userId: String = "",
    val name: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val receiptUri: String? = null
)