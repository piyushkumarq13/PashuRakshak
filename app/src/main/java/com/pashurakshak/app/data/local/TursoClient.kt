package com.pashurakshak.app.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tech.turso.libsql.Connection
import tech.turso.libsql.Database
import tech.turso.libsql.Libsql
import tech.turso.libsql.Row
import java.io.File

/**
 * Offline-first wrapper around an embedded libSQL database file.
 * Opens lazily on first query; applies [Migrations] tracked via PRAGMA user_version.
 * Remote Turso sync will be added in a later phase.
 */
class TursoClient(context: Context) {

    private val databasePath: String =
        context.applicationContext.getDatabasePath(DATABASE_NAME).absolutePath

    private val mutex = Mutex()
    private var database: Database? = null

    suspend fun execute(sql: String, vararg args: Any?) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                withConnection { it.execute(sql, *args) }
            }
        }
    }

    suspend fun <T> query(sql: String, vararg args: Any?, mapper: (Row) -> T): List<T> {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                withConnection { connection ->
                    connection.query(sql, *args).use { rows ->
                        rows.asSequence().map(mapper).toList()
                    }
                }
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        val connection = ensureDatabase().connect()
        return connection.use {
            it.execute("PRAGMA foreign_keys = ON")
            block(it)
        }
    }

    private fun ensureDatabase(): Database {
        database?.let { return it }
        File(databasePath).parentFile?.mkdirs()
        val db = Libsql.open(databasePath)
        try {
            db.connect().use(::migrate)
        } catch (t: Throwable) {
            db.close()
            throw t
        }
        database = db
        return db
    }

    private fun migrate(connection: Connection) {
        val current = connection.query("PRAGMA user_version").use { rows ->
            rows.firstOrNull()?.let { (it[0] as Number).toLong() } ?: 0L
        }
        Migrations.all
            .filter { it.version > current }
            .sortedBy { it.version }
            .forEach { migration ->
                connection.executeBatch(migration.sql)
                connection.execute("PRAGMA user_version = ${migration.version}")
            }
    }

    private companion object {
        const val DATABASE_NAME = "pashurakshak.db"
    }
}
