# shared-backend

Multi-app shared backend foundation: Express (ES modules) + Turso/libSQL.

- **Core:** app-key system (`/api/v1/core/*`) — app registration, admin routes,
  users, pincode lookup, device tokens, magistrate seeding.
- **Auth (`/api/v1/auth/*`):** custom email-OTP + PIN/password sessions —
  **no Firebase Auth**. Firebase is used only for FCM push.
- **Government (`/api/v1/gov/*`):** district-magistrate dashboard endpoints
  (applications scoped to the magistrate's district).
- **Pashu-health (`/api/v1/pashu-health/*`):** reports, animals, vaccinations,
  alerts, vet applications, AI chat, visits, clusters. All routes require
  `X-App-Key`; protected routes additionally require a session token.

Clients: **PashuRakshak Farmer** (Android, `com.pashurakshak.app`),
**PashuRakshak Vet** (Android, `com.pashurakshak.vet`),
**vet-registration** (web, port 5173), **gov-portal** (web, port 5174).

## Stack

| Piece | Choice |
|---|---|
| Runtime | Node.js ≥ 18 (ES modules) |
| HTTP | Express 4 |
| Database | Turso via `@libsql/client` (remote `libsql://` URL, or `file:` for local dev) |
| Config | `dotenv` + strict validation in `src/config/env.js` |
| CORS | Restricted to `CORS_ALLOWED_ORIGINS` |
| Auth | HMAC-signed session tokens + emailed 6-digit OTP + scrypt PIN/password |
| Push | Firebase Cloud Messaging (FCM) via `firebase-admin` |

## Setup

```bash
cd shared-backend
npm install
cp .env.example .env
# edit .env — fill TURSO_DATABASE_URL, TURSO_AUTH_TOKEN, PORT,
# CORS_ALLOWED_ORIGINS, ADMIN_SECRET; set SESSION_SECRET + MAIL_* for OTP
npm start          # runs migrations, then listens on PORT
```

Health check:

```bash
curl http://localhost:3000/health
# {"status":"ok"}
```

## Authentication (custom — no Firebase Auth)

Tokens are stateless HMAC-SHA256 signatures (`src/core/services/sessionToken.js`):

- **Session tokens** — payload `{ typ: 'session', sub, role, phone?, email?, district?, exp }`,
  30-day TTL, returned by login/register/reset endpoints as `{ token }`.
  Sent on every protected request as `Authorization: Bearer <token>`.
- **Verify tokens** — payload `{ typ: 'verify', purpose, email, phone?, exp }`,
  10-minute TTL, returned by `POST /auth/otp/verify` and consumed once while
  completing registration or a PIN reset.

Middleware (`src/core/middleware/verifySession.js`):

- `verifySession` — parses the Bearer token; sets `req.session`, `req.userId`
  (farmers/vets), `req.magistrateId` (gov), `req.role`. Errors:
  `401 missing_bearer_token`, `401 invalid_session_token`.
- `requireRole('farmer' | 'vet' | 'gov')` — rejects with `403 forbidden_role`.

Credentials are stored as `scrypt:<salt>:<hash>` (`src/core/services/credentials.js`).
PINs are exactly 6 digits; vet accounts may also use a password (≥ 6 chars).

OTP codes are 6 digits, SHA-256-hashed at rest, 10-minute TTL, 5 attempts,
rate-limited to 1 send / 60 s and 5 sends / hour per (email, purpose).
Delivery: `MAIL_MODE=log` prints the code to the server console (dev);
`MAIL_MODE=smtp` sends email via Gmail SMTP (use an App Password).

### Auth endpoints (`/api/v1/auth/*`, all require `X-App-Key`)

| Method & path | Body | Returns |
|---|---|---|
| `POST /auth/otp/send` | `{ email, purpose, phone? }` | `{ success, email: masked, purpose, retryAfterSeconds?, message }` |
| `POST /auth/otp/verify` | `{ email, purpose, code }` | `{ success, verifyToken, email: masked }` |
| `POST /auth/farmer/check` | `{ phone }` | `{ exists, hasCredential, requiresPinSetup, hasEmail }` |
| `POST /auth/farmer/login` | `{ phone, pin }` | `{ token, user, profile }` |
| `POST /auth/farmer/register` | `{ verifyToken, phone, pin, name, preferredLanguage?, animalCount?, village, pincode }` | `{ token, user, profile }` |
| `POST /auth/farmer/reset-pin` | `{ verifyToken, pin }` | `{ token, user, profile }` |
| `POST /auth/vet/setup-pin` | `{ verifyToken, phone, pin, pinType? }` | `{ token, user }` |
| `POST /auth/vet/login` | `{ phone, credential, client: 'app' \| 'web' }` | `{ token, user, applicationStatus, application }` |
| `POST /auth/vet/reset-pin` | `{ verifyToken, pin, pinType? }` | `{ token, user }` |
| `POST /auth/gov/request-otp` | `{ email }` | `{ success, email: masked, message }` (same shape whether or not the magistrate exists — no enumeration) |
| `POST /auth/gov/verify` | `{ email, code }` | `{ token, magistrate }` |

`purpose` must be one of: `vet_register`, `farmer_register`,
`farmer_reset_pin`, `vet_reset_pin` (gov uses `gov_login` via its own routes).

**Vet approval gate:** `POST /auth/vet/login` with `client: 'app'` returns
`403 { error: 'not_approved', applicationStatus, reviewNote?, message }` unless
the vet's application is `approved`. `client: 'web'` always succeeds (the
registration web shows status).

