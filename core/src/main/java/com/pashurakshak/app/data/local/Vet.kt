package com.pashurakshak.app.data.local

import java.util.UUID

data class Vet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val assignedVillage: String,
)
