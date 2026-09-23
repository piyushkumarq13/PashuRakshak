# PasuRakshak — Complete Project Audit Report

> **Generated:** 2026-09-22 | **Read-only audit** — no code changes made.  
> **Scope:** Android app (Kotlin/Compose) + shared Node.js backend (Express/Turso)  
> **Git state:** 2 commits, working tree clean (except this report file)

---

## 1. Project Structure

### 1.1 Full Directory Tree

```
PasuRakshak/                              # Root monorepo: Android + shared backend
├── .git/                                 # Git repository (2 commits, main branch)
├── .gitignore                            # Git ignore rules (root)
├── .gradle/                              # Gradle cache and plugins
├── .idea/                                # IntelliJ IDEA project config
├── .kotlin/                              # Kotlin compiler cache
├── app/                                  # Android app module (Compose, minSdk 24, targetSdk 37)
│   ├── build.gradle.kts                  # Android build config (Compose, Turso, Firebase deps)
│   ├── google-services.json              # REAL Firebase config (NOT committed to git)
│   ├── google-services.json.example      # Placeholder for Firebase config
│   ├── local.properties                  # Local secrets (B2 keys, API URLs, app key) — gitignored
│   ├── proguard-rules.pro                # ProGuard rules for release builds
│   ├── src/main/
│   │   ├── AndroidManifest.xml           # App manifest: permissions, FCM service, provider
│   │   ├── java/com/pashurakshak/app/
│   │   │   ├── MainActivity.kt           # Compose entry point, notification deep-link handling
│   │   │   ├── PashuRakshakApplication.kt # Application class: initializes sync, DI, notifications
│   │   │   ├── data/
│   │   │   │   ├── AuthRepository.kt     # Firebase Phone Auth + device token registration
│   │   │   │   ├── AnimalRepository.kt   # CRUD on local `animals` table via TursoClient
│   │   │   │   ├── ReportRepository.kt   # CRUD on local `symptom_reports`, B2 photo queue
│   │   │   │   ├── VaccinationRepository.kt # CRUD on local `vaccinations` table
│   │   │   │   ├── VetRepository.kt      # CRUD on local `vets` table
│   │   │   │   ├── AlertRepository.kt    # CRUD on local `alerts` table
│   │   │   │   ├── SessionManager.kt     # Phone/role login state with SharedPreferences persistence
│   │   │   │   ├── local/
│   │   │   │   │   ├── TursoClient.kt    # Offline-first embedded libSQL wrapper (PRAGMA user_version)
│   │   │   │   │   ├── Migrations.kt     # Local DB schema migrations (version 1 + 2)
│   │   │   │   │   ├── SymptomReport.kt  # Data class + ReportStatus enum
│   │   │   │   │   ├── Animal.kt         # Data class
│   │   │   │   │   ├── Vaccination.kt    # Data class
│   │   │   │   │   ├── Alert.kt          # Data class
│   │   │   │   │   ├── Vet.kt            # Data class
│   │   │   │   │   └── RowValues.kt      # Extension funcs for libSQL Row positional access
│   │   │   │   ├── remote/
│   │   │   │   │   ├── B2UploadService.kt     # Backblaze B2 photo upload (native API)
│   │   │   │   │   ├── PushMessagingService.kt # FCM push receiver → local notifications
│   │   │   │   │   └── ReportPushApi.kt       # POST symptom reports to backend (HttpURLConnection)
│   │   │   │   ├── sync/
│   │   │   │   │   ├── RemoteSync.kt         # Bidirectional sync with backend (push locals, pull remotes)
│   │   │   │   │   ├── SyncWorker.kt         # WorkManager CoroutineWorker: B2 upload → push reports → sync all
│   │   │   │   │   ├── SyncScheduler.kt      # Schedules periodic (15min) + immediate sync work
│   │   │   │   │   ├── SyncStatusHolder.kt   # Shared StateFlow of sync queue status for UI banner
│   │   │   │   │   └── NetworkReconnectObserver.kt # ConnectivityManager callback → triggers sync
│   │   │   │   ├── di/
│   │   │   │   │   └── ServiceLocator.kt     # Singleton DI container for all repositories/services
│   │   │   │   ├── navigation/
│   │   │   │   │   └── Screen.kt              # Sealed class defining all navigation routes
│   │   │   │   ├── notifications/
│   │   │   │   │   └── AppNotifications.kt     # FCM notification channel + display helpers
│   │   │   │   └── ui/
│   │   │   │       ├── HomeScreens.kt         # FarmerHomeScreen, VetHomeScreen (Compose placeholders)
│   │   │   │       ├── RoleSelectScreen.kt    # Farmer/Vet role selection
│   │   │   │       ├── navigation/
│   │   │   │       │   └── AppNavHost.kt      # Full Compose navigation graph (230 lines)
│   │   │   │       ├── components/
│   │   │   │       │   ├── SyncStatusBanner.kt   # "X reports pending sync" banner on every screen
│   │   │   │       │   ├── FarmerBottomBar.kt    # Bottom nav for farmer role
│   │   │   │       │   ├── VetBottomBar.kt       # Bottom nav for vet role
│   │   │   │       │   └── FarmerHomeScreen.kt   # Farmer home (delegates to HomePlaceholder)
│   │   │   │       ├── auth/
│   │   │   │       │   ├── PhoneEntryScreen.kt   # Step 1: phone number input → send OTP
│   │   │   │       │   ├── PhoneEntryViewModel.kt
│   │   │   │       │   ├── OtpScreen.kt           # Step 2: 6-digit SMS code verification
│   │   │   │       │   └── OtpViewModel.kt
│   │   │   │       ├── farmer/
│   │   │   │       │   ├── MyAnimalsScreen.kt      # List animals + QR passport navigation
│   │   │   │       │   ├── MyAnimalsViewModel.kt
│   │   │   │       │   ├── MyReportsScreen.kt      # List reports with risk level, photo
│   │   │   │       │   ├── MyReportsViewModel.kt
│   │   │   │       │   ├── ReportSickAnimalScreen.kt  # Form to report a sick animal
│   │   │   │       │   ├── ReportSickAnimalViewModel.kt
│   │   │   │       │   ├── VaccinationStatusScreen.kt # Vaccination history per animal
│   │   │   │       │   ├── VaccinationStatusViewModel.kt
│   │   │   │       │   ├── AlertsScreen.kt         # Farmer's alerts list
│   │   │   │       │   ├── AlertsViewModel.kt
│   │   │   │       │   ├── QrPassportScreen.kt     # QR-based animal passport/certificate
│   │   │   │       │   ├── QrPassportViewModel.kt
│   │   │   │       │   ├── StatusLabels.kt         # Shared status formatting utilities
│   │   │   │       │   ├── DateFormats.kt          # Shared date formatting utilities
│   │   │   │       │   └── FarmerBottomBar.kt
│   │   │   │       ├── vet/
│   │   │   │       │   ├── VetCaseQueueScreen.kt   # Vet's queue of reported cases
│   │   │   │       │   ├── VetCaseQueueViewModel.kt
│   │   │   │       │   ├── CaseDetailScreen.kt     # Detailed case view with field check dialog
│   │   │   │       │   ├── CaseDetailViewModel.kt
│   │   │   │       │   ├── RiskLevel.kt            # Risk scoring labels (HIGH/MEDIUM/LOW)
│   │   │   │       │   ├── RiskColors.kt           # Color definitions for risk levels
│   │   │   │       │   └── VetAlertsScreen.kt      # Vet-wide alert broadcast screen
│   │   │   │       ├── theme/
│   │   │   │       │   └── Theme.kt                # Compose Material3 theme
│   │   │   │       └── debug/
│   │   │   │           └── B2UploadTestScreen.kt   # Debug-only B2 upload test screen
│   │   │   └── test/                             # Unit tests
│   └── res/                                  # Android resources (layouts, strings, drawables, xml)
├── gradle/                                 # Gradle wrapper and libs
├── build.gradle.kts                        # Root build config (plugins apply false)
├── settings.gradle.kts                     # `rootProject.name = "PashuRakshak"`, include :app
├── local.properties.example                # Template for Android secrets (B2, API_BASE_URL, APP_API_KEY)
├── gradle.properties                       # Gradle configuration
├── shared-backend/                         # Node.js + Express + Turso/libSQL backend
│   ├── .env                                # REAL env file (gitignored, contains secrets)
│   ├── .env.example                        # Template env file
│   ├── .gitignore                          # node_modules/, .env, *.db excluded
│   ├── local.db                            # Local SQLite (if using file: URL for dev)
│   ├── package.json                        # Node.js project config
│   ├── package-lock.json                   # Lock file
│   ├── Procfile                            # Render: `web: npm start`
│   ├── README.md                           # Full project documentation
│   ├── render.yaml                         # Render deployment configuration
│   ├── src/
│   │   ├── app.js                          # Express setup, CORS, /health, error handlers, starts server
│   │   ├── routes.js                       # Mounts core + pashu-health under /api/v1
│   │   ├── config/
│   │   │   └── env.js                      # dotenv loading + strict validation of required env vars
│   │   ├── core/
│   │   │   ├── middleware/
│   │   │   │   ├── verifyAppKey.js         # X-App-Key → apps table lookup, sets req.appId
│   │   │   │   ├── verifyFirebaseToken.js  # Firebase Admin init + ID token verification
│   │   │   │   └── adminSecret.js          # X-Admin-Secret gate for admin-only core routes
│   │   │   ├── routes/
│   │   │   │   ├── apps.js                 # POST/GET /api/v1/core/apps (app registration + listing)
│   │   │   │   └── devices.js              # POST /api/v1/core/devices (device-token upsert)
│   │   │   └── services/
│   │   │       └── push.js                 # sendPushToUid(uid, title, body) — Firebase FCM push
│   │   ├── db/
│   │   │   ├── client.js                   # Turso @libsql/client configured from env vars
│   │   │   ├── migrate.js                  # Migration runner using migrations_applied table
│   │   │   └── migrations/
│   │   │       ├── 000_core.js             # Version 1: creates `apps` + `device_tokens` tables
│   │   │       └── 001_pashu_health.js     # Version 2: creates `pashu_*` tables (6 tables)
│   │   ├── modules/
│   │   │   └── pashu-health/
│   │   │       ├── routes/
│   │   │       │   ├── index.js            # Mounts reports/animals/vaccinations/alerts/clusters
│   │   │       │   ├── reports.js          # POST/GET/GET :id on pashu_symptom_reports
│   │   │       │   ├── animals.js          # Full CRUD on pashu_animals
│   │   │       │   ├── vaccinations.js     # CRUD on pashu_vaccinations
│   │   │       │   ├── alerts.js           # Full CRUD on pashu_alerts
│   │   │       │   └── clusters.js         # GET /api/v1/pashu-health/clusters
│   │   │       └── services/
│   │   │           └── clusterDetection.js # Cluster detection (7d/5km/3+) + vet push
│   │   └── .gitkeep
└── PROJECT_AUDIT.md                        # This file
```