Phone numbers are canonicalized (digits only; `+91`/`0` prefixes stripped) —
see `src/core/services/phone.js`.

### Flows

- **Farmer app:** phone + PIN login (`farmer/login`). New users: phone + email →
  OTP (`farmer_register`) → details + PIN (`farmer/register`) → session.
  Legacy accounts without a PIN use forgot-PIN (`farmer_reset_pin`).
- **Vet app:** phone + PIN/password login with `client: 'app'` (approved only).
- **Vet registration web:** email OTP (`vet_register`) → `vet/setup-pin` →
  session → submit application → status page (re-login with `client: 'web'`).
- **Government web:** seeded magistrate → `gov/request-otp` → `gov/verify` →
  gov session.

### Seed a district magistrate (admin only)

```bash
curl -X POST http://localhost:3000/api/v1/core/admin/magistrates \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: $ADMIN_SECRET" \
  -d '{"email":"dm@example.gov.in","name":"District Magistrate","district":"Jaipur","state":"Rajasthan"}'
```

Also: `GET /admin/magistrates` (list), `DELETE /admin/magistrates/:id`.
Magistrates live in the standalone `gov_magistrates` table (session `role='gov'`).

### Vet applications (routed by pincode → district)

All `/api/v1/pashu-health/vet-applications/*` routes require a **vet** session:

| Method & path | Purpose |
|---|---|
| `POST /vet-applications` | Submit/resubmit `{ fullName, qualification, licenseNumber, experienceYears?, clinicName?, address, village, pincode, serviceAreas: string[] }`. District/state resolved from the pincode via `api.postalpincode.in`; also upserts `pashu_vet_profiles` |
| `GET /vet-applications/me` | The signed-in vet's application (or `{ application: null }`) |

### Government dashboard (`/api/v1/gov/*`, gov session + `X-App-Key`)

