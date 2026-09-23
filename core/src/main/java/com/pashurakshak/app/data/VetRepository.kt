package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.Vet
import com.pashurakshak.app.data.local.string
import tech.turso.libsql.Row

class VetRepository(private val db: TursoClient) {

    suspend fun insert(vet: Vet) {
        db.execute(
            "INSERT INTO vets (id, name, phone, assigned_village) VALUES (?, ?, ?, ?)",
            vet.id,
            vet.name,
            vet.phone,
            vet.assignedVillage,
        )
    }

    suspend fun getAll(): List<Vet> =
        db.query("SELECT * FROM vets ORDER BY name ASC") { it.toVet() }

    suspend fun getById(id: String): Vet? =
        db.query("SELECT * FROM vets WHERE id = ?", id) { it.toVet() }.firstOrNull()

    suspend fun update(vet: Vet) {
        db.execute(
            "UPDATE vets SET name = ?, phone = ?, assigned_village = ? WHERE id = ?",
            vet.name,
            vet.phone,
            vet.assignedVillage,
            vet.id,
        )
    }

    suspend fun delete(id: String) {
        db.execute("DELETE FROM vets WHERE id = ?", id)
    }

    // Column order must match the vets CREATE TABLE order (SELECT *).
    private fun Row.toVet() = Vet(
        id = string(0),
        name = string(1),
        phone = string(2),
        assignedVillage = string(3),
    )
}