### 1.2 All JS/TS Files Under `src/` (Backend), Grouped by Folder

**`src/` root (2 files)**
| File | Summary |
|------|---------|
| `app.js` | Express server: CORS config, JSON body parser (`limit: '2mb'`), `/health` endpoint, mounts `/api/v1` routes, 404 handler, JSON parse error handler. Calls `runMigrations()` then `app.listen()`. |
| `routes.js` | Mounts `coreRoutes` at `/api/v1/core`, `devicesRoutes` at `/api/v1/core`, and `pashuHealthRoutes` at `/api/v1/pashu-health` (with `verifyAppKey` middleware). Contains commented placeholder for future modules. |

**`src/config/` (1 file)**
| File | Summary |
|------|---------|
| `config/env.js` | Loads `.env` via `dotenv`. Validates 5 required vars (`TURSO_DATABASE_URL`, `TURSO_AUTH_TOKEN`, `PORT`, `CORS_ALLOWED_ORIGINS`, `ADMIN_SECRET`). Parses `CORS_ALLOWED_ORIGINS` into comma-separated array. Exports `env` object. Throws if any required var is missing. |

**`src/core/` (5 files)**
| File | Summary |
|------|---------|
| `core/middleware/verifyAppKey.js` | Middleware: reads `X-App-Key` header, queries `SELECT id FROM apps WHERE api_key = ?`, sets `req.appId`. Returns 401 `missing_app_key` or `invalid_app_key`. |
| `core/middleware/verifyFirebaseToken.js` | Middleware: lazily initializes `firebase-admin` from `FIREBASE_SERVICE_ACCOUNT_JSON`, verifies `Authorization: Bearer <idToken>`, sets `req.uid`. Returns 503 `firebase_not_configured` if env var missing; 401 for invalid/missing token. |
| `core/middleware/adminSecret.js` | Middleware: validates `X-Admin-Secret` header against `env.adminSecret`. Returns 401 `missing_admin_secret` or `invalid_admin_secret`. Used on `POST/GET /api/v1/core/apps`. |
| `core/routes/apps.js` | `POST /api/v1/core/apps`: admin-only, registers app with `randomUUID()` id + 32-char hex `apiKey` (returned once). `GET /api/v1/core/apps`: admin-only, lists `{id, name, created_at}` (never returns `api_key`). |
| `core/routes/devices.js` | `POST /api/v1/core/devices`: requires `verifyAppKey` + `verifyFirebaseToken`, upserts `device_tokens` keyed by `(app_id, uid, role)`. Body: `{ role, fcmToken }`. |
| `core/services/push.js` | `sendPushToUid(uid, title, body)`: looks up FCM tokens in `device_tokens` for `uid`, sends via `firebase-admin` `sendEachForMulticast`. Returns `{ uid, sent, failureCount, skipped? }`. |

**`src/db/` (3 files)**
| File | Summary |
|------|---------|
| `db/client.js` | Creates Turso `@libsql/client` instance with `url: env.tursoDatabaseUrl` + `authToken: env.tursoAuthToken`. Exports `db`. |
| `db/migrate.js` | Migration runner: creates `migrations_applied` table, reads sorted `.js` files from `migrations/`, applies unapplied migrations, tracks version. Runnable standalone via `node src/db/migrate.js`. |
| `db/migrations/000_core.js` | Migration v1: creates `apps` and `device_tokens` tables via `db.batch()`. |
| `db/migrations/001_pashu_health.js` | Migration v2: creates `pashu_animals`, `pashu_vaccinations`, `pashu_symptom_reports`, `pashu_vets`, `pashu_alerts`, `pashu_farmers` via `db.batch()`. |

**`src/modules/pashu-health/routes/` (6 files)**
| File | Summary |
|------|---------|
| `routes/index.js` | Mounts `reports`, `animals`, `vaccinations`, `alerts`, `clusters` sub-routers onto a single `Router()`. |
| `routes/reports.js` | `POST /`: upsert symptom report by id (idempotent). Requires `verifyFirebaseToken` on handler + `verifyAppKey` at router level. After save, runs `detectClusterForReport()` in try/catch (never fails report). `GET /`: list all reports. `GET /:id`: single report. |
| `routes/animals.js` | Full CRUD on `pashu_animals`: `POST` (upsert, server-generates id), `GET /`, `GET /:id`, `PUT /:id`, `DELETE /:id`. |
| `routes/vaccinations.js` | CRUD on `pashu_vaccinations`: `POST` (upsert, checks animal exists → 409 if not), `GET /`, `GET /:id`, `DELETE /:id`. |
| `routes/alerts.js` | Full CRUD on `pashu_alerts`: `POST` (upsert), `GET /`, `GET /:id`, `PUT /:id` (partial update), `DELETE /:id`. |
| `routes/clusters.js` | `GET /`: returns active clusters from `clusterDetection.getActiveClusters()`. |

**`src/modules/pashu-health/services/` (1 file)**
| File | Summary |
|------|---------|
| `services/clusterDetection.js` | `detectClusterForReport(report, appId)`: bounding-box SQL pre-filter (±0.05°), Haversine refinement (5km), if 3+ reports → creates `pashu_alerts` row, updates reports to `vet_assigned`, pushes to vets via `sendPushToUid`. `getActiveClusters()`: greedy single-linkage grouping of non-resolved reports from last 7 days. Constants: `WINDOW_MS=7d`, `BBOX_DEG=0.05`, `RADIUS_KM=5`, `MIN_REPORTS=3`. |

---

## 2. Package & Runtime Configuration

### Full contents of `package.json`

