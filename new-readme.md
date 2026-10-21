# PashuRakshak

> **Livestock disease early-warning, veterinary response, and government oversight platform.**

PashuRakshak ("protector of livestock") is an end-to-end animal-health platform built around a simple loop:

**Farmer reports a sick animal → the system scores the risk and looks for an outbreak → a vet is assigned / notified → the vet verifies the visit with the animal's QR code → risky findings become government alerts.**

It ships as **four clients plus one shared backend**:

| Client / service | What it is | Who uses it |
|---|---|---|
| **Farmer Android app** (`app-farmer`) | Offline-first Android app for reporting sick animals, managing animals/vaccinations, viewing AI advice and alerts | Farmers |
| **Vet Android app** (`app-vet`) | Android app for a risk-sorted case queue, QR-verified field visits, and assessments | Veterinarians |
| **Vet Registration web app** (`vet-registration`) | Self-service vet onboarding: OTP → credential → application → approval status | Prospective vets |
| **Government Portal web app** (`gov-portal`) | District-scoped dashboard for vet-application review, reports, clusters, gov alerts, users | District Magistrates / gov officials |
| **Shared Backend** (`shared-backend`) | Express + Turso/libSQL multi-tenant API, auth, AI, clustering, push, audit | All clients |

> **Project status:** actively in progress. The main end-to-end journey is implemented and demoable. Some features are partial or intentionally unfinished (push notifications for the vet APK, full UI localization, web profile editing, rate limiting, etc.). A complete **feature status matrix** and **known limitations** section are included below so the README reflects reality, not just the intended design.

---

## Table of contents

