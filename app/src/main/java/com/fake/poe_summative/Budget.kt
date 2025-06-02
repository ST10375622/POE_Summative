package com.fake.poe_summative


data class Budget(
    val id: String = "",
    val userId: String = "",
    val monthlyBudget: Double = 0.0,
    val amountLeft: Double = 0.0,
    val minimumBudget: Double = 0.0
)