| Method & path | Purpose |
|---|---|
| `GET /gov/overview` | Platform counts (farmers, vets, applications by status, reports, clusters, alerts) |
| `GET /gov/vet-applications?status=pending\|approved\|rejected\|all` | Applications in **the magistrate's district** |
| `POST /gov/vet-applications/:id/approve` | Body `{ note? }` — district-checked |
| `POST /gov/vet-applications/:id/reject` | Body `{ note }` — district-checked |
| `GET /gov/reports` | All symptom reports |
| `GET /gov/clusters` | Active outbreak clusters |
| `GET /gov/gov-alerts` | Government alerts from risky visits |
| `POST /gov/gov-alerts/:id/acknowledge` | Mark acknowledged |
| `GET /gov/users` | Farmers & vets listing (profiles joined) |

Wrong-district review attempts → `403 wrong_district`.

## Core API

All routes are under `/api/v1/core`.

### Register an app (admin only)

Header: `X-Admin-Secret: <ADMIN_SECRET>`

```bash
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: $ADMIN_SECRET" \
  -d '{"name":"PashuRakshak Android"}'
```

Response (201) — **the `apiKey` is returned exactly once and never again**:

```json
{
  "id": "…",
  "name": "PashuRakshak Android",
  "apiKey": "<64-char hex>",
  "message": "Store this API key now — it is shown ONCE here and will never be returned again.",
  "keyShownOnce": true,
  "createdAt": 1769…
}
```

### List apps (admin only)

```bash
curl http://localhost:3000/api/v1/core/apps -H "X-Admin-Secret: $ADMIN_SECRET"
```

Returns `{ "apps": [{ "id", "name", "created_at" }] }` — never includes `api_key`.

### Using an app key

Send `X-App-Key: <apiKey>` on requests. The `verifyAppKey` middleware
(`src/core/middleware/verifyAppKey.js`) validates it against the `apps` table
and sets `req.appId` for handlers to use.

### Register a device (app key + session token)

`POST /api/v1/core/devices` — protected by **both** `verifyAppKey` and
`verifySession` (which app + which user).

```bash
curl -X POST http://localhost:3000/api/v1/core/devices \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -d '{"role":"vet","fcmToken":"<FCM token from the app>"}'
```

Upserts `device_tokens` keyed by `(app_id, uid, role)` →
`200 { "success": true, "device": { "id", "appId", "uid", "role", "updatedAt" } }`.

### Users system

`POST /api/v1/core/users` — protected by **both** `verifyAppKey` and
`verifySession`. Creates or updates a user keyed by `phone`.

```bash
curl -X POST http://localhost:3000/api/v1/core/users \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -H "Authorization: Bearer $SESSION_TOKEN" \
  -d '{"phone":"9000000000","role":"farmer","name":"Ravi","email":"ravi@example.com","preferredLanguage":"hi"}'
```

Returns `{ "user": { "id", "phone", "role", "name", "email", "preferred_language", "created_at", "updated_at" } }`
(`pin_hash` and legacy `firebase_uid` are stripped).

`GET /api/v1/core/users/me` — protected by both middlewares. Looks up the
calling user by the session's `sub` (user id). Returns `{ "user": {...} }` or
`404 { "exists": false }` (when the user has not completed onboarding).

`PUT /api/v1/core/users/me` — protected by both middlewares. Partial update
of `{ name, email, preferredLanguage }`. Returns the updated user row or
`404 { "exists": false }`.

### Pincode lookup (public)

