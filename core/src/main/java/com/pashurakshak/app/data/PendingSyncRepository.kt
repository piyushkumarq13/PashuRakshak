package com.pashurakshak.app.data

import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.local.int
import com.pashurakshak.app.data.local.string
import tech.turso.libsql.Row

/**
 * Durable queue of locally changed rows that still need to reach the backend.
 *
 * Reports keep their existing `synced` flag. Animals, vaccinations and alerts
 * use this queue so a periodic sync no longer re-uploads every local row.
 * The primary key coalesces repeated edits into one pending operation.
 */
class PendingSyncRepository(private val db: TursoClient) {

    enum class EntityType(val value: String) {
        ANIMAL("animal"),
        VACCINATION("vaccination"),
        ALERT("alert");

        companion object {
            fun fromValue(value: String): EntityType? = entries.firstOrNull { it.value == value }
        }
    }

    enum class Operation(val value: String) {
        UPSERT("upsert"),
        DELETE("delete");
    }

    data class Item(
        val entityType: EntityType,
        val entityId: String,
        val operation: Operation,
    )

    suspend fun enqueueUpsert(entityType: EntityType, entityId: String) {
        enqueue(entityType, entityId, Operation.UPSERT)
    }

    suspend fun enqueueDelete(entityType: EntityType, entityId: String) {
        enqueue(entityType, entityId, Operation.DELETE)
    }

    private suspend fun enqueue(entityType: EntityType, entityId: String, operation: Operation) {
        db.execute(
            """
            INSERT INTO pending_sync (entity_type, entity_id, operation, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(entity_type, entity_id) DO UPDATE SET
                operation = excluded.operation,
                updated_at = excluded.updated_at
            """.trimIndent(),
            entityType.value,
            entityId,
            operation.value,
            System.currentTimeMillis(),
        )
    }

    suspend fun remove(entityType: EntityType, entityId: String) {
        db.execute(
            "DELETE FROM pending_sync WHERE entity_type = ? AND entity_id = ?",
            entityType.value,
            entityId,
        )
    }

    suspend fun get(entityType: EntityType): List<Item> =
        db.query(
            """
            SELECT entity_type, entity_id, operation
            FROM pending_sync
            WHERE entity_type = ?
            ORDER BY updated_at ASC
            """.trimIndent(),
            entityType.value,
        ) { it.toItem() }

    suspend fun isDeletePending(entityType: EntityType, entityId: String): Boolean =
        db.query(
            """
            SELECT 1 FROM pending_sync
            WHERE entity_type = ? AND entity_id = ? AND operation = ?
            LIMIT 1
            """.trimIndent(),
            entityType.value,
            entityId,
            Operation.DELETE.value,
        ) { it }.isNotEmpty()

    suspend fun count(): Int =
        db.query("SELECT COUNT(*) FROM pending_sync") { it.int(0) }.firstOrNull() ?: 0

    private fun Row.toItem(): Item {
        val type = EntityType.fromValue(string(0)) ?: error("Unknown pending entity type")
        return Item(
            entityType = type,
            entityId = string(1),
            operation = Operation.entries.firstOrNull { it.value == string(2) }
                ?: error("Unknown pending operation"),
        )
    }
}
