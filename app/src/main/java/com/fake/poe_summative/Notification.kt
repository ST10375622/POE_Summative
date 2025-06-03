package com.fake.poe_summative

import com.google.firebase.Timestamp

data class Notification (

    val message: String = "",
    val timestamp: Timestamp? = null,
    val read: Boolean = false
)