`GET /api/v1/core/pincode/:code` — calls the [PostalPincode API](https://api.postalpincode.in/pincode/{code}) server-side and returns a simplified list of area / post-office names.

```bash
curl http://localhost:3000/api/v1/core/pincode/110001
```

Returns `{ "pincode": "110001", "offices": [{ "name", "branchType", "deliveryStatus", "district", "state", "areaName", "pincode" }] }`. Returns `404 { "error": "pincode_not_found" }` when the pincode has no results.

### Push helper (core)

`src/core/services/push.js` exports `sendPushToUid(uid, title, body)` — looks up
the user's `fcm_token` in `device_tokens` and sends via
`firebase-admin` `sendEachForMulticast`. Used by cluster detection: when a new
cluster alert is created, every `device_tokens` row with `role='vet'` and the
same `app_id` receives the alert (best-effort; failures never fail the report).
Requires `FIREBASE_SERVICE_ACCOUNT_JSON` (FCM only — not used for login).

## Pashu-Health API

All routes below live under `/api/v1/pashu-health` and are protected by
`verifyAppKey` (the whole router in `src/routes.js`) — every request needs:

```
X-App-Key: <apiKey issued at app registration>
```

Missing/invalid key → `401 { "error": "missing_app_key" | "invalid_app_key", ... }`.

Session-protected routes additionally need `Authorization: Bearer <session token>`
(report POST, farmer/vet profiles, vet applications, AI chat, visits).

Database tables (migration `001_pashu_health.js`, all prefixed `pashu_`):
`pashu_animals`, `pashu_vaccinations`, `pashu_symptom_reports`, `pashu_vets`,
`pashu_alerts`, `pashu_farmers` — columns mirror the Android app's `Migrations.kt`.

Core `users` table (migration `002_users.js`): `id`, `phone`, `role`, `name`,
`email`, `preferred_language`, `pin_hash`, `pin_type`, `email_verified`,
`created_at`, `updated_at` (legacy `firebase_uid` column may still exist).

Profile tables (migration `003_pashu_profiles.js`, `pashu_` prefixed):
- `pashu_farmer_profiles` (`user_id PK/FK → users`, `animal_count`, `village`, `pincode`, `created_at`)
- `pashu_vet_profiles` (`user_id PK/FK → users`, `pincode`, `service_areas` (JSON array), `created_at`, `updated_at`)

AI fields (migration `004_report_ai_fields.js`): `pashu_symptom_reports` gains
`assigned_vet_id`, `ai_risk_category`, `vet_assessment`, and `village`.

AI tables (migration `005_ai_tables.js`): `pashu_ai_responses` (report_id, farmer_id,
response_text, model, created_at) and `pashu_ai_conversations` (report_id, farmer_id,
full_history, created_at, updated_at).

Visit verification + gov alerts (migration `006_visits_and_gov_alerts.js`):
`pashu_visit_verifications` (id, report_id, vet_id, scanned_qr_code_id, matched,
latitude, longitude, verified_at) and `pashu_gov_alerts` (id, report_id, severity,
message, acknowledged, created_at).

Auth + applications (migration `007_auth_and_vet_applications.js`):
`auth_otps` (hashed codes), `gov_magistrates`, `vet_applications`
(status pending/approved/rejected, district from pincode), plus `users` PIN columns.

### Reports

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/reports` | Upsert a symptom report by `id` (idempotent — safe for app retries). Requires a session token. After a successful save, runs **cluster detection**, **AI risk categorization**, and **vet auto-assignment** |
| `GET /api/v1/pashu-health/reports` | List all reports (newest first) → `{ "reports": [...] }` |
| `GET /api/v1/pashu-health/reports/:id` | Single report → `{ "report": {...} }` or `404` |

`POST` body (the Android `SymptomReport` push shape — camelCase; snake_case
aliases from `ReportPushApi.buildPayload` are also accepted; `symptoms` and
`riskBreakdown` are JSON strings):

```json
{
  "id": "uuid",
  "animalId": "uuid",
  "farmerId": "users.id (defaults to the session user)",
  "symptoms": "[\"Fever\",\"Cough\"]",
  "photoRemoteUrl": "https://… or null",
  "latitude": 12.34,
  "longitude": 56.78,
  "riskScore": 45,
  "riskBreakdown": "{\"Fever\":20,\"multiple_symptoms\":10}",
  "status": "reported",
  "createdAt": 1790084141971,
  "village": "optional-village-name"
}
```

`status` must be one of: `reported`, `vet_assigned`, `examined`, `sample_sent`,
`confirmed`, `resolved`.

`village` is optional. When present, the system auto-categorizes the report's
risk (`high` ≥ 60, `mid` ≥ 30, `low` < 30) and attempts to assign a vet whose
`service_areas` contains the village name. See **AI categorization + vet assignment** below.

### AI categorization + vet assignment

After a successful report save, `categorizeAndAssign(report, appId)` runs
automatically:

1. **Risk category** — sets `ai_risk_category` on the report:
   - `high` if `riskScore >= 60` OR report was flagged as part of a cluster
   - `mid` if `riskScore >= 30`
   - `low` otherwise
2. **Vet auto-assignment** — if `village` is present, queries `pashu_vet_profiles`
   for a vet whose `service_areas` JSON text contains the village name. If found:
   - sets `assigned_vet_id` on the report
   - creates a targeted `pashu_alerts` row addressed to that specific vet's user id
   If no village or no matching vet: `assigned_vet_id` stays null, a warning is logged, no error thrown.

This runs inside the same `try/catch` as cluster detection — failures are always
logged and never fail the report push.

### Animals — basic CRUD

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/animals` | Create/upsert (`ownerFarmerId`, `species`, `name`, `qrCodeId` required; server generates `id` if omitted) |
| `GET /api/v1/pashu-health/animals` | List → `{ "animals": [...] }` |
| `GET /api/v1/pashu-health/animals/:id` | One animal or `404` |
| `PUT /api/v1/pashu-health/animals/:id` | Full update or `404` (duplicate `qrCodeId` → `409`) |
| `DELETE /api/v1/pashu-health/animals/:id` | Delete or `404` |

### Vaccinations — basic CRUD

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/vaccinations` | Create/upsert (`animalId`, `vaccineName`, `dateGiven`, `nextDue` required) |
| `GET /api/v1/pashu-health/vaccinations` | List (newest first) |
| `GET /api/v1/pashu-health/vaccinations/:id` | One vaccination or `404` |
| `DELETE /api/v1/pashu-health/vaccinations/:id` | Delete or `404` |

### Alerts — basic CRUD

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/alerts` | Create/upsert (`recipientRole`, `recipientId`, `message` required) |
| `GET /api/v1/pashu-health/alerts` | List (newest first) |
| `GET /api/v1/pashu-health/alerts/:id` | One alert or `404` |
| `PUT /api/v1/pashu-health/alerts/:id` | Partial update (e.g. `{"read": true}`) or `404` |
| `DELETE /api/v1/pashu-health/alerts/:id` | Delete or `404` |

### Cluster detection

`src/modules/pashu-health/services/clusterDetection.js` runs automatically after
every successful `POST /reports`:

1. Load reports from the **last 7 days** inside a **±0.05° bounding box** around
   the new report (SQL pre-filter).
2. Refine with **Haversine** in JS — keep reports within **5 km**.
3. If **3+ reports** (including the new one) qualify → cluster:
   - insert a `pashu_alerts` row with `recipient_role='vet'`,
     `recipient_id='all-vets'`, message describing count + area (deduped so
     retried POSTs don't spam alerts);
   - update every involved report still in `reported` → `vet_assigned`.

Plain JS, no spatial library. Detection failures are logged and never fail the
report push.

### Clusters

| Method & path | Purpose |
|---|---|
| `GET /api/v1/pashu-health/clusters` | Currently active clusters → `{ "clusters": [...] }` |

Active = non-resolved reports from the last 7 days, grouped by single-linkage
at the same 5 km radius; groups with ≥ 3 reports are returned with
`reportCount`, `reportIds`, centroid (`centerLat`/`centerLng`), `radiusKm`,
`firstReportAt`/`lastReportAt`, and `statuses`.

### Farmer profiles

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/farmer-profiles` | Create/upsert profile (requires `animalCount`, `village`, `pincode`); keyed by the session user |
| `GET /api/v1/pashu-health/farmer-profiles/me` | Get the caller's own profile or `404 { exists: false }` |

Protected by `verifyAppKey` + `verifySession`.

### Vet profiles

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/vet-profiles` | Create/upsert profile (requires `pincode`, `serviceAreas: string[]`) |
| `GET /api/v1/pashu-health/vet-profiles/me` | Get the caller's own profile or `404 { exists: false }` |
| `GET /api/v1/pashu-health/vets/available?area=` | List vets whose `service_areas` JSON contains the given area string |

Protected by `verifyAppKey` + `verifySession` for all mutating and `me` routes.
`GET /available` is app-key only (via the router-level middleware).

### AI advisory & chat

Powered by Groq (`llama-3.3-70b-versatile`). Requires `GROQ_API_KEY` in `.env` (optional — failures degrade gracefully).

After a successful report save, `generateAdvisory` is called automatically:
- Builds a prompt with species, symptoms, and risk category
- System prompt restricts guidance to preventive/first-aid only (isolation, hydration, limiting herd contact, hygiene)
- NEVER prescribes medications or dosages
- Closes with "A vet will follow up shortly. This is not a diagnosis"
- Responds in Hindi if `preferred_language = 'hi'`, otherwise English
- Result saved to `pashu_ai_responses` and returned as `aiAdvisory` in the POST `/reports` response

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/reports/:id/chat` | Send a follow-up message; preserves full history but sends only the last 10 messages to Groq to cap token usage |
| `GET /api/v1/pashu-health/reports/:id/chat` | Returns the FULL stored conversation history for display |

Both chat endpoints require `verifyAppKey` + `verifySession`. A Groq failure (rate limit, network, missing key) never blocks the report save — `aiAdvisory` is `null` on failure.

### Visit verification

`POST /api/v1/pashu-health/visits` — protected by `verifyAppKey` + `verifySession`.

Accepts `{ reportId, scannedQrCodeId, latitude, longitude, assessment }` where `assessment` is one of `risky`, `moderate`, `mild`.

- Compares `scannedQrCodeId` to the report's animal QR code to set `matched`
- Rejects `403` if the session user is not the assigned vet (unless no vet is assigned — fallback path)
- Inserts into `pashu_visit_verifications`
- Updates `pashu_symptom_reports` with `vet_assessment` and sets `status = 'examined'`
- If `assessment === 'risky'`, inserts a `pashu_gov_alerts` row (severity='high') for the government dashboard

### Government alerts

`GET /api/v1/pashu-health/gov-alerts` — app-key only (via router-level middleware).

Returns all `pashu_gov_alerts` rows ordered by `created_at DESC`. The gov-portal
uses `GET /api/v1/gov/gov-alerts` (gov session) plus acknowledge.

## Environment variables

See `.env.example`:

| Var | Required | Purpose |
|---|---|---|
| `TURSO_DATABASE_URL` | yes | Turso `libsql://…` URL (or `file:…` for local dev) |
| `TURSO_AUTH_TOKEN` | yes | Turso auth token |
| `PORT` | yes | HTTP port |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated allowed origins (backend + both webs: 3000, 5173, 5174) |
| `ADMIN_SECRET` | yes | Value of the `X-Admin-Secret` header for admin routes |
| `SESSION_SECRET` | recommended | HMAC secret for session/verify tokens. Falls back to a value derived from `ADMIN_SECRET` when unset |
| `MAIL_MODE` | no (default `log`) | `log` prints OTP codes to the console; `smtp` sends email |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_USER` / `SMTP_PASS` / `MAIL_FROM` | for `MAIL_MODE=smtp` | Gmail SMTP (App Password) |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | for FCM push | One-line service-account JSON. Missing → push helpers skip gracefully; login is unaffected |
| `B2_APPLICATION_KEY_ID` / `B2_APPLICATION_KEY` / `B2_BUCKET_NAME` | for photo storage | Backblaze B2 (mirrors Android `local.properties`) |
| `GROQ_API_KEY` | no | Groq API key for AI advisory and chat. Without it, AI routes degrade gracefully and return `null`. |

The server refuses to start and prints every missing variable if any required
variables are absent.

## Migrations

Migrations live in `src/db/migrations/*.js`, each exporting:

```js
export default {
  version: 8,          // unique, monotonic
  name: '007_auth_and_vet_applications',
  async up(db) { /* SQL via db.execute / db.batch */ },
};
```

Applied versions are tracked in a `migrations_applied` table (instead of
`PRAGMA user_version`) because migrations span multiple files across modules.
Migrations run automatically on server start (`npm start`) or manually:

```bash
npm run migrate
```

## Project layout

```
src/
├── config/env.js            # env loading + validation
├── db/client.js             # configured Turso client
├── db/migrate.js            # migration runner (migrations_applied table)
├── db/migrations/           # numbered migration files (000_core.js … 007_auth_and_vet_applications.js)
├── core/
│   ├── middleware/          # verifyAppKey.js, adminSecret.js, verifySession.js
│   ├── routes/              # apps.js, auth.js, devices.js, users.js, pincode.js, gov.js, adminMagistrates.js
│   └── services/            # sessionToken.js, otp.js, mailer.js, credentials.js, phone.js, pincode.js, push.js, firebaseAdmin.js
├── modules/
│   ├── pashu-health/routes/      # reports.js, animals.js, vaccinations.js, alerts.js, clusters.js, farmerProfiles.js, vetProfiles.js, vetApplications.js, vetsAvailable.js, aiChat.js, visits.js, govAlerts.js, index.js
│   └── pashu-health/services/    # clusterDetection.js (7-day / 5 km / 3+ rule, categorizeAndAssign), aiAdvisory.js (Groq)
├── routes.js                # mounts core + auth + gov + pashu-health under /api/v1
└── app.js                   # Express setup, CORS, /health, starts server
```

## How to add a new module

1. Create a folder: `src/modules/<name>/` (e.g. `src/modules/pashu-health/`).
2. Inside it, add your routers (see `src/modules/pashu-health/routes/` for the
   real pattern): a `routes/index.js` that mounts per-resource routers, e.g.:

   ```js
   // src/modules/<name>/routes/index.js
   import { Router } from 'express';
   const router = Router();
   // router.use('/things', thingsRouter);
   export default router;
   ```

3. Put module-specific migrations in `src/db/migrations/` with a fresh
   `version` number (e.g. `010_pashu_health.js`); they run automatically.
4. Mount it in `src/routes.js`:

   ```js
   import myModuleRoutes from './modules/<name>/routes/index.js';
   router.use('/<name>', verifyAppKey, myModuleRoutes);
   ```

   → served at `/api/v1/<name>/...`.

5. Keep project-specific tables inside your module's migrations; core tables
   (`apps`, `device_tokens`, `users`, `auth_otps`, `gov_magistrates`) stay
   generic and shared.

## Security notes

- `POST /api/v1/core/apps` returns `apiKey` **once** — store it securely; the
  GET route never returns keys.
- `.env` is git-ignored; only `.env.example` (no secrets) is committed.
- CORS rejects any origin not listed in `CORS_ALLOWED_ORIGINS`.
- Every `/api/v1/*` request must send a valid `X-App-Key`
  (`verifyAppKey` is applied in `src/routes.js`).
- Protected business routes also require a valid session token
  (`Authorization: Bearer …`) and, where relevant, the right role
  (`requireRole`).
- OTP codes and credential hashes are never stored in plaintext; OTP responses
  only return masked emails.
- Set an explicit `SESSION_SECRET` in production; rotate `ADMIN_SECRET` carefully
  (changing it changes the derived session secret when `SESSION_SECRET` is unset,
  invalidating existing sessions).
- Firebase is used **only** for FCM push (`FIREBASE_SERVICE_ACCOUNT_JSON`);
  removing it only disables push notifications, never login.
