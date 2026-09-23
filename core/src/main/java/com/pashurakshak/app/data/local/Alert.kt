package com.pashurakshak.app.data.local

import java.util.UUID

data class Alert(
    val id: String = UUID.randomUUID().toString(),
    val recipientRole: String,
    val recipientId: String,
    val message: String,
    val read: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