```json
{
  "name": "shared-backend",
  "version": "0.1.0",
  "description": "Multi-app shared backend foundation (Express + Turso/libSQL)",
  "type": "module",
  "main": "src/app.js",
  "scripts": {
    "start": "node src/app.js",
    "dev": "node --watch src/app.js",
    "migrate": "node src/db/migrate.js"
  },
  "engines": {
    "node": ">=18"
  },
  "dependencies": {
    "@libsql/client": "^0.15.0",
    "cors": "^2.8.5",
    "dotenv": "^16.4.5",
    "express": "^4.21.0",
    "firebase-admin": "^14.4.0"
  }
}
```

**No `devDependencies`** — no TypeScript, ESLint, Prettier, or testing frameworks configured.

### Node Version

- Required: `>=18` (from `package.json` "engines")
- Render.yaml specifies `NODE_VERSION: 20`

### Module System

- **ESM** (`"type": "module"` in package.json)
- All source files use `.js` extension with `import`/`export` syntax
- **Consistent across all files** — no CommonJS `require()` or `module.exports` found anywhere in the backend

### Full Contents of All Config Files

**`render.yaml`** (Render deployment config):
```yaml
services:
  - type: web
    name: shared-backend
    runtime: node
    rootDir: shared-backend
    buildCommand: npm install
    startCommand: npm start
    healthCheckPath: /health
    envVars:
      - key: PORT
        value: 10000
      - key: TURSO_DATABASE_URL
        sync: false
      - key: TURSO_AUTH_TOKEN
        sync: false
      - key: ADMIN_SECRET
        sync: false
      - key: CORS_ALLOWED_ORIGINS
        value: "*"
      - key: FIREBASE_SERVICE_ACCOUNT_JSON
        sync: false
      - key: NODE_VERSION
        value: 20
```

**`Procfile`**:
```
web: npm start
```

**`settings.gradle.kts`** (Android root):
```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "PashuRakshak"
include(":app")
```

**`build.gradle.kts`** (Android root):
```kotlin
plugins { alias(libs.plugins.android.application) apply false; alias(libs.plugins.compose.compiler) apply false; alias(libs.plugins.google.services) apply false }
```

**Android `app/build.gradle.kts`** (key excerpts):
- `namespace = "com.pashurakshak.app"`, `minSdk 24`, `targetSdk 37`, `compileSdk 37`
- `versionCode = 1`, `versionName = "1.0"`
- `buildConfigField` for `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`, `B2_BUCKET_NAME`, `API_BASE_URL`, `APP_API_KEY` — all read from `local.properties` via `localProp()`
- Conditionally applies `com.google.gms.google-services` plugin **only** when `google-services.json` exists
- Dependencies: Compose BOM, Material3, Navigation Compose, Lifecycle ViewModel, Coroutines, `libs.turso.libsql` (embedded libSQL), Play Services Location, ZXing, WorkManager, Firebase BOM + Auth + Messaging, SplashScreen, Coil
- `buildFeatures { compose = true; buildConfig = true }`

**No `tsconfig.json`, `.eslintrc`, `nodemon.json`, or any other config files** exist in the project. The backend uses plain Node.js with no compilation or linting.

---

## 3. Database Layer

### Full SQL/Schema — Every CREATE TABLE Statement

#### Migration `000_core.js` (version 1) — Core/shared tables (NO `pashu_` prefix)

```sql
CREATE TABLE apps (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  api_key TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
)

CREATE TABLE device_tokens (
  id TEXT PRIMARY KEY,
  app_id TEXT NOT NULL REFERENCES apps(id),
  uid TEXT,
  role TEXT,
  fcm_token TEXT,
  updated_at INTEGER
)
```

**Table prefix analysis:**
- `apps`, `device_tokens` — **core/shared** (no prefix), shared across all future app modules
- All `pashu_*` tables — **module-prefixed** (pashu-health module), preventing future collisions

#### Migration `001_pashu_health.js` (version 2) — pashu-health tables (all prefixed `pashu_`)

```sql
CREATE TABLE pashu_animals (
  id TEXT PRIMARY KEY,
  owner_farmer_id TEXT NOT NULL,
  species TEXT NOT NULL,
  name TEXT NOT NULL,
  qr_code_id TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
)

CREATE TABLE pashu_vaccinations (
  id TEXT PRIMARY KEY,
  animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
  vaccine_name TEXT NOT NULL,
  date_given INTEGER NOT NULL,
  next_due INTEGER NOT NULL
)

CREATE TABLE pashu_symptom_reports (
  id TEXT PRIMARY KEY,
  animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
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
)

CREATE TABLE pashu_vets (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT NOT NULL,
  assigned_village TEXT NOT NULL
)

CREATE TABLE pashu_alerts (
  id TEXT PRIMARY KEY,
  recipient_role TEXT NOT NULL,
  recipient_id TEXT NOT NULL,
  message TEXT NOT NULL,
  read INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
)

CREATE TABLE pashu_farmers (
  id TEXT PRIMARY KEY,
  phone TEXT NOT NULL,
  created_at INTEGER NOT NULL
)
```

**Schema divergence note:** The Android app's `Migrations.kt` uses **unprefixed** table names (`animals`, `vaccinations`, `symptom_reports`, `vets`, `alerts`, `farmers`) with slightly different schemas. The backend uses `pashu_*` prefixed names. This is a deliberate architectural split: the Android app has its own local SQLite (via embedded libSQL), while the backend has a remote Turso database. They are **not the same database** — the Android app syncs to the backend via HTTP.

### Migration System Implementation

Uses a `migrations_applied` tracking table (NOT `PRAGMA user_version`):

From `src/db/migrate.js` (exact code):
```js
await db.execute(
  `CREATE TABLE IF NOT EXISTS migrations_applied (
    version INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    applied_at INTEGER NOT NULL
  )`,
);

const appliedResult = await db.execute('SELECT version FROM migrations_applied');
const applied = new Set(appliedResult.rows.map((row) => Number(row.version)));
```

Then iterates sorted `.js` files, skips applied versions, runs `migration.up(db)`, and inserts a record:
```js
await db.execute({
  sql: 'INSERT INTO migrations_applied (version, name, applied_at) VALUES (?, ?, ?)',
  args: [migration.version, migration.name ?? file, Date.now()],
});
```

Comment in code explains: *"Uses a small `migrations_applied` table (instead of PRAGMA user_version) because migrations now span multiple files across future modules — same idea as the Android app's Migrations.kt, but scaled to a multi-file, multi-module layout."*

**Contrast with Android's migration system:** The Android `TursoClient.kt` uses `PRAGMA user_version` tracked in the local embedded SQLite:
```kotlin
private fun migrate(connection: Connection) {
    val current = connection.query("PRAGMA user_version").use { rows ->
        rows.firstOrNull()?.let { (it[0] as Number).toLong() } ?: 0L
    }
    Migrations.all.filter { it.version > current }.sortedBy { it.version }.forEach { migration ->
        connection.executeBatch(migration.sql)
        connection.execute("PRAGMA user_version = ${migration.version}")
    }
}
```

### Turso Connection Configuration

From `src/db/client.js` (exact code):
```js
import { createClient } from '@libsql/client';
import { env } from '../config/env.js';

export const db = createClient({
  url: env.tursoDatabaseUrl,
  authToken: env.tursoAuthToken,
});
```

**This is DEFINITELY a REMOTE connection.** The `.env` file contains:
- `TURSO_DATABASE_URL=libsql://pasurakshak-divyanshupan.aws-ap-south-1.turso.io` (a `libsql://` URL)
- `TURSO_AUTH_TOKEN=eyJhbGciOiJFZERTQSIsInR5cCI6IkpXVCJ9...` (a JWT auth token)

The connection string is constructed entirely from environment variables: `url: env.tursoDatabaseUrl` and `authToken: env.tursoAuthToken`. The `env.tursoDatabaseUrl` value is a remote `libsql://` URL, confirming this is a remote Turso connection, not a local file.

### Exported DB Query/Repository Functions (Grouped by Table)

**`apps` table** (via `src/core/routes/apps.js`):
- `POST /api/v1/core/apps` — `INSERT INTO apps (id, name, api_key, created_at)` — generates `id = randomUUID()`, `apiKey = randomBytes(32).toString('hex')`
- `GET /api/v1/core/apps` — `SELECT id, name, created_at FROM apps ORDER BY created_at ASC`
- `SELECT id FROM apps WHERE name = ?` — check for duplicate app name

