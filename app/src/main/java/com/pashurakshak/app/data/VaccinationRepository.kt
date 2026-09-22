package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.Vaccination
import com.pashurakshak.app.data.local.long
import com.pashurakshak.app.data.local.string
import tech.turso.libsql.Row

class VaccinationRepository(private val db: TursoClient) {

    suspend fun insert(vaccination: Vaccination) {
        db.execute(
            "INSERT INTO vaccinations (id, animal_id, vaccine_name, date_given, next_due) " +
                "VALUES (?, ?, ?, ?, ?)",
            vaccination.id,
            vaccination.animalId,
            vaccination.vaccineName,
            vaccination.dateGiven,
            vaccination.nextDue,
        )
    }

    suspend fun getAll(): List<Vaccination> =
        db.query("SELECT * FROM vaccinations ORDER BY date_given DESC") { it.toVaccination() }

    suspend fun getById(id: String): Vaccination? =
        db.query("SELECT * FROM vaccinations WHERE id = ?", id) { it.toVaccination() }.firstOrNull()

    suspend fun getByAnimal(animalId: String): List<Vaccination> =
        db.query(
            "SELECT * FROM vaccinations WHERE animal_id = ? ORDER BY date_given DESC",
            animalId,
        ) { it.toVaccination() }

    suspend fun update(vaccination: Vaccination) {
        db.execute(
            "UPDATE vaccinations SET animal_id = ?, vaccine_name = ?, date_given = ?, next_due = ? " +
                "WHERE id = ?",
            vaccination.animalId,
            vaccination.vaccineName,
            vaccination.dateGiven,
            vaccination.nextDue,
            vaccination.id,
        )
    }

    suspend fun delete(id: String) {
        db.execute("DELETE FROM vaccinations WHERE id = ?", id)
    }

    // Column order must match the vaccinations CREATE TABLE order (SELECT *).
    private fun Row.toVaccination() = Vaccination(
        id = string(0),
        animalId = string(1),
        vaccineName = string(2),
        dateGiven = long(3),
        nextDue = long(4),
    )
}
