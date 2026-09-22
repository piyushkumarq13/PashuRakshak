package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.Alert
import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.boolean
import com.pashurakshak.app.data.local.long
import com.pashurakshak.app.data.local.string
import tech.turso.libsql.Row

class AlertRepository(private val db: TursoClient) {

    suspend fun insert(alert: Alert) {
        db.execute(
            "INSERT INTO alerts (id, recipient_role, recipient_id, message, read, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            alert.id,
            alert.recipientRole,
            alert.recipientId,
            alert.message,
            if (alert.read) 1 else 0,
            alert.createdAt,
        )
    }

    suspend fun getAll(): List<Alert> =
        db.query("SELECT * FROM alerts ORDER BY created_at DESC") { it.toAlert() }

    suspend fun getById(id: String): Alert? =
        db.query("SELECT * FROM alerts WHERE id = ?", id) { it.toAlert() }.firstOrNull()

    suspend fun getForRecipient(recipientRole: String, recipientId: String): List<Alert> =
        db.query(
            "SELECT * FROM alerts WHERE recipient_role = ? AND recipient_id = ? ORDER BY created_at DESC",
            recipientRole,
            recipientId,
        ) { it.toAlert() }

    /** All alerts for a role (e.g. every vet sees high-risk broadcasts). */
    suspend fun getForRole(recipientRole: String): List<Alert> =
        db.query(
            "SELECT * FROM alerts WHERE recipient_role = ? ORDER BY created_at DESC",
            recipientRole,
        ) { it.toAlert() }

    suspend fun update(alert: Alert) {
        db.execute(
            "UPDATE alerts SET recipient_role = ?, recipient_id = ?, message = ?, read = ?, " +
                "created_at = ? WHERE id = ?",
            alert.recipientRole,
            alert.recipientId,
            alert.message,
            if (alert.read) 1 else 0,
            alert.createdAt,
            alert.id,
        )
    }

    suspend fun markRead(id: String) {
        db.execute("UPDATE alerts SET read = 1 WHERE id = ?", id)
    }

    suspend fun delete(id: String) {
        db.execute("DELETE FROM alerts WHERE id = ?", id)
    }

    // Column order must match the alerts CREATE TABLE order (SELECT *).
    private fun Row.toAlert() = Alert(
        id = string(0),
        recipientRole = string(1),
        recipientId = string(2),
        message = string(3),
        read = boolean(4),
        createdAt = long(5),
    )
}