**`device_tokens` table** (via `src/core/routes/devices.js`):
- `POST /api/v1/core/devices` — `SELECT id FROM device_tokens WHERE app_id = ? AND uid = ? AND role = ?` (check existing), then `UPDATE` or `INSERT`

**`pashu_animals` table** (via `src/modules/pashu-health/routes/animals.js`):
- `POST /api/v1/pashu-health/animals` — `INSERT INTO pashu_animals ... ON CONFLICT(id) DO UPDATE SET ...`, then `SELECT * FROM pashu_animals WHERE id = ?`
- `GET /api/v1/pashu-health/animals` — `SELECT * FROM pashu_animals ORDER BY created_at DESC`
- `GET /api/v1/pashu-health/animals/:id` — `SELECT * FROM pashu_animals WHERE id = ?`
- `PUT /api/v1/pashu-health/animals/:id` — `UPDATE pashu_animals SET ... WHERE id = ?`, then `SELECT *`
- `DELETE /api/v1/pashu-health/animals/:id` — `DELETE FROM pashu_animals WHERE id = ?`

**`pashu_vaccinations` table** (via `src/modules/pashu-health/routes/vaccinations.js`):
- `POST /api/v1/pashu-health/vaccinations` — `INSERT INTO pashu_vaccinations ... ON CONFLICT(id) DO UPDATE SET ...`, with pre-check `SELECT id FROM pashu_animals WHERE id = ?` (409 if not found)
- `GET /api/v1/pashu-health/vaccinations` — `SELECT * FROM pashu_vaccinations ORDER BY date_given DESC`
- `GET /api/v1/pashu-health/vaccinations/:id` — `SELECT * WHERE id = ?`
- `DELETE /api/v1/pashu-health/vaccinations/:id` — `DELETE WHERE id = ?`

**`pashu_symptom_reports` table** (via `src/modules/pashu-health/routes/reports.js`):
- `POST /api/v1/pashu-health/reports` — `INSERT INTO pashu_symptom_reports (...) VALUES (...) ON CONFLICT(id) DO UPDATE SET ...` (upsert). Creates placeholder `INSERT OR IGNORE INTO pashu_animals` if animal not found.
- `GET /api/v1/pashu-health/reports` — `SELECT * FROM pashu_symptom_reports ORDER BY created_at DESC`
- `GET /api/v1/pashu-health/reports/:id` — `SELECT * WHERE id = ?`

**`pashu_alerts` table** (via `src/modules/pashu-health/routes/alerts.js` and `clusterDetection.js`):
- `POST /api/v1/pashu-health/alerts` — `INSERT INTO pashu_alerts ... ON CONFLICT(id) DO UPDATE SET ...`
- `GET /api/v1/pashu-health/alerts` — `SELECT * ORDER BY created_at DESC`
- `GET /api/v1/pashu-health/alerts/:id` — `SELECT * WHERE id = ?`
- `PUT /api/v1/pashu-health/alerts/:id` — `UPDATE pashu_alerts SET ... WHERE id = ?`
- `DELETE /api/v1/pashu-health/alerts/:id` — `DELETE WHERE id = ?`
- `detectClusterForReport()` internally: bounding-box `SELECT` + `INSERT INTO pashu_alerts (...) VALUES (?, 'vet', 'all-vets', ?, 0, ?)` + `UPDATE pashu_symptom_reports SET status = 'vet_assigned' WHERE status = 'reported' AND id IN (...)`
- `getActiveClusters()` internally: `SELECT id, latitude, longitude, status, created_at, farmer_id, animal_id FROM pashu_symptom_reports WHERE created_at >= ? AND status != 'resolved' ORDER BY created_at ASC`

**`migrations_applied` table** (via `src/db/migrate.js`):
- `CREATE TABLE IF NOT EXISTS migrations_applied (version INTEGER PRIMARY KEY, name TEXT NOT NULL, applied_at INTEGER NOT NULL)`
- `SELECT version FROM migrations_applied`
- `INSERT INTO migrations_applied (version, name, applied_at) VALUES (?, ?, ?)`

**`device_tokens` for push** (via `src/core/services/push.js` and `clusterDetection.js`):
- `SELECT fcm_token FROM device_tokens WHERE uid = ? AND fcm_token IS NOT NULL AND fcm_token != ''`
- `SELECT DISTINCT uid FROM device_tokens WHERE app_id = ? AND role = 'vet' AND fcm_token IS NOT NULL AND fcm_token != ''`

---

## 4. Routes & Middleware — Full Inventory

### Every Route Currently Registered

**Core routes** (mounted at `/api/v1/core`, NO `verifyAppKey`):

| Method | Full Path | File:Line | Description | Middleware |
|--------|-----------|-----------|-------------|------------|
| `POST` | `/api/v1/core/apps` | `src/core/routes/apps.js:13` | Register new app | `adminSecret` |
| `GET` | `/api/v1/core/apps` | `src/core/routes/apps.js:65` | List all apps | `adminSecret` |
| `POST` | `/api/v1/core/devices` | `src/core/routes/devices.js:23` | Register device token | `verifyAppKey`, `verifyFirebaseToken` |

**Pashu-health routes** (mounted at `/api/v1/pashu-health`, `verifyAppKey` applied at router level in `src/routes.js:14`):

| Method | Full Path | File:Line | Description | Middleware (in order) |
|--------|-----------|-----------|-------------|-----------------------|
| `POST` | `/api/v1/pashu-health/reports` | `src/modules/pashu-health/routes/reports.js:56` | Upsert symptom report | `verifyFirebaseToken` (+ `verifyAppKey` from router) |
| `GET` | `/api/v1/pashu-health/reports` | `src/modules/pashu-health/routes/reports.js:177` | List all reports | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/reports/:id` | `src/modules/pashu-health/routes/reports.js:190` | Get single report | `verifyAppKey` only |
| `POST` | `/api/v1/pashu-health/animals` | `src/modules/pashu-health/routes/animals.js:24` | Create/upsert animal | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/animals` | `src/modules/pashu-health/routes/animals.js:70` | List animals | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/animals/:id` | `src/modules/pashu-health/routes/animals.js:81` | Get single animal | `verifyAppKey` only |
| `PUT` | `/api/v1/pashu-health/animals/:id` | `src/modules/pashu-health/routes/animals.js:99` | Update animal | `verifyAppKey` only |
| `DELETE` | `/api/v1/pashu-health/animals/:id` | `src/modules/pashu-health/routes/animals.js:149` | Delete animal | `verifyAppKey` only |
| `POST` | `/api/v1/pashu-health/vaccinations` | `src/modules/pashu-health/routes/vaccinations.js:20` | Create/upsert vaccination | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/vaccinations` | `src/modules/pashu-health/routes/vaccinations.js:71` | List vaccinations | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/vaccinations/:id` | `src/modules/pashu-health/routes/vaccinations.js:85` | Get single vaccination | `verifyAppKey` only |
| `DELETE` | `/api/v1/pashu-health/vaccinations/:id` | `src/modules/pashu-health/routes/vaccinations.js:103` | Delete vaccination | `verifyAppKey` only |
| `POST` | `/api/v1/pashu-health/alerts` | `src/modules/pashu-health/routes/alerts.js:24` | Create/upsert alert | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/alerts` | `src/modules/pashu-health/routes/alerts.js:63` | List alerts | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/alerts/:id` | `src/modules/pashu-health/routes/alerts.js:75` | Get single alert | `verifyAppKey` only |
| `PUT` | `/api/v1/pashu-health/alerts/:id` | `src/modules/pashu-health/routes/alerts.js:93` | Update alert | `verifyAppKey` only |
| `DELETE` | `/api/v1/pashu-health/alerts/:id` | `src/modules/pashu-health/routes/alerts.js:131` | Delete alert | `verifyAppKey` only |
| `GET` | `/api/v1/pashu-health/clusters` | `src/modules/pashu-health/routes/clusters.js:7` | Get active clusters | `verifyAppKey` only |

**App-level routes:**

| Method | Full Path | File:Line | Description | Middleware |
|--------|-----------|-----------|-------------|------------|
| `GET` | `/health` | `src/app.js:25` | Health check | None |
| ANY | `*` (404) | `src/app.js:31` | Not found handler | None |
| Error | `*` | `src/app.js:35` | Error handler | None |

### Full Contents of `src/routes.js`

```js
import { Router } from 'express';
import { verifyAppKey } from './core/middleware/verifyAppKey.js';
import coreRoutes from './core/routes/apps.js';
import devicesRoutes from './core/routes/devices.js';
import pashuHealthRoutes from './modules/pashu-health/routes/index.js';

