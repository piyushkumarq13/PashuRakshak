package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.Animal
import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.long
import com.pashurakshak.app.data.local.string
import tech.turso.libsql.Row

class AnimalRepository(private val db: TursoClient) {

    suspend fun insert(animal: Animal) {
        db.execute(
            "INSERT INTO animals (id, owner_farmer_id, species, name, qr_code_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            animal.id,
            animal.ownerFarmerId,
            animal.species,
            animal.name,
            animal.qrCodeId,
            animal.createdAt,
        )
    }

    suspend fun getAll(): List<Animal> =
        db.query("SELECT * FROM animals ORDER BY created_at DESC") { it.toAnimal() }

    suspend fun getById(id: String): Animal? =
        db.query("SELECT * FROM animals WHERE id = ?", id) { it.toAnimal() }.firstOrNull()

    suspend fun getByQrCode(qrCodeId: String): Animal? =
        db.query("SELECT * FROM animals WHERE qr_code_id = ?", qrCodeId) { it.toAnimal() }.firstOrNull()

    suspend fun update(animal: Animal) {
        db.execute(
            "UPDATE animals SET owner_farmer_id = ?, species = ?, name = ?, qr_code_id = ?, " +
                "created_at = ? WHERE id = ?",
            animal.ownerFarmerId,
            animal.species,
            animal.name,
            animal.qrCodeId,
            animal.createdAt,
            animal.id,
        )
    }

    suspend fun delete(id: String) {
        db.execute("DELETE FROM animals WHERE id = ?", id)
    }

    // Column order must match the animals CREATE TABLE order (SELECT *).
    private fun Row.toAnimal() = Animal(
        id = string(0),
        ownerFarmerId = string(1),
        species = string(2),
        name = string(3),
        qrCodeId = string(4),
        createdAt = long(5),
    )
}
