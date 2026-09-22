package com.pashurakshak.app.data.local

import java.util.UUID

data class Vaccination(
    val id: String = UUID.randomUUID().toString(),
    val animalId: String,
    val vaccineName: String,
    val dateGiven: Long,
    val nextDue: Long,
)