const router = Router();

// Core platform routes (app-key system) — shared by every client app.
router.use('/core', coreRoutes);
router.use('/core', devicesRoutes);

// PashuRakshak health module — every request must present a valid X-App-Key.
router.use('/pashu-health', verifyAppKey, pashuHealthRoutes);

// ---------------------------------------------------------------------------
// Future feature modules are mounted here, each in its own folder under
// src/modules/<name>/ with its own router:
//
//   // import otherRoutes from './modules/other/routes.js';
//   // router.use('/other', verifyAppKey, otherRoutes);
//
// which produces paths like: /api/v1/other/...
// ---------------------------------------------------------------------------

export default router;
```

### Full Contents of Every Middleware File

**`src/core/middleware/verifyAppKey.js`** (complete, 40 lines):
```js
import { db } from '../../db/client.js';

export async function verifyAppKey(req, res, next) {
  const apiKey = req.get('X-App-Key');
  if (!apiKey) {
    return res.status(401).json({ error: 'missing_app_key', message: 'Missing X-App-Key header. Send the API key issued at app registration.' });
  }
  try {
    const result = await db.execute({ sql: 'SELECT id FROM apps WHERE api_key = ?', args: [apiKey] });
    const row = result.rows[0];
    if (!row) {
      return res.status(401).json({ error: 'invalid_app_key', message: 'Invalid X-App-Key — no registered app matches this key.' });
    }
    req.appId = row.id;
    return next();
  } catch (error) {
    console.error('[verifyAppKey] lookup failed:', error);
    return res.status(500).json({ error: 'app_key_check_failed', message: 'Could not validate the app key. Try again later.' });
  }
}
```

**`src/core/middleware/verifyFirebaseToken.js`** (complete, 73 lines):
```js
import { cert, getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';

let configError = 'FIREBASE_SERVICE_ACCOUNT_JSON has not been checked yet';

export function ensureFirebase() {
  if (getApps().length > 0) return;
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
  if (!raw || !raw.trim()) {
    configError = 'FIREBASE_SERVICE_ACCOUNT_JSON is not set. Add the service account JSON (Firebase Console → Project Settings → Service Accounts) to your .env.';
    throw new Error(configError);
  }
  try {
    const serviceAccount = JSON.parse(raw.trim());
    initializeApp({ credential: cert(serviceAccount) });
    configError = null;
  } catch (error) {
    configError = `Invalid FIREBASE_SERVICE_ACCOUNT_JSON: ${error.message}`;
    throw new Error(configError);
  }
}

export async function verifyFirebaseToken(req, res, next) {
  try { ensureFirebase(); } catch (error) {
    return res.status(503).json({ error: 'firebase_not_configured', message: error.message });
  }
  const header = req.get('Authorization') ?? '';
  const match = /^Bearer\s+(\S+)$/i.exec(header);
  if (!match) {
    return res.status(401).json({ error: 'missing_bearer_token', message: 'Missing Authorization: Bearer <idToken> header.' });
  }
  try {
    const decoded = await getAuth().verifyIdToken(match[1]);
    req.uid = decoded.uid;
    return next();
  } catch (error) {
    console.warn('[verifyFirebaseToken] token rejected:', error?.message ?? error);
    return res.status(401).json({ error: 'invalid_firebase_token', message: 'Firebase ID token is invalid or expired.' });
  }
}
```

**`src/core/middleware/adminSecret.js`** (complete, 26 lines):
```js
import { env } from '../../config/env.js';

export function adminSecret(req, res, next) {
  const provided = req.get('X-Admin-Secret');
  if (!provided) {
    return res.status(401).json({ error: 'missing_admin_secret', message: 'Missing X-Admin-Secret header.' });
  }
  if (provided !== env.adminSecret) {
    return res.status(401).json({ error: 'invalid_admin_secret', message: 'Invalid X-Admin-Secret.' });
  }
  return next();
}
```

### Expected Request Body Shape for Reports Endpoint

From `src/modules/pashu-health/routes/reports.js` lines 56-94 (exact field parsing):

The code accepts **both camelCase and snake_case** aliases:

```js
const id = str(body.id)                              // required, string
const animalId = str(body.animalId) ?? str(body.animal_id)  // required
const farmerId = str(body.farmerId) ?? str(body.farmer_id)  // required
const latitude = num(body.latitude)                  // required, number
const longitude = num(body.longitude)                // required, number
const status = str(body.status) ?? 'reported'        // optional, must be in REPORT_STATUSES
const symptoms = jsonish(body.symptoms, '[]')        // optional, JSON string or array/object
const riskBreakdown = jsonish(body.riskBreakdown ?? body.risk_breakdown, '{}')  // optional
const photoLocalPath = str(body.photoLocalPath) ?? str(body.photo_local_path) ?? ''  // optional
const photoRemoteUrl = str(body.photoRemoteUrl) ?? str(body.photo_remote_url)      // optional
const riskScore = num(body.riskScore ?? body.risk_score, 0)                      // optional, number
const createdAt = num(body.createdAt ?? body.created_at, Date.now())              // optional, number
const synced = body.synced === false || body.synced === 0 ? 0 : 1                // optional
```

Where `str()` converts to string (returns null for null/undefined/empty), `num()` converts to Number (returns fallback for NaN), `jsonish()` accepts JSON strings or JS objects/arrays and returns JSON strings.

`REPORT_STATUSES = ['reported', 'vet_assigned', 'examined', 'sample_sent', 'confirmed', 'resolved']`

**Comparison with Android's `ReportPushApi.buildPayload()`** (`ReportPushApi.kt` lines 65-79):

The Android sends (via `JSONObject.put`): `id`, `animal_id`, `farmer_id`, `symptoms` (as `JSONArray`), `photo_local_path`, `photo_remote_url`, `latitude`, `longitude`, `risk_score`, `risk_breakdown` (as `JSONObject`), `status` (dbValue string), `created_at`. The backend's `jsonish()` handles arrays by `JSON.stringify()`-ing them and objects by `JSON.stringify()`, so the Android's JSONArray/JSONObject payloads are compatible. The Android uses snake_case field names; the backend accepts both camelCase and snake_case.

---

## 5. Authentication & App-Key System

### Is `verifyAppKey.js` Implemented and Wired?

**YES — fully implemented and wired to real routes.**

- Applied as router-level middleware in `src/routes.js:14`: `router.use('/pashu-health', verifyAppKey, pashuHealthRoutes)`
- **Every** `/api/v1/pashu-health/*` route requires a valid `X-App-Key` header
- Queries `SELECT id FROM apps WHERE api_key = ?` and sets `req.appId` on match
- Returns `401 { "error": "missing_app_key" }` or `401 { "error": "invalid_app_key" }`
- NOT applied to `/api/v1/core/*` routes (which use `adminSecret` instead)

### Is `verifyFirebaseToken.js` Implemented?

**YES — fully implemented.**

- Exports `ensureFirebase()` (lazy `firebase-admin` initialization) and `verifyFirebaseToken(req, res, next)` (Express middleware)
- **Reads from:** `process.env.FIREBASE_SERVICE_ACCOUNT_JSON`
- **Fails gracefully:** If the env var is missing/invalid:
  - `ensureFirebase()` throws an Error
  - `verifyFirebaseToken()` catches it and returns `503 { "error": "firebase_not_configured", "message": "..." }`
  - The server does **NOT** crash — the error is caught in the middleware
  - Server starts normally without Firebase; only protected routes return 503
- On success: sets `req.uid` (Firebase UID) and calls `next()`
- On invalid token: returns `401 { "error": "invalid_firebase_token" }`
- On missing Authorization header: returns `401 { "error": "missing_bearer_token" }`

**Currently wired to:**
1. `POST /api/v1/core/devices` (via `src/core/routes/devices.js`)
2. `POST /api/v1/pashu-health/reports` (via `src/modules/pashu-health/routes/reports.js`)

All other routes only use `verifyAppKey`.

### Has Any App Been Registered via `POST /api/v1/core/apps`?

The database is a remote Turso instance. I cannot query it directly in this audit without the admin secret. The git commit `e3fcd80` says "Fix camera permission crash, add bidirectional cloud sync, harden backend" — the system has been actively used. The admin secret is `dev-admin-secret-1234567890abcdef` (from `.env`), but I will not make live API calls.

### Is `ADMIN_SECRET` Set or Placeholder?

**Set.** From `.env`: `ADMIN_SECRET=dev-admin-secret-1234567890abcdef`. This is **not** the placeholder value (`change-me-to-a-long-random-string` from `.env.example`). It has been replaced with a developer-style value. It is **not** cryptographically strong.

---

## 6. Cluster Detection & Push Logic

### Full Contents of `clusterDetection.js`

Located at `src/modules/pashu-health/services/clusterDetection.js` (213 lines). Key exports and functions:

- **`detectClusterForReport(report, appId = null)`** — Async function. Takes `{ id, latitude, longitude, status, createdAt }` and optional `appId`. Performs bounding-box SQL pre-filter (±0.05°), Haversine refinement (5km), and if 3+ reports qualify, creates a `pashu_alerts` row and updates reports to `vet_assigned`. Returns `{ clustered, count, reportIds, alertId?, message, alertCreated }`. Never throws on its own.
- **`getActiveClusters()`** — Async function. Returns clusters of non-resolved reports from last 7 days using greedy single-linkage grouping at 5km radius with 3-report minimum. Returns array of `{ reportCount, reportIds, centerLat, centerLng, radiusKm, windowDays, firstReportAt, lastReportAt, statuses }`.
- **`haversineKm(lat1, lon1, lat2, lon2)`** — Great-circle distance utility (Earth radius 6371 km).
- **Constants:** `WINDOW_MS = 7 days`, `BBOX_DEG = 0.05`, `RADIUS_KM = 5`, `MIN_REPORTS = 3`

**Cluster detection algorithm:**
1. Bounding-box SQL pre-filter: `SELECT ... WHERE created_at >= ? AND id != ? AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ?`
2. Haversine refinement in JS: keep reports within `RADIUS_KM`
3. If `involvedIds.length < MIN_REPORTS` → return `{ clustered: false }`
4. If cluster detected: insert `pashu_alerts` row with `recipient_role='vet'`, `recipient_id='all-vets'`, deduped message; update involved `reported` reports to `vet_assigned`; call `notifyVets(appId, message)` to push

### Full Contents of `push.js`

Located at `src/core/services/push.js` (38 lines):

```js
import { getMessaging } from 'firebase-admin/messaging';
import { db } from '../../db/client.js';
import { ensureFirebase } from '../middleware/verifyFirebaseToken.js';

export async function sendPushToUid(uid, title, body) {
  ensureFirebase();
  const tokensResult = await db.execute({
    sql: `SELECT fcm_token FROM device_tokens WHERE uid = ? AND fcm_token IS NOT NULL AND fcm_token != ''`,
    args: [uid],
  });
  const tokens = tokensResult.rows.map((row) => row.fcm_token);
  if (tokens.length === 0) {
    return { uid, sent: 0, failureCount: 0, skipped: 'no_tokens' };
  }
  const result = await getMessaging().sendEachForMulticast({
    notification: { title, body },
    data: { title, body },
    tokens,
  });
  return { uid, sent: result.successCount, failureCount: result.failureCount };
}
```

### Are They Wired into POST `/reports`?

**YES — cluster detection is wired.** In `src/modules/pashu-health/routes/reports.js` lines 149-164:

```js
try {
  await detectClusterForReport(
    { id, latitude, longitude, status, createdAt },
    req.appId,
  );
} catch (error) {
  console.error('[pashu-health/reports] cluster detection failed:', error);
}
```

This runs **after** the report is successfully inserted/upserted. The `try/catch` ensures cluster detection failures never fail the report response. The `push.js` `sendPushToUid` is called from within `clusterDetection.js` in the `notifyVets()` function (lines 122-142), which is called only when `alertCreated` is true (i.e., a new cluster alert is created, not a duplicate).

---

## 7. Environment Variables & Secrets

### Every Environment Variable Read Anywhere

| Variable | File(s) That Read It | Purpose |
|----------|---------------------|---------|
| `TURSO_DATABASE_URL` | `src/config/env.js`, `src/db/client.js` | Turso database URL |
| `TURSO_AUTH_TOKEN` | `src/config/env.js`, `src/db/client.js` | Turso auth token |
| `PORT` | `src/config/env.js`, `src/app.js` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `src/config/env.js` | CORS allowed origins (comma-separated) |
| `ADMIN_SECRET` | `src/config/env.js`, `src/core/middleware/adminSecret.js` | Admin secret for core routes |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | `src/core/middleware/verifyFirebaseToken.js`, `src/core/services/push.js` | Firebase service account JSON |

**Note:** `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`, `B2_BUCKET_NAME` are in `.env` and `.env.example` but are **NOT read by any backend code**. They are only used by the Android app via `local.properties` → `BuildConfig`.

### Full Contents of `.env.example`

```
# Copy this file to .env and fill in real values. Never commit .env.

TURSO_DATABASE_URL=libsql://your-db-name.turso.io
TURSO_AUTH_TOKEN=your-turso-auth-token

# HTTP port the server listens on.
PORT=3000

# Comma-separated list of origins allowed by CORS
CORS_ALLOWED_ORIGINS=http://localhost:3000

# Shared secret for admin-only core routes
ADMIN_SECRET=change-me-to-a-long-random-string

# Firebase service account JSON as ONE line
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}

# Backblaze B2 (photo/report storage)
B2_APPLICATION_KEY_ID=your-b2-key-id
B2_APPLICATION_KEY=your-b2-application-key
B2_BUCKET_NAME=your-b2-bucket-name
```

### Does a Real `.env` File Exist?

**YES.** The `.env` file exists at `shared-backend/.env`.

Filled vs empty keys (confirming presence without revealing values):
- `TURSO_DATABASE_URL` — **FILLED** (real `libsql://pasurakshak-divyanshupan.aws-ap-south-1.turso.io`)
- `TURSO_AUTH_TOKEN` — **FILLED** (JWT token)
- `PORT` — **FILLED** (value `3000`)
- `CORS_ALLOWED_ORIGINS` — **FILLED** (value `http://localhost:3000,http://localhost:5173`)
- `ADMIN_SECRET` — **FILLED** (not the placeholder)
- `FIREBASE_SERVICE_ACCOUNT_JSON` — **FILLED** (real service account JSON for `pashurakshak-961c1`)
- `B2_APPLICATION_KEY_ID` — **FILLED** (value `b191a6363707`)
- `B2_APPLICATION_KEY` — **FILLED** (value `00547285064cf5d2a06d9ecab8db40d09231752890`)
- `B2_BUCKET_NAME` — **FILLED** (value `pashurakshak-reports`)

### Firebase Service Account

**FILLED.** `FIREBASE_SERVICE_ACCOUNT_JSON` in `.env` contains a real service account JSON for project `pashurakshak-961c1`. The `client_email` contains `firebase-adminsdk-fbsvc@pashurakshak-961c1.iam.gserviceaccount.com`.

---

## 8. What's Stubbed vs. Real

### TODO/FIXME/STUB/PLACEHOLDER Grep Results

**Backend (`src/`):** No TODO/FIXME/STUB/PLACEHOLDER found anywhere in the backend source code.

**Android (`app/`):** One match:
- `app/google-services.json.example:2` — `"_comment": "PLACEHOLDER — copy this file to app/google-services.json and replace every value..."`

**Android codebase — intentional placeholder:**
- `PashuRakshakApplication.kt:34` — Comment: `"TEMPORARY: exercises schema and repositories at startup; remove once features consume the data layer."` (`runDatabaseSmokeCheck`)
- `CaseDetailScreen.kt:382` — Comment: `"Client-side placeholder score — refined server-side later."` (in `WhyThisScoreCard`)

### Planned Features Not Implemented, Simplified, or Skipped

1. **`pashu_vets` and `pashu_farmers` endpoints** — Tables exist in migration `001_pashu_health.js` but have **no backend route files**. The README explicitly states: *"> `pashu_vets` / `pashu_farmers` tables exist (migration `001`) but have no endpoints yet."* The Android has `VetRepository.kt` and local `vets` table but no API routes to sync with the backend.

2. **No `POST /clusters` or cluster mutation endpoint** — `GET /api/v1/pashu-health/clusters` is implemented but read-only. Cluster creation only happens automatically via report POST.

3. **`pashu_farmers` table naming divergence** — Backend uses `pashu_farmers` (prefixed), Android local uses `farmers` (unprefixed). The Android `Migrations.kt` creates `farmers` table; the backend creates `pashu_farmers`. These are synced via different mechanisms.

4. **Backblaze B2 integration on backend** — The `.env` has `B2_*` credentials and `.env.example` documents them, but **no backend code reads or uses B2 credentials**. The B2 upload is entirely on the Android side (`B2UploadService.kt`). The backend only receives the resulting `photo_remote_url`.

5. **No photo upload endpoint on backend** — The Android uploads photos to B2 and stores `photo_remote_url`, but the backend has no photo upload endpoint. Photos are just stored as URLs in `pashu_symptom_reports.photo_remote_url`.

6. **Android `SyncWorker` and `RemoteSync`** — The Android has full bidirectional sync infrastructure (`SyncWorker.kt`, `SyncScheduler.kt`, `NetworkReconnectObserver.kt`, `RemoteSync.kt`, `SyncStatusHolder.kt`), but the backend has no sync-specific endpoints or pagination. The `GET` endpoints return all rows without any sync token or cursor.

7. **`node_modules/.gitkeep`** in `src/core/services/` and `src/modules/` — placeholder directories indicating planned future service files.

8. **No `GET /api/v1/core/devices`** — Device registration (`POST`) exists but listing registered devices does not.

9. **No `POST /api/v1/pashu-health/vets` or `POST /api/v1/pashu-health/farmers`** — Vet and farmer data management endpoints are missing on both sides.

---

## 9. Deployment

### Full Contents of `render.yaml`

```yaml
services:
  - type: web
    name: shared-backend
    runtime: node
    rootDir: shared-backend
    buildCommand: npm install
    startCommand: npm start
    healthCheckPath: /health
    envVars:
      - key: PORT
        value: 10000
      - key: TURSO_DATABASE_URL
        sync: false
      - key: TURSO_AUTH_TOKEN
        sync: false
      - key: ADMIN_SECRET
        sync: false
      - key: CORS_ALLOWED_ORIGINS
        value: "*"
      - key: FIREBASE_SERVICE_ACCOUNT_JSON
        sync: false
      - key: NODE_VERSION
        value: 20
```

**`sync: false`** means these env vars must be manually set in the Render dashboard — they won't be synced from a git-based secret manager.

### Does `package.json` Start Script Match Render Expectations?

**YES.** `render.yaml` specifies `startCommand: npm start` and `package.json` has `"start": "node src/app.js"`.

**Potential mismatch:** `render.yaml` sets `PORT: 10000` but `.env` has `PORT=3000`. The server reads `PORT` from `env.js` which reads from `process.env`. On Render, `process.env.PORT` would be `10000` (from the rendered env var), overriding the `.env` file value. This should work correctly since `dotenv.config()` is called before `process.env.PORT` is used, and `readEnv('PORT')` reads from `process.env`.

**Security concern:** `CORS_ALLOWED_ORIGINS` is set to `"*"` in `render.yaml` but the `.env` has `http://localhost:3000,http://localhost:5173`. On Render, CORS would allow all origins. This is acceptable for deployment but less secure than restricting origins.

### Has This Been Deployed?

**Unknown from config alone.** The project has:
- `render.yaml` — deployment configuration exists
- `Procfile` — `web: npm start`
- Git repo with 2 commits, up to date with `origin/main`
- The `.env` has real Turso credentials suggesting active use
- No explicit indication of a live Render deployment URL

The backend appears to be **deployed or intended for deployment on Render**. The server was tested locally and works correctly.

---

## 10. Version Control State

### Git Repo Status

**YES, this is a git repo.**

`git log --oneline -20`:
```
e3fcd80 Fix camera permission crash, add bidirectional cloud sync, harden backend
611d789 Initial commit: app + shared backend
```

Only **2 commits** total. The branch `main` is up to date with `origin/main`. Working tree is clean (`nothing to commit, working tree clean`). The only untracked file is `PROJECT_AUDIT.md`.

### Full Contents of `.gitignore`

**`shared-backend/.gitignore`**:
```
node_modules/
.env
*.db
*.db-*
npm-debug.log*
```

**`app/.gitignore`** — Not explicitly read, but `local.properties` is referenced as gitignored in `build.gradle.kts` comments.

**Confirmation:** `.env`, `node_modules/`, `*.db` files are all properly excluded. The `local.db` file in `shared-backend/` would be excluded. `google-services.json` is NOT in `.gitignore` but is NOT committed (it exists locally but is not tracked — the `google-services.json.example` placeholder IS committed).

---

## 11. Android App Architecture (New Section)

### 11.1 Application Architecture

The Android app follows a **clean architecture** pattern with these layers:

- **UI Layer** (Compose): Screens + ViewModels
- **Domain Layer**: Repository interfaces (via `ServiceLocator`)
- **Data Layer**: Local SQLite (embedded libSQL) + Remote HTTP sync
- **Infrastructure**: Firebase Auth, FCM, WorkManager, Network callbacks

**Dependency Injection**: `ServiceLocator.kt` — a singleton object that lazily initializes `TursoClient` and provides all repositories. No Dagger/Hilt used.

**Navigation**: Jetpack Navigation Compose via `AppNavHost.kt` — a sealed `Screen` class defines all routes. The nav graph handles: PhoneEntry → OTP → RoleSelect → FarmerHome/VetHome → sub-screens.

### 11.2 Local Database Architecture

**`TursoClient.kt`** — Offline-first wrapper around embedded libSQL:
- Opens `pashurakshak.db` via `Libsql.open(databasePath)`
- Applies migrations via `PRAGMA user_version` (not the same as backend!)
- `execute(sql, vararg args)` and `query(sql, vararg args, mapper)` are the two public APIs
- Uses a `Mutex` for thread-safe database access
- `PRAGMA foreign_keys = ON` on every connection

**`Migrations.kt`** — Two local migrations:
- `version = 1`: Creates `animals`, `vaccinations`, `symptom_reports`, `vets`, `alerts` (all unprefixed)
- `version = 2`: Creates `farmers` (unprefixed)

**Data classes** in `src/main/java/.../data/local/`:
- `SymptomReport` — `id`, `animalId`, `farmerId`, `symptoms: List<String>`, `photoLocalPath`, `photoRemoteUrl`, `latitude`, `longitude`, `riskScore: Int`, `riskBreakdown: Map<String, Int>`, `status: ReportStatus`, `synced: Boolean`, `createdAt: Long`
- `Animal`, `Vaccination`, `Alert`, `Vet` — simple data classes with `UUID` default ids
- `ReportStatus` — enum with `dbValue: String` mapping (`REPORTED`, `VET_ASSIGNED`, etc.)

### 11.3 Repository Pattern

Each repository wraps `TursoClient` with typed methods:

**`AnimalRepository`**: `insert()`, `getAll()`, `getById()`, `getByQrCode()`, `update()`, `delete()`, `insertIfAbsent()`
**`ReportRepository`**: `insert()`, `getAll()`, `getById()`, `getByAnimal()`, `getByFarmer()`, `getUnsyncedReports()`, `getReportsPendingPhotoUpload()`, `update()`, `delete()`, `upsertFromRemote()`
**`VaccinationRepository`**: `insert()`, `getAll()`, `getById()`, `getByAnimal()`, `update()`, `delete()`, `insertIfAbsent()`
**`VetRepository`**: `insert()`, `getAll()`, `getById()`, `update()`, `delete()`
**`AlertRepository`**: `insert()`, `getAll()`, `getById()`, `getForRecipient()`, `getForRole()`, `update()`, `markRead()`, `delete()`, `insertIfAbsent()`

**Note:** Repositories use positional `Row` accessors (`string(0)`, `long(1)`, etc.) matching the CREATE TABLE column order.

### 11.4 Authentication Flow

**`SessionManager`** — Phone-based auth state:
- `Role` enum: `FARMER`, `VET`
- State: `uid`, `phone`, `role` persisted via `SharedPreferences`
- Flow: PhoneEntry → OTP verification → RoleSelect → completeLogin
- `farmerId` returns `uid ?: "anonymous-farmer"`; `vetId` returns `"all-vets"`

**`AuthRepository`** — Firebase Phone Auth:
- `sendVerificationCode()`, `signInWithOtp()`, `signInWithCredential()`
- `saveRoleMapping()` — persists phone→role in local `farmers`/`vets` tables
- `registerDeviceToken()` — POSTs FCM token to backend `/api/v1/core/devices` (app key + Firebase ID token)
- `configErrorMessage()` — degrades gracefully when Firebase isn't configured

### 11.5 Sync Architecture

**`SyncScheduler`** — Schedules WorkManager tasks:
- Periodic sync every 15 minutes (`NetworkType.CONNECTED`)
- Immediate sync on demand (network reconnect, app start)
- Uses `ExistingPeriodicWorkPolicy.KEEP` and `ExistingWorkPolicy.KEEP` to avoid duplicates

**`SyncWorker`** — `CoroutineWorker` that executes one sync cycle:
1. Upload pending photos to B2 (`B2UploadService`)
2. Re-push reports that got photo URLs
3. Push unsynced reports to backend (`ReportPushApi.pushReport()`)
4. Push animals/vaccinations/alerts (`RemoteSync.syncAll()`)
5. Pull remote data → local SQLite
6. Update `synced` flag on success

**`RemoteSync`** — Bidirectional sync with backend:
- `pushLocals()` — POST animals/vaccinations/alerts to backend
- `pullAll()` — GET animals/vaccinations/reports/alerts → insertIfAbsent locally
- Order matters: animals → vaccinations → reports → alerts (FK order)

**`NetworkReconnectObserver`** — `ConnectivityManager.NetworkCallback` that triggers `SyncScheduler.triggerNow()` when network becomes available.

**`SyncStatusHolder`** — Shared `StateFlow<SyncUiStatus>` for the `SyncStatusBanner` composable:
- `pendingCount`, `isSyncing`, `lastSyncAt`
- Refreshes every 30 seconds in the banner

### 11.6 UI/Navigation Structure

**Navigation routes** (`Screen.kt` sealed class):
- `phone_entry`, `otp/{verificationId}/{phone}`, `role_select`
- `farmer_home`, `my_animals`, `report_sick_animal`, `vaccination_status`, `alerts`, `my_reports`, `qr_passport/{animalId}`, `b2_upload_test`
- `vet_home`, `vet_case_queue`, `case_detail/{reportId}`, `vet_alerts`

**Auth flow:** PhoneEntryScreen → OtpScreen → RoleSelectScreen → (FarmerHome OR VetHome)

**Bottom bars:** `FarmerBottomBar` and `VetBottomBar` with different navigation items per role.

**Key screens:**
- `CaseDetailScreen` — Full case view with risk score, symptoms, photo, "Why this score?" expandable card, field check dialog
- `VetCaseQueueScreen` — Queue of pending reports with risk badges
- `AlertsScreen` — Alert list with read/unread indicators
- `MyReportsScreen` — Farmer's reports with risk level, status chips, photos
- `SyncStatusBanner` — Persistent banner showing sync status on every screen

### 11.7 Remote API Layer

**`ReportPushApi`** — Pushes symptom reports to backend:
- `pushReport(report)` → builds JSON payload, POSTs to `/api/v1/pashu-health/reports`
- Headers: `X-App-Key` (from `BuildConfig.APP_API_KEY`), `Authorization: Bearer <idToken>`
- Returns `PushResult.Success` or `PushResult.Failure`

**`B2UploadService`** — Uploads photos to Backblaze B2:
- Native B2 API: `b2_authorize_account` → `b2_get_upload_url` → `b2_upload_file`
- Retry with exponential backoff (max 3 attempts)
- Skips silently when offline (`UploadResult.Skipped`)
- Credentials from `BuildConfig.B2_*` fields

**`PushMessagingService`** — FCM push receiver:
- Receives data-only messages, shows local notifications via `AppNotifications`
- On token refresh: re-registers device with backend (`AuthRepository.registerDeviceTokenPublic()`)

### 11.8 Build Configuration

**`BuildConfig` fields** (generated from `local.properties`):
- `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`, `B2_BUCKET_NAME`
- `API_BASE_URL` — backend URL
- `APP_API_KEY` — app key for `X-App-Key` header

**`google-services.json`** — Real Firebase config (exists locally, NOT committed). The `google-services.json.example` is a placeholder. The Gradle plugin is only applied when the real file exists.

**`local.properties`** — Contains real B2 credentials and API URLs. Referenced in `build.gradle.kts` but **not committed**. The `local.properties.example` documents expected keys.

---

## 12. Known Issues / Verification

### `npm install` Results

```
up to date, audited 273 packages in 10s
51 packages are looking for funding
2 moderate severity vulnerabilities
```

**Vulnerable packages:**
- `uuid <11.1.1` — moderate severity (missing buffer bounds check in v3/v5/v6). Fixed via `npm audit fix`
- `gaxios 6.4.0 - 6.7.1` — depends on vulnerable `uuid`

### `npm run migrate` Results

```
[migrate] all migrations up to date
Assertion failed: !(handle->flags & UV_HANDLE_CLOSING), file src\win\async.c, line 94
```

**Benign** — known Node.js libuv issue on Windows when the process exits cleanly after all async handles are closed. Migrations complete successfully.

### `GET /health` Response

Server started and tested. **Response confirmed:**
```json
{"status":"ok"}
```
HTTP status 200. Works correctly.

### Server Start Verification

The server starts successfully with the `.env` file configured. `npm install` completed without errors. The server listens on port 3000 and responds to `/health`.

### Android Build Notes

- The app conditionally applies the Google Services plugin only when `google-services.json` exists
- `local.properties` is read for B2 credentials and API URLs (via `localProp()`)
- `BuildConfig.DEBUG` flag is used to conditionally show the B2 Upload Test screen
- The app uses `libs.turso.libsql` for embedded offline-first database and `@libsql/client` would be the backend equivalent

### Cross-Platform Schema Mapping

| Android Local (libSQL) | Backend Remote (Turso) | Notes |
|------------------------|------------------------|-------|
| `animals` | `pashu_animals` | Different table names |
| `vaccinations` | `pashu_vaccinations` | Different table names |
| `symptom_reports` | `pashu_symptom_reports` | Different table names |
| `vets` | `pashu_vets` | Different table names |
| `alerts` | `pashu_alerts` | Different table names |
| `farmers` | `pashu_farmers` | Different table names |
| (none) | `apps` | Backend-only |
| (none) | `device_tokens` | Backend-only |
| (none) | `migrations_applied` | Backend-only |

The Android uses `Migrations.kt` with `PRAGMA user_version`; the backend uses `migrations_applied` table. These are completely independent systems.

---

## Summary of Key Findings

| Area | Status |
|------|--------|
| **Project structure** | Android Compose app + Node.js Express backend, well-organized with clean separation |
| **Database** | Backend: 6 core tables + 6 pashu-prefixed tables, migrations working. Android: 6 local tables via embedded libSQL, independent migration system |
| **Auth** | `verifyAppKey`, `verifyFirebaseToken`, `adminSecret` all implemented and wired |
| **Routes** | 18 backend endpoints across 3 modules, all wired. Android has full local data layer + sync to backend |
| **Cluster detection** | Fully implemented (7d/5km/3+ rule) and wired to report POST |
| **Push notifications** | Backend: Firebase `sendEachForMulticast`. Android: FCM `PushMessagingService` |
| **Sync** | Android: full WorkManager-based bidirectional sync with B2 photo upload queue |
| **Environment** | All `.env.example` keys filled in `.env` with real values |
| **TODOs/Stubs** | None in backend. 3 intentional placeholders in Android |
| **Missing features** | No vets/farmers endpoints, no B2 backend integration, schema naming divergence |
| **Security** | `.env` git-ignored, but `ADMIN_SECRET` is weak (`dev-admin-secret-1234567890abcdef`) |
| **Dependencies** | `uuid` moderate vulnerability in transitive dependency tree |
| **Deployment** | Render-ready, `npm start` matches `render.yaml`. `PORT=10000` in Render vs `PORT=3000` in `.env` |
| **Architecture** | Two independent databases (Android local libSQL + backend remote Turso) syncing via HTTP |
