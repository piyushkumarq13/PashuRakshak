# PashuRakshak

Livestock disease early-warning Android app: farmers report sick animals (with photo +
location), a client-side risk score flags urgency, vets work a case queue, vaccination
records and alerts keep everyone informed. Offline-first — reports sync when connectivity
returns; FCM push delivers alerts.

> **Status:** feature-complete through Phase 8 (auth, sync engine, push, polish). Next up:
> backend integration testing (the report push API is still a stub — see `ReportPushApi`).

## Tech stack

| Component | Choice | Version |
|---|---|---|
| Language | Kotlin (built-in with AGP) | 2.2.10 (bundled) |
| Build system | Gradle (wrapper) | 9.6.0 |
| Android Gradle Plugin | `com.android.application` | 9.4.1 |
| UI toolkit | Jetpack Compose (BOM) | 2026.09.00 |
| Design system | Material 3 | via BOM |
| Navigation | `androidx.navigation:navigation-compose` | 2.10.1 |
| Async / DI-ish wiring | coroutines + manual `ServiceLocator` | 1.11.0 |
| Database | Turso libSQL (local embedded file) | 0.1.2 |
| Background sync | WorkManager | 2.10.1 |
| Auth + push | Firebase Phone Auth, FCM | firebase-bom 33.16.0 |
| Photo storage | Backblaze B2 (native API) | — |
| SDK | minSdk 24 / target+compileSdk 37 | — |

## Setup for a new developer

### 1. Clone + build

```powershell
.\gradlew.bat assembleDebug
```

Open the project root in Android Studio and run the `app` configuration. The app builds
and runs **without** any of the external config below (Firebase login/push and B2 upload
report a friendly "not configured" error until you add credentials).

### 2. Firebase config (Phone Auth + FCM)

1. Create a project at <https://console.firebase.google.com>.
2. Add an Android app with package name **`com.pashurakshak.app`**.
3. Download the real `google-services.json` and save it as **`app/google-services.json`**
   (a placeholder template lives at `app/google-services.json.example` — copy + replace
   every value). The Google Services Gradle plugin only applies when this file exists.
4. **Phone Auth:** enable it under *Build → Authentication → Sign-in method → Phone*.
   Register your debug SHA-1 **and** SHA-256:
   ```powershell
   .\gradlew.bat signingReport
   ```
   (*Project settings → Your apps → SHA certificate fingerprints → Add fingerprint*).
   Without fingerprints, OTP verification fails on real devices.
5. **FCM:** send **data-only** messages so `PushMessagingService.onMessageReceived`
   fires in foreground *and* background:
   ```json
   { "title": "New high-risk case", "body": "Cow 'Gauri' — risk score 70" }
   ```
   Tapping the notification opens the app on the Alerts screen. Register a real sender
   key/server key for production sends from the Firebase console or your backend.

### 3. Backblaze B2 credentials (photo upload)

1. Create a B2 bucket and an application key with `listBuckets` + `writeFiles`.
2. Copy `local.properties.example` → `local.properties` (already git-ignored) and fill in:
   ```properties
   B2_APPLICATION_KEY_ID=your-key-id
   B2_APPLICATION_KEY=your-key
   B2_BUCKET_NAME=your-bucket
   ```
   These are read into `BuildConfig` at build time — never commit them. Empty values are
   fine for development; photo upload reports "credentials missing" until filled.
3. Mirror the same three values in `shared-backend/.env` (documented there for deploy;
   the backend does not upload to B2 today — the app talks to B2 directly).

### 4. Turso connection

The app currently runs against a **local embedded libSQL file** (`pashurakshak.db`,
created on first launch — zero setup). When the shared/cloud Turso database lands:

1. Create a database: `turso db create pashurakshak`
2. Get the URL + auth token: `turso db show pashurakshak` / `turso db tokens create pashurakshak`
3. Add them to `local.properties` and wire them into `data/local/TursoClient.kt`
   (the class is already isolated for this swap).

Schema migrations live in `data/local/Migrations.kt` and run automatically via
`PRAGMA user_version`.

## Running a full local pass

1. **Login:** phone entry → OTP → pick Farmer or Vet (mapping stored in local
   `farmers`/`vets` tables). Without Firebase configured you'll see a setup hint instead.
2. **Farmer:** add animals → report a sick animal (photo, location, symptoms) →
   vaccination log; alerts appear on the Alerts tab.
3. **Vet:** case queue sorted by risk (green/orange/red) → case detail → field check.
4. **Sync:** the status banner shows "X reports pending sync"; WorkManager pushes
   photos to B2 then `POST /reports` (stub) every 15 min or on reconnect.
5. **Push:** send the data payload above from the Firebase console → notification →
   tap → Alerts screen.

## Key decisions

- **AGP 9 built-in Kotlin** — never apply `org.jetbrains.kotlin.android`; the Compose
  compiler plugin is pinned to the bundled 2.2.10.
- **MVVM with StateFlow** — immutable UI state, no LiveData; ViewModels built inline via
  `viewModel { }` + `ServiceLocator` (no Hilt).
- **Offline-first** — local libSQL is the source of truth; `synced=0` rows are the queue.
  WorkManager uses `NetworkType.CONNECTED` constraints (no polling loops).
- **Firebase optional at build time** — google-services plugin applies only when
  `app/google-services.json` exists, so CI/dev builds never break on missing config.
- **Risk palette** — green (`#2E7D32`) healthy/low, orange (`#EF6C00`) medium,
  red (`#D32F2F`) high; see `ui/vet/RiskLevel.kt` (`RiskColors`).

## Package structure

```
com.pashurakshak.app
├── MainActivity.kt            # single activity, splash + notification routing
├── PashuRakshakApplication.kt # ServiceLocator, session, sync + FCM channel init
├── ui/                        # Compose screens, ViewModels, theme
│   ├── auth/                  # phone entry + OTP
│   ├── farmer/ vet/           # role feature screens
│   ├── components/            # bottom bars, sync banner
│   └── debug/                 # manual B2 upload test screen (DEBUG builds)
├── data/
│   ├── local/                 # libSQL client, schema migrations, entities
│   ├── remote/                # B2 upload, report push stub, FCM service
│   └── sync/                  # SyncWorker, scheduler, reconnect observer
├── notifications/             # notification channel + display helper
├── navigation/                # route definitions
└── di/                        # ServiceLocator wiring
```

## Building

```powershell
# Debug APK
.\gradlew.bat assembleDebug

# Unit tests
.\gradlew.bat testDebugUnitTest

# Install on a connected device/emulator
.\gradlew.bat installDebug
```

> Note: local JVM heap for Gradle is set in `gradle.properties`
> (`org.gradle.jvmargs=-Xmx4g`). Lower it if the machine is short on memory.

## Do not commit

- `local.properties` (B2 keys — listed in `.gitignore`)
- Real `app/google-services.json` is optional to commit per Firebase docs, but keep
  server-side secrets (Turso auth tokens, FCM server keys) out of the repo — put them
  in `local.properties` or your CI secret store.
