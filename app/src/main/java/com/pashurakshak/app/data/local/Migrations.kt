package com.pashurakshak.app.data.local

object Migrations {

    data class Migration(
        val version: Int,
        val sql: String,
    )

    val all: List<Migration> = listOf(
        Migration(version = 1, sql = INITIAL_SCHEMA),
        Migration(version = 2, sql = FARMERS_TABLE),
    )

    private const val FARMERS_TABLE = """
        CREATE TABLE farmers (
            id TEXT PRIMARY KEY NOT NULL,
            phone TEXT NOT NULL,
            created_at INTEGER NOT NULL
        );
    """

    private const val INITIAL_SCHEMA = """
        CREATE TABLE animals (
            id TEXT PRIMARY KEY NOT NULL,
            owner_farmer_id TEXT NOT NULL,
            species TEXT NOT NULL,
            name TEXT NOT NULL,
            qr_code_id TEXT NOT NULL UNIQUE,
            created_at INTEGER NOT NULL
        );
        CREATE TABLE vaccinations (
            id TEXT PRIMARY KEY NOT NULL,
            animal_id TEXT NOT NULL REFERENCES animals(id),
            vaccine_name TEXT NOT NULL,
            date_given INTEGER NOT NULL,
            next_due INTEGER NOT NULL
        );
        CREATE TABLE symptom_reports (
            id TEXT PRIMARY KEY NOT NULL,
            animal_id TEXT NOT NULL REFERENCES animals(id),
            farmer_id TEXT NOT NULL,
            symptoms TEXT NOT NULL,
            photo_local_path TEXT NOT NULL,
            photo_remote_url TEXT,
            latitude REAL NOT NULL,
            longitude REAL NOT NULL,
            risk_score INTEGER NOT NULL,
            risk_breakdown TEXT NOT NULL,
            status TEXT NOT NULL CHECK (status IN ('reported', 'vet_assigned', 'examined', 'sample_sent', 'confirmed', 'resolved')),
            synced INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );
        CREATE TABLE vets (
            id TEXT PRIMARY KEY NOT NULL,
            name TEXT NOT NULL,
            phone TEXT NOT NULL,
            assigned_village TEXT NOT NULL
        );
        CREATE TABLE alerts (
            id TEXT PRIMARY KEY NOT NULL,
            recipient_role TEXT NOT NULL,
            recipient_id TEXT NOT NULL,
            message TEXT NOT NULL,
            read INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL
        );
    """
}