1. [What PashuRakshak solves](#1-what-pashurakshak-solves)
2. [System at a glance](#2-system-at-a-glance)
3. [Feature overview by user](#3-feature-overview-by-user)
4. [Tech stack](#4-tech-stack)
5. [Repository map](#5-repository-map)
6. [Android apps in detail](#6-android-apps-in-detail)
7. [Shared backend in detail](#7-shared-backend-in-detail)
8. [Core algorithms and trust layer](#8-core-algorithms-and-trust-layer)
9. [Database and data model](#9-database-and-data-model)
10. [Web apps in detail](#10-web-apps-in-detail)
11. [Security model](#11-security-model)
12. [Configuration](#12-configuration)
13. [Running the project locally](#13-running-the-project-locally)
14. [Building and testing](#14-building-and-testing)
15. [End-to-end user journeys / demo script](#15-end-to-end-user-journeys--demo-script)
16. [Feature status matrix](#16-feature-status-matrix)
17. [Known limitations and work in progress](#17-known-limitations-and-work-in-progress)
18. [Roadmap, advanced features & product vision (planned)](#18-roadmap-advanced-features--product-vision-planned)
19. [Deployment](#19-deployment)
20. [Troubleshooting](#20-troubleshooting)
21. [Glossary](#21-glossary)
22. [Document notes](#22-document-notes)

---

## 1. What PashuRakshak solves

### The problem

- Livestock disease outbreaks are often detected late.
- Farmers may not know how serious a symptom is, or which vet to contact.
- Vets have no shared queue and no easy way to verify that a field visit actually happened.
- District authorities learn about outbreaks only after damage is done.
- Rural connectivity is unreliable, so a reporting app must work offline.

### The solution

PashuRakshak combines:

1. **Farmer reporting** — symptoms, free-text disease description, up to 5 photos, GPS location, village.
2. **AI-assisted triage** — a rule-based symptom score is blended with a Groq LLM risk analysis.
3. **Geo outbreak detection** — 3+ reports within 5 km over 7 days become a cluster alert.
4. **Vet auto-assignment** — the report is routed to a vet whose service areas match the village.
5. **QR-verified visits** — a vet can only confirm a visit by scanning the report's HMAC-signed QR code.
6. **Government escalation** — a vet marking a case *risky* creates a high-severity government alert.
7. **Offline-first sync** — reports, photos, animals, vaccinations, and alerts are queued and synced when connectivity returns.
8. **Trust layer** — hash-chained audit log, idempotent offline pushes, fraud signals, and QR confidence scoring.

### Actors

| Actor | Entry point | Main goals |
|---|---|---|
| **Farmer** | Farmer Android app | Register/login, add animals, report sickness, track reports, vaccinations, alerts, AI chat |
| **Veterinarian** | Vet Android app + vet registration web | Register/apply, get approved, triage cases, field-verify visits, record assessments |
| **District Magistrate** | Government portal web | Approve/reject vets, monitor reports/clusters, acknowledge gov alerts, view users |
| **Platform admin** | Backend admin API | Register apps, seed magistrates |
| **AI provider (Groq)** | Backend service | Risk analysis, preventive advisory, follow-up chat |

---

## 2. System at a glance

```text
                    ┌──────────────────────────┐
                    │   PashuRakshak Backend   │
                    │  Express + Turso/libSQL  │
                    │                          │
                    │  Auth / OTP              │
                    │  Reports / Animals       │
                    │  Cluster detection       │
                    │  Vet assignment          │
                    │  QR visit verification   │
                    │  AI (Groq)               │
                    │  Audit + idempotency     │
                    │  FCM push                │
                    └────────────┬─────────────┘
                                 │ HTTPS / REST
          ┌──────────────────────┼──────────────────────────┐
          │                      │                          │
 ┌────────┴────────┐    ┌────────┴─────────┐      ┌─────────┴─────────┐
 │ Farmer Android  │    │ Vet Android      │      │ Web apps          │
 │ com.pashu...app │    │ com.pashu...vet  │      │ vet-registration  │
 │                 │    │                  │      │ gov-portal        │
 │ report + photos │    │ queue + QR scan  │      │ onboarding + gov  │
 │ offline sync    │    │ assessments      │      │ dashboards        │
 └────────┬────────┘    └────────┬─────────┘      └─────────┬─────────┘
          │                      │                          │
          └──────────────────────┼──────────────────────────┘
                                 │
                    ┌────────────┴─────────────┐
                    │  External services       │
                    │  • Backblaze B2 (photos) │
                    │  • Groq (AI)             │
                    │  • Firebase FCM (push)   │
                    │  • Postal pincode API    │
                    │  • Gmail SMTP (OTP mail) │
                    └──────────────────────────┘
```

### The core loop

```text
Farmer                     Backend                         Vet
  │                          │                              │
  │  POST /reports           │                              │
  │  (symptoms, photos, GPS, │                              │
  │   village)               │                              │
  ├─────────────────────────▶│                              │
  │                          │ 1. upsert report             │
  │                          │ 2. detect 5km/7d cluster     │
  │                          │ 3. categorize risk (60/30)   │
  │                          │ 4. auto-assign vet           │
  │                          │ 5. generate AI advisory      │
  │                          │ 6. push/alert vet            │
  │  ◀── aiAdvisory ─────────┤                              │
  │                          ├─────────────────────────────▶│
  │                          │                              │ queue + alerts
  │                          │                              │
  │                          │  POST /visits (signed QR)    │
  │                          │◀─────────────────────────────┤
  │                          │ verify HMAC + QR match       │
  │                          │ status = examined            │
  │                          │ if risky → gov alert         │
  │  ◀── alert / sync ───────┤─────────────────────────────▶│
```

---

## 3. Feature overview by user

### 3.1 Farmer (Android)

| Feature | Description |
|---|---|
| **Registration** | Phone + email → 6-digit email OTP → name, 6-digit PIN, confirm PIN, animal count, village, pincode, language |
| **Login** | Phone + 6-digit PIN; session stored locally |
| **Forgot PIN** | Email OTP → set a new PIN |
| **Onboarding fallback** | Legacy accounts without a completed farmer profile collect name/email/animal count/village/pincode/language |
| **Farmer home** | Greeting, stat cards (Animals, Reports, Active, Examined), unread-alert badge, daily tip, quick actions |
| **Report sick animal** | Animal picker, symptom chips, optional custom disease description, up to 5 photos (camera/gallery), GPS + village, AI risk analysis, submit, post-submit AI advisory |
| **My reports** | Filter all / active (ongoing) / examined; risk colour stripe; status chip; symptoms; photo thumbnail; report QR; vet note; "Case verified" badge |
| **My animals** | Add/edit/delete animals; species emoji; health badge (healthy vs under observation); open QR passport |
| **Animal passport** | Signed QR code + animal details + vaccination summary + next-due/overdue indicator |
| **Vaccinations** | Add/edit/delete vaccination records; overdue styling; creates an alert on add |
| **Alerts** | Unread highlighting; tap to mark read; farmer-specific alerts |
| **AI Insight chat** | Markdown-rendered chat with the AI about a specific report; full history; streaming-style word-by-word reveal |
| **Profile** | View/edit name, email, village, pincode, animal count, language; phone read-only; sign out |
| **Offline support** | Reports and photos queue locally; sync banner shows pending count; 15-minute + reconnect sync |
| **Push notifications** | FCM data-only alerts; tapping opens the Alerts screen |

### 3.2 Veterinarian (Android + web)

| Feature | Description |
|---|---|
| **Self-registration (web)** | Phone + email → OTP → choose 6-digit PIN or password → professional application → status page |
| **Application** | Qualification, license number, experience, clinic, address, village, pincode; coverage areas selected from the postal pincode lookup; district resolved server-side |
| **Approval gate** | Vet app login is allowed only after the application is approved; web login can still show status |
| **Vet home** | In queue / High risk / Examined counts; quick actions: Case queue, Outbreak alerts, Profile |
| **Case queue** | Filters: Ongoing, High risk, Examined; sorted by risk score then newest |
| **Case detail** | Risk card, "Why this score?" breakdown, animal/location, QR scan, assessment (risky/moderate/mild), notes, GPS, submit visit |
| **QR verification** | Scans the report's signed QR; client verifies signature + id; server independently verifies HMAC + match |
| **Field check** | "Mark Examined" / "Sample Sent" flow; if no QR was scanned, the visit is recorded as **not QR verified** (matched = 0) |
| **Alerts** | Role-wide vet alerts (cluster / high-risk broadcast) |
| **Profile** | Shared profile screen; sign out |
| **Push notifications** | Designed, but the vet APK currently has no `google-services.json`, so vet FCM is inactive |

### 3.3 District Magistrate / Government (web)

| Feature | Description |
|---|---|
| **Passwordless login** | Registered magistrate email → 6-digit OTP → gov session |
| **Overview** | Platform counts: farmers, vets, applications by status, reports by status, active clusters, gov alerts, total alerts |
| **Vet applications** | District-scoped list; expand for full details; approve or reject with a review note |
| **Reports** | All symptom reports; status filter; animal, symptoms, village, status, risk |
| **Clusters** | Active outbreak clusters with report count, centre, radius, date window |
| **Gov alerts** | Alerts generated by risky vet visits; acknowledge them |
| **Users** | Farmers and vets with profile/application info |

### 3.4 Platform / shared capabilities

- Multi-tenant app-key system (`X-App-Key` per client app).
- Custom auth (no Firebase Auth): email OTP + PIN/password + HMAC session tokens.
- AI risk analysis and advisory (Groq).
- Cluster detection and vet auto-assignment.
- QR-based visit verification with confidence scoring.
- Hash-chained audit log.
- Idempotency keys for offline retries.
- Fraud signals: reused photo, report spike.
- Firestore-free stack: Turso/libSQL only.
- FCM push for cluster alerts (best effort).
- Pincode → district/area lookup via `api.postalpincode.in`.
- Render deployment configuration and `/health` endpoint.

---

## 4. Tech stack

### Android

| Piece | Choice / version |
|---|---|
| Language | Kotlin (bundled with AGP 9.4.1 built-in Kotlin) |
| Build | Gradle wrapper **9.6.0**, AGP **9.4.1** |
| UI | Jetpack Compose (BOM **2026.09.00**), Material 3 |
| Navigation | `androidx.navigation:navigation-compose` **2.8.5** |
| Architecture | MVVM + `StateFlow`, manual `ServiceLocator` (no Hilt) |
| Local database | Turso libSQL embedded (`tech.turso.libsql:libsql` **0.1.2**), raw SQL |
| Background work | WorkManager **2.10.1** |
| Location | `play-services-location` **21.4.0** |
| QR | ZXing Android Embedded **4.3.0** |
| Images | Coil **2.7.0** |
| Push | Firebase BOM **33.16.0**, `firebase-messaging` |
| Splash | `core-splashscreen` **1.0.1** |
| SDK | minSdk **24**, target/compileSdk **37** |
| Tests | JUnit **4.13.2** (B2 upload tests only) |

### Backend

| Piece | Choice |
|---|---|
| Runtime | Node.js ≥ 18, ES modules |
| HTTP | Express **4.21** |
| Database | Turso / libSQL via `@libsql/client` **0.15** |
| Auth | Custom: HMAC-SHA256 tokens, email OTP, scrypt credentials |
| AI | Groq SDK **1.6**, model `openai/gpt-oss-120b` |
| Push | `firebase-admin` **14.4** (FCM only) |
| Email | Nodemailer **6.9.16** (SMTP/Gmail app password) |
| CORS | `cors` **2.8.5** |
| Config | `dotenv` **16.4.5** |
| Deploy | Render (`render.yaml`, `Procfile`), health check `/health` |

### Web apps

| Piece | Choice / version |
|---|---|
| Framework | React **19.1**, React DOM **19.1** |
| Bundler | Vite **6.3.5** |
| React plugin | `@vitejs/plugin-react` **4.5.2** |
| Routing | None — stage-state machine in `App.jsx` |
| Auth storage | `localStorage` session token |
| Ports | vet-registration **5173** (Vite default), gov-portal **5174** (explicit) |

---

## 5. Repository map

```text
PasuRakshak/
├── core/                          # Shared Android library — all Kotlin code
│   ├── src/main/java/com/pashurakshak/app/
│   │   ├── MainActivity.kt        # Single activity, splash, notification routing
│   │   ├── PashuRakshakApplication.kt
│   │   ├── data/                  # Repositories (auth, animals, reports, alerts, ...)
│   │   │   ├── local/             # libSQL client, migrations, entities
│   │   │   ├── remote/            # Report push, AI risk, B2 upload, FCM, QR payloads
│   │   │   └── sync/              # SyncWorker, scheduler, reconnect, status
│   │   ├── di/                    # ServiceLocator
│   │   ├── navigation/            # Route definitions
│   │   ├── notifications/         # Channel + notification helper
│   │   └── ui/                    # Compose screens + ViewModels
│   │       ├── auth/              # Login, register, forgot PIN
│   │       ├── farmer/            # Reports, animals, vaccinations, alerts, QR passport
│   │       ├── vet/               # Queue, case detail, risk levels
│   │       ├── aiInsight/         # AI chat
│   │       ├── profile/           # Profile
│   │       ├── components/        # Bars, dialogs, photo, sync banner
│   │       ├── debug/             # B2 upload test screen
│   │       └── theme/
│   └── src/test/                  # B2 upload unit tests
│
├── app-farmer/                    # Android application, applicationId com.pashurakshak.app
│   ├── google-services.json       # Farmer Firebase config (FCM)
│   └── src/main/...               # Manifest + app name
│
├── app-vet/                       # Android application, applicationId com.pashurakshak.vet
│   └── src/main/...               # Manifest + app name (no google-services.json yet)
│
├── shared-backend/                # Express API
│   ├── src/app.js                 # Express setup, CORS, /health, /otp debug page
│   ├── src/routes.js              # Mounts /core, /auth, /gov, /pashu-health
│   ├── src/config/env.js
│   ├── src/core/                  # middleware, routes, services
│   ├── src/modules/pashu-health/  # domain routes + services
│   └── src/db/                    # client, migration runner, migrations 000–010
│
├── vet-registration/              # React/Vite vet onboarding web app
├── gov-portal/                    # React/Vite government dashboard
├── scripts/                       # Python APK / 16KB ELF alignment helpers
├── gradle/                        # Version catalog + wrapper
├── build.gradle.kts
├── settings.gradle.kts            # includes :core, :app-farmer, :app-vet
├── local.properties.example       # Android config template
├── PASHURAKSHAK_AUDIT.md          # Detailed technical audit
├── PROJECT_AUDIT.md               # Earlier project audit
└── new-readme.md                  # This document
```

---

## 6. Android apps in detail

### 6.1 Modules and role split

All Kotlin code lives in **`:core`** (an Android library). `app-farmer` and `app-vet` are thin application modules that provide:

- `applicationId`
- app label
- optional `google-services.json`
- ABI splits / release config
- 16KB ELF alignment tasks

**The install determines the role:**

```kotlin
// SessionManager.kt
appRole = if (context.packageName == "com.pashurakshak.vet") Role.VET else Role.FARMER
```

| Module | applicationId | Role | Firebase config |
|---|---|---|---|
| `app-farmer` | `com.pashurakshak.app` | Farmer | `app-farmer/google-services.json` present |
| `app-vet` | `com.pashurakshak.vet` | Vet | **missing** → no FCM currently |

The namespace values differ from the applicationIds to avoid `R`/`BuildConfig` clashes:

- `app-farmer` namespace: `com.pashurakshak.app.farmer`
- `app-vet` namespace: `com.pashurakshak.app.vet`
- `core` namespace: `com.pashurakshak.app`

### 6.2 Screens and navigation

Routes are defined in `core/.../navigation/Screen.kt`; the graph is in `ui/navigation/AppNavHost.kt`.

| Screen / route | Purpose |
|---|---|
| `login` | Phone + PIN/password login; links to register (farmer) and forgot PIN |
| `register` | Farmer-only 3-step registration: contact → OTP → details |
| `forgot_pin` | Email → OTP → new PIN |
| `onboarding` | Legacy farmer profile completion |
| `farmer_home` | Farmer dashboard |
| `my_reports?filter=all\|ongoing\|examined` | Farmer reports list |
| `report_sick_animal` | Sick-animal report form |
| `my_animals` | Animal list / add / edit / delete |
| `qr_passport/{animalId}` | Signed QR + vaccination passport |
| `vaccination_status` | Vaccination records |
| `alerts` | Farmer alerts |
| `profile` | Shared profile screen |
| `vet_home` | Vet dashboard |
| `vet_case_queue?filter=ongoing\|high_risk\|examined` | Vet case queue |
| `case_detail/{reportId}/{scanQrAuto}` | Vet case detail / QR verification |
| `vet_alerts` | Role-wide vet alerts |
| `ai_insight/{reportId}/{aiAdvisory}` | AI chat about a report |
| `b2_upload_test` | Debug-only B2 upload test screen (route always registered; tile only in DEBUG) |

**Start destination logic:**

```text
not logged in                                  → login
logged in + role = VET                         → vet_home
logged in + farmer + profileComplete            → farmer_home
logged in + farmer + profile incomplete         → onboarding
```

**Bottom navigation:**

- Farmers see a floating bottom bar: Home, Reports, Report, Animals, Alerts (and Profile entry points).
- Vets see: Home, Cases, Alerts, Profile.
- Bottom bars are hidden on auth, onboarding, QR passport, case detail, AI insight, and debug routes.
- A notification tap sets `destination=alerts`; the nav graph routes to farmer Alerts or vet Alerts.

### 6.3 Authentication flows (Android)

Uses the **shared backend custom auth** — no Firebase Auth.

#### Farmer login

1. `POST /api/v1/auth/farmer/check` (optional phone-exists check).
2. `POST /api/v1/auth/farmer/login` with `{ phone, pin }`.
3. Backend verifies scrypt hash and returns `{ token, user, profile }`.
4. App stores session in `SharedPreferences`.
5. App registers the FCM device token with `POST /api/v1/core/devices` (silent no-op if Firebase is not configured).
6. App inserts/updates local `farmers`/profile cache.

#### Farmer registration

1. Phone + email.
2. `POST /auth/otp/send` with purpose `farmer_register`.
3. `POST /auth/otp/verify` → `verifyToken` (10 minutes).
4. Details + 6-digit PIN.
5. `POST /auth/farmer/register` with `verifyToken`, phone, PIN, name, language, animal count, village, pincode.
6. Session established; user goes to farmer home.

#### Forgot PIN

1. Email → OTP (`farmer_reset_pin` or `vet_reset_pin`).
2. `POST /auth/farmer/reset-pin` or `POST /auth/vet/reset-pin` with the verify token and new PIN.

#### Vet login

1. `POST /auth/vet/login` with `{ phone, credential, client: 'app' }`.
2. Backend requires `vet_applications.status = 'approved'` for the app client.
3. If not approved: `403 { error: 'not_approved', applicationStatus, reviewNote }`.
4. Web login (`client: 'web'`) always works so the applicant can see status.

### 6.4 Farmer features

#### Farmer home

- Greeting with name.
- Stat cards:
  - **Animals** — count of owned animals.
  - **Reports** — all reports by this farmer.
  - **Active** — reports in `reported` or `vet_assigned`.
  - **Examined** — `examined`, `sample_sent`, `confirmed`, or `resolved`.
- Vaccinations due soon count.
- Unread alert count.
- Rotating daily tip.
- Quick actions:
  - Report sick animal
  - Vaccination
  - My reports
  - My animals
- Debug B2 upload tile only in debug builds.
- **Local-first**: reads local DB immediately, then calls `RemoteSync.pullAll()` and repaints.

#### Report a sick animal

`ReportSickAnimalScreen` + `ReportSickAnimalViewModel`.

Inputs:

- Animal dropdown (owned animals).
- Symptom chips:
  - Fever (20 pts)
  - Cough (0)
  - Not Eating (0)
  - Less Milk (15)
  - Swelling (0)
  - Mouth Problem (0)
  - Other (0; requires custom text)
- Free-text disease description.
- Up to **5 photos**:
  - Camera via `FileProvider` + `TakePicture`
  - Gallery with bounded decode to ≤2048 px JPEG quality 85
- Village (prefilled from farmer profile).
- Latitude/longitude:
  - Manual entry
  - "Use current location" via Play Services Location
- **AI analysis** button.
- Risk preview.

**Risk scoring:**

```text
ruleBasedScore = sum(symptom points) + 10 × (number_of_symptoms − 1)
finalScore     = AI score available ? (ruleBasedScore + aiScore) / 2 : ruleBasedScore
```

The AI call is `POST /api/v1/pashu-health/reports/analyze-risk`; the backend returns `{ score, analysis, likelyCondition, severity }`. If AI is unavailable, the app shows "AI analysis unavailable, using rule-based score" and continues.

On submit:

1. Ensure AI analysis has run (reuse cached result if any).
2. Build a `SymptomReport`:
   - `status = reported`
   - `synced = false`
   - `qrCodeId = QR-<UUID uppercase>`
   - symptoms, breakdown, disease description, village, coordinates, photos
3. Insert locally.
4. Insert a farmer alert ("Report submitted...").
5. If score ≥ 60, insert a local vet alert.
6. Push immediately via `ReportPushApi`.
7. On success: show `aiAdvisory` dialog (if returned).
8. On failure: leave unsynced and trigger `SyncScheduler.triggerNow`.
9. The submitted report card becomes available in My Reports.

Photos are stored as newline-separated paths/URLs in the same DB column (`photo_local_path`, `photo_remote_url`), index-aligned.

#### My reports

- Filter: all / ongoing / examined.
- Each card shows:
  - Animal name & species
  - Risk stripe colour (green/orange/red)
  - Status chip
  - Symptoms
  - Photo thumbnail
  - Report QR button
- If the vet has visited:
  - QR button is hidden.
  - "Case verified" pill appears if `qrVerified = true`.
  - "Examined (not QR verified)" appears if the vet marked it without a QR match.
  - Vet note is shown as `Vet note: ...`.
- Tapping the card opens the AI Insight chat for that report.

#### My animals

- List with species emoji and health badge:
  - **Healthy** if no reports or latest status is `resolved`.
  - **Under observation** otherwise.
- Add/edit/delete dialogs.
- New animals get `qrCodeId = QR-<UUID uppercase>`.
- Changes push through `RemoteSync.pushAnimal` / `pushAnimalDelete`.
- Tapping an animal opens the QR passport.

#### Animal passport

- Gradient header with species emoji.
- Animal name, species, QR code ID.
- Signed QR bitmap generated with ZXing `BarcodeEncoder`.
  - Payload: `"<id>|<timestampMillis>|<hmacHex>"`
  - HMAC-SHA256 keyed by `BuildConfig.APP_API_KEY`.
- Vaccination summary.
- Next due date and overdue state.

#### Vaccinations

- Add/edit/delete records: vaccine name, date given, next due.
- Overdue styling.
- Creates a local alert when a vaccination is added.
- Pushes changes to the backend.
- Pulls remote records during sync.

#### Alerts

- Farmer-specific alerts (`recipientRole = farmer`, `recipientId = farmer ID`).
- Unread highlight; tap marks read locally and syncs.
- Vet alerts screen is role-wide (`recipientRole = vet`, `recipientId = null`).

#### Profile

- Name, email, village, pincode, animal count, language editable.
- Phone read-only.
- Sign out clears the local session.
- Calls `GET /core/users/me`, `GET /pashu-health/farmer-profiles/me`, `PUT /core/users/me`, and `POST /pashu-health/farmer-profiles`.

#### AI Insight chat

- Opens for a report.
- Loads existing history with `GET /reports/{id}/chat`.
- Sends messages with `POST /reports/{id}/chat`.
- Renders Markdown.
- Reveals the AI reply word-by-word for a chat-like feel.
- Full history is stored server-side; only the last 20 messages are sent to the model.

### 6.5 Vet features

#### Vet home

- Stat cards:
  - **In queue** — `reported` or `vet_assigned`.
  - **High risk** — ongoing AND risk ≥ 60.
  - **Examined** — any status beyond ongoing.
- Unread vet alerts.
- Quick actions:
  - Case queue
  - Outbreak alerts
  - Profile

#### Case queue

- Filters:
  - **ONGOING** — `reported` / `vet_assigned`
  - **EXAMINED** — `examined` / `sample_sent` / `confirmed` / `resolved`
  - **HIGH_RISK** — ongoing AND risk level HIGH
- Sorted by risk score descending, then newest.
- Each row shows animal, farmer/village, symptoms, risk, status.
- "Scan QR" opens the case detail with the scanner auto-launched.
- The queue's direct "mark examined" action is intentionally blocked:
  > "Scan the report QR first before marking a case as examined."
- The vet must open the case and verify.

#### Case detail

- Risk card with score and level.
- "Why this score?" breakdown from the stored risk breakdown.
- Animal and location details.
- QR scan button.
- Assessment section (only after QR verification):
  - `risky`
  - `moderate`
  - `mild`
- Assessment notes (required to submit a verified visit).
- Latitude/longitude (manual or fetched).
- **Submit verified visit** (`POST /pashu-health/visits`):
  - Requires QR verified + assessment + non-blank notes + valid lat/long.
  - Sends `Idempotency-Key`.
  - Server checks assignment, HMAC signature, QR match, GPS distance, timing.
  - On success:
    - Report becomes `examined`.
    - `vet_assessment`, `assigned_vet_id`, `qr_verified`, `verified_at`, `verified_by` are set.
    - Local visit verification row is inserted.
    - Farmer alert is inserted.
    - Sync is triggered.
  - On `409 qr_mismatch`: nothing is changed; the app shows a friendly QR error.
- **Mark Examined / Sample Sent** field-check flow:
  - Available even without a QR, but recorded honestly as **not QR verified** (local `matched = 0`).
  - Can mark `sample_sent` instead of `examined`.
  - Persists `vetAssessment` and a local visit row.
  - Triggers sync and creates a farmer alert.

### 6.6 Offline-first sync engine

**Local DB is the source of truth** for the app. Remote sync is REST-based (no Turso replication).

#### What is queued

- Reports with `synced = 0`.
- Reports with local photos but no remote URL.
- Animals / vaccinations / alerts are pushed best-effort each cycle.
- Remote data is pulled back into the local DB.

#### SyncWorker cycle

1. Upload every report photo that has a local file but no B2 URL.
2. If a just-uploaded report was already synced, re-push it so the server stores the photo URL.
3. Push every unsynced report via `ReportPushApi`.
4. On success set `synced = 1`; on failure leave it for the next cycle.
5. Push animals/vaccinations/alerts and pull remote records (`RemoteSync.syncAll()`).
6. Always return `Result.success()` — failures are retried next cycle, not by WorkManager backoff.

#### Triggers

- App start: immediate one-shot + periodic schedule.
- Periodic: every 15 minutes, constrained to `NetworkType.CONNECTED`.
- On connectivity return: `NetworkReconnectObserver`.
- After a visit submit / report push failure.

#### Pull behavior

- Animals are pulled first (FK order), then vaccinations/reports/alerts in parallel.
- Pulls are coalesced: a 4-second minimum interval and a mutex prevent network storms.
- `ReportRepository.upsertFromRemote` protects local unsynced edits:
  - If the local row has `synced = 0`, a stale remote row does **not** overwrite local status/assessment.
  - Only `photoRemoteUrl` (and server-authoritative QR verification fields) are merged.
- The `SyncStatusBanner` shows "N reports pending sync" / "Syncing…" and refreshes the pending count every 30 seconds.

### 6.7 Photo pipeline

1. Capture or gallery selection.
2. Downscale to max 2048 px, JPEG quality 85, stored in `filesDir/photos`.
3. Up to 5 photos per report, newline-separated.
4. `B2UploadService` uses the Backblaze B2 native API:
   - `b2_authorize_account`
   - `b2_list_buckets`
   - `b2_get_upload_url`
   - upload to `reports/{reportId}/{filename}` with SHA-1
5. Retry:
   - 3 attempts with exponential backoff (1s / 2s / 4s).
   - Immediate failure on B2 HTTP errors.
   - Offline / missing file → `Skipped`, retried later.
6. Private bucket display uses a cached `b2_get_download_authorization` token (12 hours).
7. Photo URLs are written back to the local report and pushed to the server.
8. If B2 credentials are missing, upload fails gracefully with a message.

### 6.8 Push notifications

- `PushMessagingService` handles **data-only** FCM messages `{title, body}`.
- Notification channel: `alerts` (defined in manifest metadata and `AppNotifications`).
- Tapping the notification opens the Alerts screen for the current role.
- Android 13+ asks for `POST_NOTIFICATIONS`.
- Device token registration: `POST /api/v1/core/devices` with `{ role, fcmToken }`.
- Firebase is optional at build time:
  - The Google Services plugin is applied only if `google-services.json` exists.
  - Farmer app has it; vet app currently does not.
- Backend cluster detection pushes to all `device_tokens` rows with `role = 'vet'` for the app.

### 6.9 QR identity and verification

There are **two QR concepts**:

1. **Animal passport QR** — shown by the farmer for an animal (`Animal.qrCodeId = QR-<UUID>`).
2. **Report QR** — each symptom report has its own `qrCodeId` (default `QR-<UUID uppercase>`), shown on the report card.

Both are displayed as **HMAC-signed payloads**:

```text
<id>|<timestampMillis>|<hmacHex>
```

- HMAC-SHA256 keyed by the app API key.
- Android: `data/remote/QrPayloads.kt`.
- Backend: `services/qrSign.js`.

**Vet scan flow:**

1. ZXing `CaptureActivity` scans the QR.
2. Android verifies the signature and extracts the embedded id.
3. Android compares it to the report's `qrCodeId` (or the animal's `qrCodeId` for legacy rows).
4. If valid, the vet can fill the assessment and submit.
5. Server independently verifies the HMAC and compares the id to the report QR (animal fallback).
6. On mismatch: `409 qr_mismatch`; no status change.
7. On match: visit verified, report `examined`, QR-verification truth fields written.

### 6.10 Risk scoring

- Client rule-based score: symptom point table + 10 per extra symptom.
- Optional AI score: `/reports/analyze-risk`.
- Final submitted score: average of rule-based and AI score when available.
- Client risk levels: HIGH ≥ 60, MEDIUM ≥ 30, LOW < 30.
- Server categorization: HIGH if ≥ 60 **or** in a cluster; MID if ≥ 30; LOW otherwise.
- Thresholds are currently documented as placeholders ("refined server-side later").

### 6.11 AI advisory and chat (Android)

- **Post-submit advisory**: the backend returns `aiAdvisory` in the report POST response; the app shows it in a dialog.
- **AI Insight chat**: per-report conversation; full history stored server-side; last 20 messages sent to the model; graceful fallback when AI is unavailable.
- Chat supports Markdown and word-by-word reveal.
- AI language follows the farmer's `preferredLanguage` (`hi` / `en`).

### 6.12 Local database

Embedded libSQL file:
- File: `pashurakshak.db` in app storage.
- Created on first launch.
- Mutex-serialized.
- `PRAGMA foreign_keys = ON`.
- Migrations tracked with `PRAGMA user_version`.

Local tables (migrations 1–5):

| Table | Purpose |
|---|---|
| `animals` | Farmer animals, owner id, species, name, QR id |
| `vaccinations` | Animal vaccinations, date given, next due |
| `symptom_reports` | Reports, symptoms, photos, GPS, risk, status, sync state, vet fields, QR verification fields |
| `vets` | Legacy/local vet cache |
| `alerts` | Farmer/vet alerts, read state |
| `pashu_farmer_profiles` | Farmer profile cache |
| `pashu_visit_verifications` | Local visit proof rows (matched, assessment, coordinates) |
| `farmers` | Local farmer identity cache |

Report statuses:

```text
reported → vet_assigned → examined → sample_sent → confirmed → resolved
```

### 6.13 Language

- Language selector: Hindi (`hi`) / English (`en`).
- Default: `hi`.
- Stored in session and sent as `preferredLanguage`.
- Used by the backend AI to choose Hindi or English.
- **Important:** there is no Android resource localization yet. UI strings are hardcoded in Kotlin; the toggle does **not** translate the screens. It only affects AI responses.

### 6.14 Debug tools

- `ui/debug/B2UploadTestScreen.kt`:
  - Lists reports with pending photo uploads.
  - Upload all / upload one.
  - Shows whether B2 credentials are configured.
- The route is always registered; the home tile is shown only in `BuildConfig.DEBUG`.
- The backend also has a `/otp` debug page (see security notes).

### 6.15 Android build details

- `app-farmer`:
  - ABI splits: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64` + universal APK.
  - Release: minify + resource shrinking enabled.
- `app-vet`:
  - No ABI splits.
  - Release: minify disabled.
- Both run 16KB ELF alignment tasks using Python scripts (`scripts/align-apk.py`, `scripts/align-elf-16k.py`).
- BuildConfig fields injected from root `local.properties`:
  - `B2_APPLICATION_KEY_ID`
  - `B2_APPLICATION_KEY`
  - `B2_BUCKET_NAME`
  - `API_BASE_URL`
  - `APP_API_KEY`
- Defaults fall back to the deployed Render backend and a registered app key (see security notes).

---

## 7. Shared backend in detail

### 7.1 Architecture

- Express 4 with ES modules.
- Mounted under `/api/v1`.
- Starts after running database migrations.
- `GET /health` → `{ "status": "ok" }`.
- CORS allow-list from `CORS_ALLOWED_ORIGINS`.
- JSON body limit: 2 MB.
- 404 handler and 500 error handler.
- No ORM: raw parameterized SQL through `@libsql/client`.
- Migrations tracked in the `migrations_applied` table.

### 7.2 Request authentication model

| Header | Purpose | Applied to |
|---|---|---|
| `X-App-Key` | Identifies the registered client app; sets `req.appId` and `req.appApiKey` | `/auth`, `/gov`, `/pashu-health` (router-level); `/core` routes enforce where noted |
| `Authorization: Bearer <token>` | User/magistrate session; sets `req.userId`, `req.magistrateId`, `req.role` | Protected business routes |
| `X-Admin-Secret` | Platform admin | `/core/apps`, magistrate admin routes |
| `Idempotency-Key` | Safe replay of offline pushes | `POST /reports`, `POST /visits` |

### 7.3 Auth and OTP

#### Credentials

- Stored as `scrypt:<salt>:<hash>`.
- Farmer PIN: exactly 6 digits.
- Vet credential: 6-digit PIN or password ≥ 6 characters.
- Timing-safe verification.

#### OTP

- 6 digits, random.
- Stored SHA-256-hashed with `sessionSecret:email:code`.
- TTL: 10 minutes.
- Max 5 verify attempts.
- Send limits: 1 per 60 seconds, 5 per hour per `(email, purpose)`.
- Delivery:
  - `MAIL_MODE=log` → printed to server console.
  - `MAIL_MODE=smtp` → Nodemailer/Gmail app password.
- Purposes: `vet_register`, `farmer_register`, `farmer_reset_pin`, `vet_reset_pin`, plus separate gov flow.

#### Tokens

- Stateless HMAC-SHA256 tokens.
- Session TTL: **30 days**.
- Session payload: `{ typ: 'session', sub, role, phone?, email?, district?, exp }`.
- Verify token TTL: **10 minutes**.
- Verify payload: `{ typ: 'verify', purpose, email, phone?, exp }`.
- There is **no server-side session revocation** yet.

#### Role checks

- `verifySession` sets `req.role`.
- `requireRole(...roles)` returns `403` on mismatch.
- Gov routes use `verifySession` + `requireRole('gov')`.
- Most pashu-health routes accept any authenticated role; vet-only fields on report updates are protected by explicit `req.role === 'vet'` checks.

### 7.4 API reference

Base URL: `/api/v1`

#### Core (`/core`)

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /core/apps` | admin | Register a client app; returns the API key **once** |
| `GET /core/apps` | admin | List apps (never returns keys) |
| `POST /core/devices` | app key + session | Upsert FCM device token by `(app_id, uid, role)` |
| `POST /core/users` | app key + session | Create/update user by phone |
| `GET /core/users/me` | app key + session | Current user or `404 { exists: false }` |
| `PUT /core/users/me` | app key + session | Partial update: name, email, preferredLanguage |
| `GET /core/pincode/:code` | public | Proxy postal pincode lookup; returns area/post-office list |
| `POST /core/admin/magistrates` | admin | Upsert magistrate by email |
| `GET /core/admin/magistrates` | admin | List magistrates |
| `DELETE /core/admin/magistrates/:id` | admin | Delete magistrate |

#### Auth (`/auth`, all app-key protected)

| Method & path | Purpose |
|---|---|
| `POST /auth/otp/send` | Send 6-digit OTP to email for a purpose |
| `POST /auth/otp/verify` | Verify OTP → 10-minute verify token |
| `POST /auth/farmer/check` | Check phone existence/credential state |
| `POST /auth/farmer/login` | Farmer phone + PIN login |
| `POST /auth/farmer/register` | Complete farmer registration with verify token |
| `POST /auth/farmer/reset-pin` | Reset farmer PIN |
| `POST /auth/vet/setup-pin` | Create vet account credential after OTP |
| `POST /auth/vet/login` | Vet login (`client: 'app'` or `'web'`) |
| `POST /auth/vet/reset-pin` | Reset vet PIN/password |
| `POST /auth/gov/request-otp` | Magistrate login OTP (same response whether account exists) |
| `POST /auth/gov/verify` | Magistrate OTP → gov session |

#### Government (`/gov`, app key + gov session)

| Method & path | Purpose |
|---|---|
| `GET /gov/overview` | Platform counts + district-scoped application counts |
| `GET /gov/vet-applications?status=pending\|approved\|rejected\|all` | District-scoped applications |
| `POST /gov/vet-applications/:id/approve` | Approve (optional note) |
| `POST /gov/vet-applications/:id/reject` | Reject (note) |
| `GET /gov/reports` | All reports joined with animal species/name/QR |
| `GET /gov/clusters` | Active clusters |
| `GET /gov/gov-alerts` | All gov alerts, newest first |
| `POST /gov/gov-alerts/:id/acknowledge` | Mark acknowledged |
| `GET /gov/users` | Farmers and vets with profiles/application status |

District scoping:
- `district = '*'`, `'all'`, empty, or `'every district'` → see all.
- Otherwise case-insensitive, trimmed match.
- Wrong-district review attempts return `403 wrong_district`.

#### Pashu-health (`/pashu-health`, app-key protected; session where noted)

| Method & path | Auth | Purpose |
|---|---|---|
| `POST /reports` | app key + session | Idempotent report upsert; runs cluster detection, categorization, assignment, AI advisory |
| `POST /reports/analyze-risk` | app key + session | AI disease analysis → score/analysis/condition/severity |
| `GET /reports` | app key | List reports, newest first |
| `GET /reports/:id` | app key | Single report |
| `POST /animals` | app key | Create/upsert animal |
| `GET /animals` | app key | List animals |
| `GET /animals/:id` | app key | Single animal |
| `PUT /animals/:id` | app key | Full update; duplicate QR → 409 |
| `DELETE /animals/:id` | app key | Delete animal |
| `POST /vaccinations` | app key | Create/upsert vaccination |
| `GET /vaccinations` | app key | List vaccinations |
| `GET /vaccinations/:id` | app key | Single vaccination |
| `DELETE /vaccinations/:id` | app key | Delete vaccination |
| `POST /alerts` | app key | Create/upsert alert |
| `GET /alerts` | app key | List alerts |
| `GET /alerts/:id` | app key | Single alert |
| `PUT /alerts/:id` | app key | Partial update (e.g., read) |
| `DELETE /alerts/:id` | app key | Delete alert |
| `GET /clusters` | app key | Active clusters |
| `POST /farmer-profiles` | app key + session | Upsert own farmer profile |
| `GET /farmer-profiles/me` | app key + session | Own farmer profile |
| `POST /vet-profiles` | app key + session | Upsert own vet profile |
| `GET /vet-profiles/me` | app key + session | Own vet profile |
| `POST /vet-applications` | app key + session | Submit/resubmit vet application |
| `GET /vet-applications/me` | app key + session | Own application |
| `GET /vets` | app key | List vets |
| `GET /vets/available?area=` | app key | Vets whose service areas match an area |
| `POST /reports/:id/chat` | app key + session | Send AI chat message |
| `GET /reports/:id/chat` | app key + session | Full stored chat history |
| `POST /visits` | app key + session | Verify a vet visit with signed QR |
| `GET /gov-alerts` | app key | All government alerts |

#### Report body (POST `/reports`)

Accepts camelCase and snake_case aliases. Important fields:

```json
{
  "id": "uuid",
  "animalId": "uuid",
  "farmerId": "users.id (defaults to session user)",
  "symptoms": "[\"Fever\",\"Cough\"]",
  "photoLocalPath": "",
  "photoRemoteUrl": "https://…",
  "latitude": 12.34,
  "longitude": 56.78,
  "riskScore": 45,
  "riskBreakdown": "{\"Fever\":20,\"multiple_symptoms\":10}",
  "status": "reported",
  "createdAt": 1790084141971,
  "village": "Rampur",
  "assignedVetId": null,
  "vetAssessment": null,
  "qrCodeId": "QR-…",
  "diseaseDescription": "…"
}
```

Notes:
- `symptoms` and `riskBreakdown` may be JSON strings or objects/arrays.
- `qr_verified`, `verified_at`, `verified_by` are **never accepted from the body**.
- Only a vet session may overwrite `status`, `assigned_vet_id`, or `vet_assessment` on an update.
- If the animal does not exist, the server creates a placeholder (`QR-PENDING-<animalId>`).

#### Visit body (POST `/visits`)

```json
{
  "reportId": "uuid",
  "scannedQrCodeId": "QR-…|timestamp|hmac",
  "latitude": 12.34,
  "longitude": 56.78,
  "assessment": "risky"
}
```

`assessment` must be `risky`, `moderate`, or `mild`.

### 7.5 Middleware

- **`verifyAppKey`** — validates `X-App-Key` against `apps.api_key`; sets `req.appId` and `req.appApiKey`; returns `401 missing_app_key` / `401 invalid_app_key`.
- **`verifySession`** — parses the Bearer HMAC token; sets session/user/magistrate/role; returns `401 missing_bearer_token` / `401 invalid_session_token`.
- **`requireRole(...)`** — returns `403 forbidden_role`.
- **`adminSecret`** — requires `X-Admin-Secret` to match `ADMIN_SECRET`; returns `401`.

### 7.6 Error handling, CORS, logging

- Invalid JSON → `400 invalid_json`.
- Unknown route → `404 not_found`.
- Unhandled error → `500 internal_error`.
- Most route handlers use try/catch and return structured JSON errors.
- CORS:
  - No `Origin` header → allowed.
  - `*` in allow-list → all browser origins.
  - Otherwise origin must be listed.
- Logging is `console.log/warn/error` only — no structured logs, request logger, metrics, or tracing.

---

## 8. Core algorithms and trust layer

### 8.1 Risk categorization

File: `shared-backend/src/modules/pashu-health/services/clusterDetection.js`

```text
category = high  if riskScore >= 60 OR report is part of a cluster
         = mid   if riskScore >= 30
         = low   otherwise
```

Stored in `pashu_symptom_reports.ai_risk_category`.

The client computes:
- Rule-based symptom points.
- An optional AI score.
- Final score = average of the two when AI is available.

### 8.2 Cluster detection

Constants:

```text
WINDOW_MS  = 7 days
BBOX_DEG   = 0.05 degrees
RADIUS_KM  = 5 km
MIN_REPORTS = 3
```

Algorithm (`detectClusterForReport`):

1. Skip if lat/lon are not finite.
2. SQL pre-filter: reports in the last 7 days, excluding the trigger, inside a ±0.05° bounding box.
3. Refine with Haversine distance ≤ 5 km.
4. Include the trigger; if fewer than 3 reports → not a cluster.
5. If clustered:
   - Build a deterministic message with rounded coordinates.
   - Deduplicate against an existing alert with the same message within the window.
   - Insert a `pashu_alerts` row (`recipient_role = 'vet'`, `recipient_id = 'all-vets'`).
   - Best-effort FCM push to every vet device token for the app.
   - Promote involved `reported` reports to `vet_assigned`.
6. Return `{ clustered, count, reportIds, alertId?, message, alertCreated }`.

Active clusters (`getActiveClusters`):

- Non-resolved reports from the last 7 days.
- Greedy single-linkage grouping: any member within 5 km extends the group.
- Groups with ≥ 3 reports become clusters.
- Returns report count/ids, centroid, radius, window, first/last report time, statuses.
- Runs synchronously inside the report request path.

### 8.3 Vet auto-assignment

Runs after risk categorization when the report has a village.

Query:

```sql
SELECT u.id
FROM users u
JOIN pashu_vet_profiles v ON u.id = v.user_id
LEFT JOIN vet_applications a ON a.user_id = u.id
WHERE u.role = 'vet'
  AND (a.status = 'approved' OR a.id IS NULL)
  AND v.service_areas LIKE '%<village>%'
LIMIT 1
```

Behavior:

- First matching row only; no `ORDER BY`.
- Substring match on the JSON text of `service_areas` (not tokenised).
- Approved application or no application row is accepted.
- On match:
  - Sets `assigned_vet_id`.
  - Writes an audit-log entry.
  - Inserts a targeted vet alert:
    `AI risk <category> — report <id> in <village> assigned to vet`.
- No match / no village:
  - Logs a warning, leaves `assigned_vet_id` null, and does not fail the report.

**Not implemented:** proximity/distance, caseload balancing, availability flags, deterministic ordering.

### 8.4 QR visit verification and confidence

File: `shared-backend/src/modules/pashu-health/routes/visits.js`

Steps:

1. Validate `reportId` and `assessment` (`risky` / `moderate` / `mild`).
2. Replay idempotent requests with the same `Idempotency-Key`.
3. Load the report + animal QR + assigned vet.
4. If assigned vet exists and does not match the session user → `403`.
5. Verify the scanned QR's HMAC signature with the app API key.
6. Extract the embedded id and compare with:
   - report `qr_code_id` first,
   - animal `qr_code_id` fallback for legacy rows.
7. Compute confidence:
   - Start at 100.
   - `−50` if QR did not match.
   - `−20` if signature invalid.
   - `−min(round(distanceKm × 10), 30)` based on GPS distance from the report.
   - `−15` if verified less than 60 seconds after reporting.
   - Clamp to 0–100.
8. Flags:
   - `qr_mismatch`
   - `invalid_qr_signature`
   - `gps_far_from_report`
   - `verified_too_quickly`
9. Insert a visit-verification row **even on mismatch**.
10. On mismatch:
    - Write an audit log (`visit_rejected_qr_mismatch`).
    - Return `409 { error: 'qr_mismatch' }`.
    - Do **not** change report status.
11. On match:
    - Set `vet_assessment`, `status = 'examined'`, `qr_verified = 1`, `verified_at`, `verified_by`.
    - Write an audit log (`visit_verified`).
    - If assessment is `risky`, insert a `pashu_gov_alerts` row with severity `high`:
      `Gov alert: <species> in <village> flagged as risky — field visit verified by vet`.
    - Return `{ success, visit: { id, reportId, matched, assessment, confidence, flagged } }`.

### 8.5 AI advisory

File: `shared-backend/src/modules/pashu-health/services/aiAdvisory.js`

- Provider: Groq.
- Model: `openai/gpt-oss-120b`.
- Temperature 0.7, max tokens 16,384, reasoning effort `low`.
- System prompt restricts advice to preventive/first-aid:
  - isolation, hydration, limiting herd contact, hygiene.
  - never prescribe medications/dosages/treatment protocols.
  - always close with "A vet will follow up shortly. This is not a diagnosis."
  - respond in Hindi if `preferred_language = 'hi'`, else English.
- After a report save:
  - Looks up the farmer's language.
  - Builds a case prompt from species, symptoms, risk category, village, score, breakdown.
  - Saves the response in `pashu_ai_responses`.
  - Seeds `pashu_ai_conversations` with the assistant reply.
  - Returns the advisory in the report POST response.
- On Groq failure: returns `null`; the report still saves.

### 8.6 AI risk analysis

File: `services/aiRiskScore.js`

- Same Groq model.
- Temperature 0.3, max tokens 16,384, reasoning effort `low`.
- Prompt asks for **only** compact JSON:

```json
{
  "score": 0,
  "analysis": "2–4 sentence plain-language analysis",
  "likely_condition": "short name or unknown",
  "severity": "low|medium|high"
}
```

- Parses the first JSON object from the response.
- Clamps score to 0–100.
- Derives severity from score if omitted.
- Returns `null` on failure; the route returns `503 ai_unavailable`.

### 8.7 AI chat

File: `services/aiAdvisory.js` + `routes/aiChat.js`

- `POST /reports/:id/chat`:
  - Requires a session.
  - Loads or creates the conversation for `(report_id, farmer_id)`.
  - Appends the user message to the **full** history.
  - Sends the system prompt + the **last 20 messages** to Groq.
  - Appends the assistant reply (or a fallback message) to the full history.
  - Persists and returns the reply.
- `GET /reports/:id/chat` returns the full stored history.
- No per-user rate limit; the 20-message window is the main context/cost control.

### 8.8 Hash-chained audit log

File: `services/auditLog.js`, table `audit_log`.

Every important action appends a row:

```text
hash = sha256(prevHash | actor | action | entityType | entityId | JSON(payload) | createdAt)
```

- First entry uses `GENESIS`.
- Tampering with an earlier row breaks the chain.
- Audit failures are logged but never fail the business action.
- Example actions: `report_upserted`, `vet_assigned`, `visit_verified`, `visit_rejected_qr_mismatch`.

### 8.9 Idempotency

File: `services/idempotency.js`, table `idempotency_keys`.

- Clients send `Idempotency-Key` on retryable POSTs (`/reports`, `/visits`).
- The first successful JSON response is cached per `(key, endpoint)`.
- Replays return the cached response instead of duplicating effects.
- Used by Android for offline-safe pushes.

### 8.10 Fraud signals and report protection

On report upsert:

- `photo_reused` — the same `photo_remote_url` already exists on a different report.
- `report_spike` — the farmer has ≥ 5 reports in the last 10 minutes.
- Flags are returned in the response and audit-logged; reports are **not hard-rejected**.
- Vet-controlled columns are protected:
  - Only a vet session may overwrite `status`, `assigned_vet_id`, or `vet_assessment` on an update.
  - `qr_verified`, `verified_at`, `verified_by` are only written by `visits.js`.

---

## 9. Database and data model

Engine: **Turso / libSQL** (SQLite dialect). All IDs are TEXT UUIDs; timestamps are INTEGER epoch milliseconds; booleans are INTEGER 0/1.

Migrations live in `shared-backend/src/db/migrations/` and are applied on server start or via `npm run migrate`. Applied versions are tracked in `migrations_applied`.

### 9.1 Migration history

| Version | File | Adds |
|---|---|---|
| 1 | `000_core.js` | `apps`, `device_tokens` |
| 2 | `001_pashu_health.js` | `pashu_animals`, `pashu_vaccinations`, `pashu_symptom_reports`, `pashu_vets`, `pashu_alerts`, `pashu_farmers` |
| 3 | `002_users.js` | `users` |
| 4 | `003_pashu_profiles.js` | `pashu_farmer_profiles`, `pashu_vet_profiles` |
| 5 | `004_report_ai_fields.js` | Report `assigned_vet_id`, `ai_risk_category`, `vet_assessment`, `village` |
| 6 | `005_ai_tables.js` | `pashu_ai_responses`, `pashu_ai_conversations` |
| 7 | `006_visits_and_gov_alerts.js` | `pashu_visit_verifications`, `pashu_gov_alerts` |
| 8 | `007_auth_and_vet_applications.js` | User PIN columns, `auth_otps`, `vet_applications`, `gov_magistrates` |
| 9 | `008_report_qr_disease.js` | Report `qr_code_id`, `disease_description` |
| 10 | `009_report_qr_verified.js` | Report `qr_verified`, `verified_at`, `verified_by` |
| 11 | `010_trust_layer.js` | `audit_log`, `idempotency_keys`, visit `confidence`, `flagged` |

### 9.2 Tables

| Table | Key columns / purpose |
|---|---|
| `apps` | Registered client apps; `api_key` used by `verifyAppKey` |
| `device_tokens` | FCM tokens keyed by `(app_id, uid, role)` |
| `users` | Core identity: phone, role (`farmer`/`vet`/`admin`), name, email, language, PIN hash/type, email verified |
| `pashu_farmer_profiles` | `user_id` PK/FK, animal count, village, pincode |
| `pashu_vet_profiles` | `user_id` PK/FK, pincode, service areas (JSON array) |
| `vet_applications` | Professional details, status (`pending`/`approved`/`rejected`), district/state, license, coverage areas, review note, reviewer, timestamps |
| `gov_magistrates` | Magistrate email, name, district (`*` allowed), state |
| `auth_otps` | Hashed OTPs, purpose, phone, attempts, expiry, consumed |
| `pashu_animals` | Owner farmer, species, name, unique QR code |
| `pashu_vaccinations` | Animal FK, vaccine name, date given, next due |
| `pashu_symptom_reports` | Report data: animal/farmer, symptoms JSON, photos, lat/lon, risk score/breakdown, status, synced, village, vet assignment/assessment, report QR, disease description, QR verification truth fields |
| `pashu_alerts` | In-app alerts by recipient role/id, message, read state |
| `pashu_visit_verifications` | Visit proof: report, vet, scanned QR, matched, GPS, timestamp, confidence, flagged |
| `pashu_gov_alerts` | Government alerts: report, severity (`high`/`medium`/`low`), message, acknowledged |
| `pashu_ai_responses` | AI advisory responses per report/farmer, model, timestamp |
| `pashu_ai_conversations` | Full chat history per report/farmer |
| `audit_log` | Hash-chained audit entries |
| `idempotency_keys` | Cached responses per `(key, endpoint)` |
| `pashu_farmers`, `pashu_vets` | Legacy/local cache shapes; live identity is `users` + profiles |

### 9.3 Relationships

```text
apps 1:N device_tokens
users 1:1 pashu_farmer_profiles
users 1:1 pashu_vet_profiles
users 1:1 vet_applications
pashu_animals 1:N pashu_vaccinations
pashu_animals 1:N pashu_symptom_reports
pashu_symptom_reports 1:N pashu_ai_responses
pashu_symptom_reports 1:N pashu_ai_conversations
pashu_symptom_reports 1:N pashu_visit_verifications
pashu_symptom_reports 1:N pashu_gov_alerts
pashu_symptom_reports.assigned_vet_id → users.id (logical)
device_tokens.uid → users.id (logical)
```

---

## 10. Web apps in detail

### 10.1 Vet Registration (`vet-registration/`)

**Purpose:** self-service vet onboarding against the shared backend.

**Flow / stage machine** (`src/App.jsx`):

```text
landing ── register ──▶ otp ──▶ set-credential ──▶ application ──▶ status
   │                     ▲
   ├── sign-in ──────────┼──▶ status
   │                     │
   └── forgot ──▶ otp(reset) ──▶ reset-credential ──▶ status/application
```

**Components:**

| Component | Role |
|---|---|
| `RegisterStart` | Phone (+91, 10 digits) + email; sends `vet_register` OTP |
| `OtpVerify` | 6-digit OTP entry; resend cooldown; verify token |
| `SetCredential` | Choose 6-digit PIN or password ≥ 6 chars; `POST /auth/vet/setup-pin` |
| `SignInForm` | Phone + PIN/password; `POST /auth/vet/login` with `client: 'web'` |
| `ForgotStart` | Email → `vet_reset_pin` OTP |
| `ResetCredential` | New PIN/password after OTP |
| `ApplicationForm` | Step 1 professional details; step 2 coverage areas from pincode; submit/resubmit |
| `StatusPage` | Pending/approved/rejected status, full profile, reviewer note, refresh, sign out |

**Application submission:**

```text
POST /api/v1/pashu-health/vet-applications
{
  fullName, qualification, licenseNumber, experienceYears?,
  clinicName?, address, village, pincode, serviceAreas: [...]
}
```

The server:
- Resolves district/state from the pincode via `api.postalpincode.in`.
- Upserts `pashu_vet_profiles`.
- Routes the application to the matching district magistrate.

**Status behavior:**

- `pending` → waiting for review.
- `approved` → can sign in to the Vet Android app.
- `rejected` → reviewer note shown; applicant can edit and resubmit.
- Returning vets can sign in with `client: 'web'` even while pending/rejected, to view status.

**Known gap:** no profile-edit UI. `updateMe()` exists in `api.js` but is never called.

### 10.2 Government Portal (`gov-portal/`)

**Purpose:** district-magistrate oversight dashboard.

**Login:** email → 6-digit OTP → gov session. Magistrates are seeded by a platform admin.

**Tabs:**

| Tab | What it does |
|---|---|
| **Overview** | Platform counts: farmers, vets, applications by status, reports total, active clusters, gov alerts, total alerts |
| **Vet Applications** | District-scoped list; status filters pending/approved/rejected/all; expand for license, address, coverage areas; approve/reject with review note |
| **Reports** | All symptom reports; status filters; date, animal, symptoms, village, status, risk |
| **Clusters** | Active clusters with report count, centre, radius, date window |
| **Gov Alerts** | Severity, report id, created time; acknowledge unacknowledged alerts |
| **Users** | Farmers and vets; toggle between roles; profile/application columns |

**District scoping:**

- Applications are filtered by the magistrate's district.
- `*` / `all` sees every district.
- Applications are routed to a district from the applicant's pincode.
- District changes are re-read from the DB on each request.

**API client:** `src/api.js`, token in `localStorage`, `X-App-Key` on every request.

---

## 11. Security model

### What is in place

- **App keys** on all module routes.
- **Session tokens** are HMAC-signed and stateless.
- **Role checks** for government routes and vet-only report fields.
- **OTP** hashed at rest, expiring, attempt-limited, resend-limited.
- **Credentials** hashed with scrypt and random salt.
- **Parameterized SQL** throughout.
- **CORS allow-list**.
- **QR payloads** are HMAC-signed, so ids cannot be guessed/forged.
- **Server-side QR match enforcement** on visits (409 on mismatch).
- **Hash-chained audit log**.
- **Idempotency keys** prevent duplicate offline pushes.
- **Fraud signals** for reused photos and report spikes.
- **Secrets** are read from `local.properties` / environment variables, not hardcoded in Kotlin logic.

### Known weaknesses / hardening needed

- Hardcoded fallback app keys and API URLs exist in:
  - `core/build.gradle.kts`
  - `vet-registration/src/api.js`
  - `gov-portal/src/api.js`
  Rotate these keys and move them fully to environment configuration.
- A populated `local.properties` on disk may contain live B2/API secrets (it is git-ignored, but treat it as sensitive; rotate if it has been shared).
- Session tokens are valid for 30 days and cannot be revoked server-side; there is no refresh-token flow.
- Vet approval revocation does not invalidate already-issued tokens.
- No global API rate limiting or login-attempt lockout (only OTP limits).
- The `/otp` debug page can list OTP codes; it is protected by `ADMIN_SECRET` unless `OTP_PAGE_OPEN=true`. Disable it in production (`OTP_PAGE=false`) and use `MAIL_MODE=smtp`.
- `GET /core/pincode/:code` is unauthenticated.
- `GET /pashu-health/gov-alerts` only requires the app key.
- Most pashu-health routes accept any authenticated role; only some fields are role-guarded.
- No security headers (e.g., Helmet).
- No structured logging/metrics/tracing.
- `shared-backend/.env` and local SQLite files are local dev artifacts and are git-ignored.

---

## 12. Configuration

### 12.1 Backend environment (`shared-backend/.env`)

| Variable | Required | Purpose |
|---|---|---|
| `TURSO_DATABASE_URL` | yes | `libsql://…` remote URL or `file:local.db` for local dev |
| `TURSO_AUTH_TOKEN` | yes | Turso auth token |
| `PORT` | yes | HTTP port |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated origins; `*` allows all |
| `ADMIN_SECRET` | yes | `X-Admin-Secret` for admin routes |
| `SESSION_SECRET` | recommended | HMAC signing secret; derived from `ADMIN_SECRET` if unset |
| `MAIL_MODE` | no | `log` (console) or `smtp` |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`, `SMTP_PASS`, `MAIL_FROM` | for SMTP | Gmail/SMTP email delivery |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | for FCM | One-line service-account JSON |
| `B2_APPLICATION_KEY_ID`, `B2_APPLICATION_KEY`, `B2_BUCKET_NAME` | for photo storage | Mirrors Android `local.properties` (the app uploads directly to B2) |
| `GROQ_API_KEY` | for AI | Advisory, chat, and risk analysis; graceful degradation without it |
| `OTP_PAGE` | no | Default `true`; set `false` to disable the debug page |
| `OTP_PAGE_OPEN` | no | Default `false`; `true` makes `/otp` public (dev only) |

The server refuses to start and lists all missing required variables.

### 12.2 Android `local.properties`

Copy `local.properties.example` → `local.properties` (git-ignored):

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
B2_APPLICATION_KEY_ID=
B2_APPLICATION_KEY=
B2_BUCKET_NAME=
API_BASE_URL=https://pashurakshak-z8lk.onrender.com
APP_API_KEY=
```

These are injected as `BuildConfig` fields.

### 12.3 Web app environment

Both web apps use:

```properties
VITE_API_BASE_URL=http://localhost:3000
VITE_APP_API_KEY=<apiKey from POST /api/v1/core/apps>
```

If unset, both fall back to a deployed backend URL and a hardcoded app key (see security notes).

### 12.4 Firebase

- Farmer app package: `com.pashurakshak.app`.
- Vet app package: `com.pashurakshak.vet`.
- Place `google-services.json` in **`app-farmer/`** and/or **`app-vet/`**.
- The Google Services plugin is applied only when the file exists.
- FCM messages should be **data-only**:

```json
{ "title": "New high-risk case", "body": "Cow 'Gauri' — risk score 70" }
```

- Register debug SHA-1/SHA-256 fingerprints for phone auth/debug builds if needed.

---

## 13. Running the project locally

### 13.1 Backend

```bash
cd shared-backend
npm install
cp .env.example .env
# fill TURSO_DATABASE_URL, TURSO_AUTH_TOKEN, PORT, CORS_ALLOWED_ORIGINS, ADMIN_SECRET
# optional: SESSION_SECRET, MAIL_*, FIREBASE_SERVICE_ACCOUNT_JSON, GROQ_API_KEY, B2_*
npm run migrate
npm start
```

Health check:

```bash
curl http://localhost:3000/health
# {"status":"ok"}
```

For a purely local database:

```properties
TURSO_DATABASE_URL=file:local.db
TURSO_AUTH_TOKEN=dev-token
```

### 13.2 Android

1. Open the repository root in Android Studio.
2. Create `local.properties` from `local.properties.example`.
3. Let Gradle sync.
4. Run:
   - `app-farmer` configuration for the farmer app.
   - `app-vet` configuration for the vet app.
5. Both build without external config; Firebase/B2 features degrade gracefully.

### 13.3 Vet registration web

```bash
cd vet-registration
npm install
cp .env.example .env
# set VITE_API_BASE_URL and VITE_APP_API_KEY
npm run dev
```

Open the Vite URL (default `http://localhost:5173`).

### 13.4 Government portal web

```bash
cd gov-portal
npm install
cp .env.example .env
# set VITE_API_BASE_URL and VITE_APP_API_KEY
npm run dev
```

Open `http://localhost:5174`.

### 13.5 Seed a magistrate

```bash
curl -X POST http://localhost:3000/api/v1/core/admin/magistrates \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET>" \
  -d '{"email":"dm@example.gov.in","name":"District Magistrate","district":"Jaipur","state":"Rajasthan"}'
```

Use `"district":"*"` to see all districts.

### 13.6 Register client apps

```bash
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET>" \
  -d '{"name":"PashuRakshak Android"}'
```

The response contains `apiKey` **once** — store it securely.

---

## 14. Building and testing

### Android

```powershell
# Debug APK (farmer and vet modules)
.\gradlew.bat assembleDebug

# Unit tests
.\gradlew.bat testDebugUnitTest

# Install on a connected device/emulator
.\gradlew.bat installDebug

# Debug signing fingerprints (for Firebase)
.\gradlew.bat signingReport
```

Release builds:

```powershell
.\gradlew.bat assembleRelease
```

The custom `alignDebugApk` / `alignReleaseApk` tasks run the 16KB ELF alignment scripts.

### Backend

```bash
cd shared-backend
npm start
npm run dev       # node --watch
npm run migrate
```

There is **no backend test suite, linter, or type checker** configured yet.

### Web apps

```bash
cd vet-registration
npm run build     # -> dist/
npm run preview

cd ../gov-portal
npm run build     # -> dist/
npm run preview
```

---

## 15. End-to-end user journeys / demo script

A complete happy-path demo:

1. **Backend up** — `GET /health` returns `{"status":"ok"}`.
2. **Register a vet (web)**:
   - Phone + email → OTP (read from server console or `/otp` debug page in dev).
   - Set a PIN.
   - Submit the application with a pincode.
   - Coverage areas load from the postal API.
3. **Approve the vet (gov portal)**:
   - Seed a magistrate.
   - Login with email OTP.
   - Open Vet Applications → approve the applicant with a note.
4. **Register/login a farmer (Android)**:
   - Phone + email → OTP → details + PIN.
   - Add an animal.
   - Open the animal passport QR.
5. **Report a sick animal**:
   - Choose the animal, symptoms, disease description.
   - Take 1–5 photos.
   - Use current location / enter village.
   - Run AI analysis (if `GROQ_API_KEY` is set).
   - Submit.
   - See the AI advisory dialog.
6. **Observe the backend pipeline**:
   - Report saved.
   - Cluster detection runs.
   - Risk category set.
   - Vet auto-assigned when the village matches a vet's service areas.
   - Vet alert created.
7. **Vet handles the case (Android)**:
   - Login (approved only).
   - Open Case Queue.
   - Open the case detail.
   - Scan the report/animal QR.
   - Choose assessment (risky/moderate/mild) + notes + GPS.
   - Submit the verified visit.
8. **Verify the outcomes**:
   - Farmer sees "Case verified" and the vet note.
   - Report status becomes `examined`.
   - If assessment was `risky`, a gov alert appears.
   - Gov portal → Gov Alerts → acknowledge.
   - Cluster detection can be demonstrated by posting 3 nearby reports within 7 days.
9. **Offline demo**:
   - Turn off connectivity.
   - Submit a report.
   - Observe "pending sync" banner.
   - Restore connectivity → WorkManager syncs photo + report.

---

## 16. Feature status matrix

Legend: **Working** = implemented end-to-end; **Partial** = implemented with known gaps/config dependency; **Not implemented** = planned/absent; **Dev-only** = debug utility.

| Feature | Status | Where |
|---|---|---|
| Farmer email OTP + PIN register/login/reset | Working | `shared-backend/src/core/routes/auth.js` |
| Vet OTP register + web login + approval gate | Working | `auth.js`, `vet-registration/` |
| Gov magistrate OTP login + district scoping | Working | `auth.js`, `gov.js`, `gov-portal/` |
| App-key protection on module routes | Working | `verifyAppKey.js` |
| HMAC session tokens (30 days) | Working | `sessionToken.js` |
| Offline report queue + 15-min/reconnect sync | Working | `SyncWorker.kt`, `SyncScheduler.kt` |
| B2 photo capture/upload + retries | Working (farmer APK) | `B2UploadService.kt` |
| Client rule-based risk score | Working (placeholder points) | `ReportSickAnimalViewModel.kt` |
| AI risk analysis endpoint | Working when Groq key set | `aiRiskScore.js`, `AiRiskApi.kt` |
| Server risk categorization (60/30 + cluster→high) | Working (rule-based) | `clusterDetection.js` |
| Cluster detection (7d / 5km / 3+, single-linkage) | Working | `clusterDetection.js` |
| Vet auto-assignment by village | Partial (substring match, no ordering/proximity) | `clusterDetection.js` |
| QR visit verification | Working (client + server HMAC + match) | `visits.js`, `QrPayloads.kt`, `qrSign.js` |
| QR confidence + flags | Working | `visits.js` |
| Mark examined without QR | Working (recorded as unverified) | `CaseDetailViewModel.kt` |
| Vet queue filters (ongoing/high risk/examined) | Working | `VetCaseQueueViewModel.kt` |
| AI advisory (Groq gpt-oss-120b) | Working with `GROQ_API_KEY` | `aiAdvisory.js` |
| AI chat (20-message context, full history) | Working with `GROQ_API_KEY` | `aiAdvisory.js`, `aiChat.js` |
| Push notifications to vet (cluster) | Partial (vet APK lacks FCM config) | `PushMessagingService.kt`, `push.js` |
| Gov alerts created on risky visit | Working | `visits.js` |
| Gov alert acknowledgement | Working | `gov.js`, `gov-portal/` |
| Vet application review + district scoping | Working | `gov.js` |
| Gov portal tabs | Working | `gov-portal/src/components/` |
| Vet registration web flow | Working (no profile edit) | `vet-registration/src/App.jsx` |
| Language toggle (hi/en) | Partial (AI language only; no UI localization) | `AppComponents.kt` |
| Hash-chained audit log | Working | `auditLog.js` |
| Idempotency keys for offline pushes | Working | `idempotency.js` |
| Fraud signals (photo reuse, report spike) | Working (surfaced/audit-logged) | `reports.js` |
| OTP debug page | Dev-only | `app.js` |
| Server-side QR match enforcement | Working | `visits.js` |
| Vet profile edit in web | Not implemented | — |
| Localized UI strings | Not implemented | — |
| Logout / token revocation | Not implemented | — |
| Global rate limiting / login lockout | Not implemented | — |
| Backend tests / CI / lint | Not implemented | — |
| Government push/SMS/email notifications | Not implemented | — |
| Lab confirmation workflow | Not implemented (status exists only) | — |
| Analytics / metrics / tracing | Not implemented | — |

---

## 17. Known limitations and work in progress

This section is intentionally explicit. The project is still evolving; the following items are **not fully working or are deliberately scoped out** in the current build.

### 17.1 Features that are partial or not working today

1. **Vet push notifications**
   - The backend can send cluster FCM pushes to vet devices.
   - The vet APK (`com.pashurakshak.vet`) does not have `google-services.json` and is not registered in Firebase.
   - As a result, vets currently receive in-app alerts on the next sync, not push notifications.

2. **UI localization**
   - The Hindi/English selector is stored and sent to the backend.
   - It changes the AI response language.
   - It does **not** translate Android UI strings; all strings are hardcoded in Kotlin.

3. **Vet profile editing on the web**
   - `updateMe()` exists in `vet-registration/src/api.js` but no UI calls it.

4. **Vet auto-assignment**
   - No proximity/distance scoring.
   - No caseload balancing.
   - No availability flag.
   - No deterministic ordering (`LIMIT 1` without `ORDER BY`).
   - Substring matching can produce false positives.

5. **Risk score**
   - The client symptom-point table is a placeholder.
   - The server only thresholds (60/30) and does not independently recompute the rule-based score.
   - The final score is a 50/50 average of rule-based and AI scores when AI is available.

6. **Backend hardening**
   - No global rate limiting or login lockout.
   - No account/token revocation.
   - No refresh tokens.
   - No Helmet/security headers.
   - No structured logging, metrics, or tracing.

7. **Government escalation**
   - Risky visits create gov alerts in the database and portal.
   - There is no automatic email/SMS/push to authorities, no SLA/escalation workflow, and no gov mobile app.

8. **Photo storage**
   - Uploads happen directly from the Android app to Backblaze B2.
   - The backend holds B2 credentials in its environment but has no server-side upload/ingest code.

9. **Lab/testing workflow**
   - Statuses `sample_sent`, `confirmed`, and `resolved` exist in the data model.
   - There is no dedicated lab UI/workflow beyond the vet field-check status option.

10. **Local visit rows**
    - Verified visits are also recorded server-side.
    - Local `pashu_visit_verifications` rows (including unverified field checks) are not uploaded as a separate sync entity; the report status/assessment is what syncs.

11. **The `/otp` debug page and `MAIL_MODE=log`**
    - Useful for development but must be disabled/secured in production.

12. **Hardcoded fallbacks**
    - App keys and API base URLs have hardcoded fallbacks in Android and web source.
    - These should be rotated and moved to required configuration.

### 17.2 Technical debt / scalability notes

- Cluster detection and active-cluster grouping run synchronously in the report request path and load candidate rows into memory (O(k²) grouping per group).
- No DB indexes dedicated to `symptom_reports(created_at, status)`, `latitude/longitude`, `ai_risk_category`, or `assigned_vet_id`.
- Chat sends the last 20 messages regardless of token size.
- `SyncWorker` always returns `Result.success()`, so persistent failures retry every 15 minutes rather than using WorkManager backoff.
- Multiple app keys exist across Android and web fallbacks; consolidate into per-client environment configuration.
- Some legacy tables (`pashu_farmers`, `pashu_vets`) remain as cache shapes.

---

## 18. Roadmap, advanced features & product vision (planned)

> **Status: not built yet.** Everything in this section is intended/planned work. It is documented so the design direction is explicit; the current implementation does **not** yet include these capabilities. Section 18.1 lists near-term hardening that follows directly from the known limitations above; sections 18.2–18.6 are the forward product vision.

### 18.1 Near-term hardening (from the current build)

1. **Firebase for the vet app** — add `app-vet/google-services.json`, register `com.pashurakshak.vet`, and verify device-token registration + cluster push.
2. **True localization** — move UI strings to Android resources / web translation files and switch locale from the language setting.
3. **Security hardening** — remove hardcoded fallback keys/URLs, rotate exposed keys and B2 credentials, add global rate limiting + login throttling, disable `/otp` in production, add Helmet + structured logging, and add token revocation or short-lived access + refresh tokens.
4. **Deterministic assignment** — see *smart assignment* in 18.4.
5. **Server-side risk model** — see *learning from vet outcomes* in 18.2.
6. **Testing & CI** — backend API tests (auth, reports, visits, clustering), Android instrumentation tests, and lint/typecheck scripts in CI.
7. **Government workflow** — see 18.5.
8. **Vet web profile editing** — wire `updateMe()` into a profile screen.
9. **Observability** — request IDs, structured logs, metrics and tracing; surface sync/upload failures to users.

### 18.2 Advanced intelligence

- **Photo-based lesion detection (multimodal).** A multimodal model pre-classifies uploaded photos — skin nodules, mouth lesions, eye discharge — and returns a confidence score. Below a configurable threshold the system always **defers to a vet**; the model never makes a definitive call on its own.
- **Learning from vet outcomes (closed loop).** Every verified visit is a labelled example. The vet's assessment is stored alongside the original symptoms and AI scores, and symptom weights are recalibrated from that data — replacing the placeholder symptom table with a model grounded in real outcomes.
- **Predictive outbreak forecasting.** Combine weather (temperature, humidity, rainfall), season, vaccination-coverage gaps and cluster history to forecast per-district risk a week or more ahead. Clustering is reactive; forecasting is preventive.
- **Retrieval-augmented advice (RAG).** Ground the AI chat and advisories in a curated veterinary knowledge base with citations. This reduces hallucinated advice — critical in a health context — and summarising older chat turns removes the fixed 20-message context window.
- **Voice input and read-aloud in regional languages.** Whisper (via Groq) for speech-to-text and text-to-speech, since many farmers are more comfortable speaking than typing.
- **Safety evaluation suite.** A fixed set of test cases asserting that advisories never recommend dosages or prescriptions and that every output includes the defer-to-vet line. Run in CI.

### 18.3 Farmer experience

- **Vet teleconsultation.** Video/audio call (WebRTC, or Jitsi for speed) for quick triage before a field visit.
- **Treatment and withdrawal tracking.** Record medication courses and track milk and meat withdrawal periods so animal products are not sold too early — a real food-safety feature.
- **Herd records and productivity.** Milk yield, breeding and weight trends alongside health, so the app is useful on normal days, not only during outbreaks.
- **Multi-channel reminders.** Vaccinations, follow-ups and deworming delivered by push, SMS and WhatsApp.
- **SMS / WhatsApp fallback.** Advisories and outbreak alerts must reach farmers with feature phones or unreliable data who never open the app.
- **Real localization.** UI strings in Android resources and web translation files, with more regional languages; **Bhashini** (India's government language platform) can assist translation.
- **Accessibility.** Large-text mode, icon-first navigation and high-contrast themes.
- **Community alerts.** Village-level visibility so farmers can see that a neighbour's animal is sick.
- **Insurance and scheme support.** Export a report as evidence for livestock-insurance claims.

### 18.4 Veterinarian workflow

- **Smart assignment.** Score vets by distance, current caseload, species specialty and availability with a deterministic ordering — replacing the current substring match.
- **SLA timers and escalation.** If a case is not visited within a set time, reassign it and notify the district.
- **Route planning.** Order a vet's day by travel time (OSRM or Google Routes).
- **Structured clinical forms** per species and disease, with photos attached to each finding.
- **Digital prescriptions** with a verified council registration number and an e-signature.
- **Inventory and cold chain.** Track vaccine and drug stock, with temperature logs for vaccine storage.
- **Lab integration.** Barcode labels for samples, chain-of-custody tracking and webhook-based ingestion of lab results — replacing the current status-only model.
- **Vet performance analytics.** Response time, verification rate and farmer feedback.

### 18.5 Government and oversight

- **Geospatial heatmap with H3 hexagonal indexing.** Replaces the current in-memory O(k²) clustering and gives officials a live map.
- **Quarantine zones** as drawn polygons, with movement restrictions and alerts when animals cross a boundary.
- **Animal movement e-permits** with a QR code checked at checkpoints — controlling movement is central to containing disease.
- **Vaccination campaign management.** Targets, coverage progress by village and gap analysis.
- **Automated official reports** as PDFs in the format state departments and international reporting bodies expect.
- **Multi-tier roles** — state, district and block — with permissions that cascade downward.
- **Escalation workflow** for high-severity alerts, with acknowledgement deadlines and an audit trail of who acted and when.
- **Official notifications** by SMS, email and WhatsApp Business for high-severity events.

### 18.6 Integrations and hardware (big bets)

- **National livestock ID linkage.** India's livestock mission uses unique animal IDs; linking to them avoids duplicate records and simplifies government reporting.
- **WhatsApp bot** for reporting and advisories — given WhatsApp's rural reach, potentially the highest-impact single integration.
- **Maps and geocoding** for reverse-geocoding villages and validating GPS input.

### 18.7 Suggested delivery phases

| Phase | Theme | Representative features |
|---|---|---|
| **P1** | Harden & localize | Vet push, UI localization, rate limiting, CI, server-side risk model |
| **P2** | Smarter triage | Smart assignment, SLA/escalation, structured forms, RAG advisories |
| **P3** | Farmer reach | SMS/WhatsApp fallback, voice, teleconsultation, accessibility, insurance export |
| **P4** | Predictive & spatial | Forecasting, H3 heatmap, quarantine zones, movement e-permits |
| **P5** | Ecosystem & hardware | National ID linkage, WhatsApp bot, lab integration, IoT sensors |

Every phase preserves the **defer-to-vet** safety guarantee and the immutable audit trail.

---

## 19. Deployment

### Backend on Render

- `shared-backend/render.yaml` defines a Node web service.
- Build: `npm install`.
- Start: `npm start`.
- Health check: `/health`.
- Migrations run automatically at boot.
- Required env vars are set in the Render dashboard (`sync: false`).
- `Procfile`: `web: npm start`.

### Web apps

- Both are Vite static SPAs.
- Build with `npm run build` → `dist/`.
- Deploy `dist/` to Vercel/Netlify/Render static hosting.
- Set `VITE_API_BASE_URL` and `VITE_APP_API_KEY`.
- Add deployed origins to backend `CORS_ALLOWED_ORIGINS`.

### Android

- Build APKs with `assembleRelease`.
- Farmer app produces per-ABI APKs + a universal APK.
- The vet app produces a single APK.
- Both run the 16KB ELF alignment task after packaging.
- Distribute via Play Store/internal distribution/MDM as appropriate.

### Database

- Turso remote database for production.
- Keep `TURSO_AUTH_TOKEN` secret.
- Run migrations on deploy (automatic on server start).
- Maintain backups/seed data before demos.

---

## 20. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| App builds but login says setup required | Firebase config missing (for older builds) or backend unreachable | Check `API_BASE_URL`, network, and `app-farmer/google-services.json`; current auth is backend-based |
| OTP email not received | `MAIL_MODE=log` or SMTP not configured | Read OTP from server console or `/otp?key=<ADMIN_SECRET>`; configure SMTP for real email |
| `401 invalid_app_key` | Wrong/missing `X-App-Key` | Set `APP_API_KEY` / `VITE_APP_API_KEY` to a key from `POST /core/apps` |
| `403 not_approved` on vet login | Vet application not approved | Approve it in the gov portal |
| No vet push notifications | Vet APK lacks `google-services.json` | Add Firebase config for `com.pashurakshak.vet` |
| Photo upload says credentials missing | `B2_*` not set | Fill `local.properties` (and backend `.env` for parity) |
| Photos upload but do not display | Private B2 bucket | B2 download-auth token is used; verify bucket/key permissions |
| Report stuck "pending sync" | No network / backend error / expired session | Check sync banner, backend logs, and re-login |
| "QR code did not match" | Scanned wrong QR / stale report QR / signature invalid | Regenerate/open the correct report QR; ensure the same app key signed and verified |
| `409 qr_mismatch` | Server rejected the QR | Scan the report's QR (not another animal's) |
| Vet sees "Scan the report QR first" | Direct queue marking is blocked | Open the case, scan the QR, then submit |
| `aiAdvisory` is null | `GROQ_API_KEY` missing / Groq failure | Set the key; AI degrades gracefully |
| Gov portal shows no applications | Magistrate district mismatch | Use the applicant's district or set magistrate district to `*` |
| CORS error in web app | Origin not in `CORS_ALLOWED_ORIGINS` | Add the web origin or use `*` for development |
| Gradle build fails on memory | JVM heap | Adjust `org.gradle.jvmargs` in `gradle.properties` |

---

## 21. Glossary

| Term | Meaning |
|---|---|
| **Farmer** | Livestock owner using the farmer Android app |
| **Vet** | Veterinarian using the vet Android app and/or registration web |
| **Magistrate** | District government official using the gov portal |
| **Report** | A symptom report for a sick animal |
| **Case** | A report as viewed by a vet |
| **Cluster** | 3+ reports within 5 km over 7 days |
| **Risk score** | 0–100 severity estimate (rule-based + optional AI) |
| **Risk category** | `low` / `mid` / `high` (server categorization) |
| **Visit verification** | A vet field visit confirmed by scanning the report QR |
| **Gov alert** | High-severity alert created by a risky verified visit |
| **App key** | Per-client key sent as `X-App-Key` |
| **Session token** | HMAC-signed Bearer token identifying a user/magistrate |
| **Verify token** | Short-lived token proving OTP verification during registration/reset |
| **Idempotency key** | Client-supplied key that makes retried POSTs safe |
| **Signed QR** | `<id>|<timestamp>|<HMAC-SHA256>` payload |
| **Audit chain** | Hash-chained log where each entry includes the previous hash |
| **B2** | Backblaze B2 object storage for report photos |
| **FCM** | Firebase Cloud Messaging for push notifications |
| **Groq** | LLM inference provider used for AI risk/advisory/chat |
| **libSQL / Turso** | SQLite-compatible database used locally and remotely |

---

## 22. Document notes

- This document describes the **current repository state** and is intended to be the complete, presentation-friendly reference for the whole project.
- It supersedes the older root `README.md` for architecture/feature details. Other audits in the repo (`PASHURAKSHAK_AUDIT.md`, `PROJECT_AUDIT.md`) contain additional deep technical notes, but some statements may be stale as the code evolves (for example, the AI risk endpoint now exists and server-side QR match enforcement is implemented).
- Some details are intentionally marked **partial** or **not implemented** so the README reflects the in-progress nature of the project.
- No secret values are reproduced in this document. Rotate any credentials that may have been shared or committed as fallback values.
- For the presentation, the most compelling demo thread is:
  **farmer reports → AI score + advisory → cluster/assignment → vet QR-verified visit → gov alert + oversight**.
