package com.pashurakshak.app.data.local

import java.util.UUID

data class Animal(
    val id: String = UUID.randomUUID().toString(),
    val ownerFarmerId: String,
    val species: String,
    val name: String,
    val qrCodeId: String,
    val createdAt: Long = System.currentTimeMillis(),
)
