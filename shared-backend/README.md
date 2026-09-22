# shared-backend

Multi-app shared backend foundation: Express (ES modules) + Turso/libSQL.

- **Phase 1:** core app-key system (`/api/v1/core/*`) — app registration, admin auth.
- **Phase 2:** `pashu-health` module (`/api/v1/pashu-health/*`) — reports, animals,
  vaccinations, alerts for the PashuRakshak Android app. All module routes require
  a valid `X-App-Key`.
- **Phase 3:** cluster detection (7-day / 5 km / 3+ rule) on report POST + `GET /clusters`.
- **Phase 4:** Firebase Auth verification (core) + generic device-token registration
  + vet push on cluster alerts.

## Stack

| Piece | Choice |
|---|---|
| Runtime | Node.js ≥ 18 (ES modules) |
| HTTP | Express 4 |
| Database | Turso via `@libsql/client` (remote `libsql://` URL, or `file:` for local dev) |
| Config | `dotenv` + strict validation in `src/config/env.js` |
| CORS | Restricted to `CORS_ALLOWED_ORIGINS` |

## Setup

```bash
cd shared-backend
npm install
cp .env.example .env
# edit .env — fill TURSO_DATABASE_URL, TURSO_AUTH_TOKEN, PORT,
# CORS_ALLOWED_ORIGINS, ADMIN_SECRET
npm start          # runs migrations, then listens on PORT
```

Health check:

```bash
curl http://localhost:3000/health
# {"status":"ok"}
```

## Core API (Phase 1)

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

### Using an app key (for future module routes)

Send `X-App-Key: <apiKey>` on requests. The `verifyAppKey` middleware
(`src/core/middleware/verifyAppKey.js`) validates it against the `apps` table
and sets `req.appId` for handlers to use.

## Firebase Auth + devices (Phase 4)

Core middleware `src/core/middleware/verifyFirebaseToken.js` initializes
`firebase-admin` from `FIREBASE_SERVICE_ACCOUNT_JSON` and verifies
`Authorization: Bearer <idToken>`, attaching `req.uid`.

> **Where to get the JSON:** Firebase Console → **Project Settings** →
> **Service Accounts** → **Generate new private key**. It must belong to the
> **same Firebase project the Android app uses** (the one behind its
> `google-services.json`), otherwise tokens minted by the app will fail
> verification. Put the file contents on a single line as the value of
> `FIREBASE_SERVICE_ACCOUNT_JSON` in `.env` (see `.env.example`).
>
> Without it, protected routes respond `503 { "error": "firebase_not_configured" }`.

### Register a device (app key + Firebase token)

`POST /api/v1/core/devices` — protected by **both** `verifyAppKey` and
`verifyFirebaseToken` (which app + which user).

```bash
curl -X POST http://localhost:3000/api/v1/core/devices \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -H "Authorization: Bearer $FIREBASE_ID_TOKEN" \
  -d '{"role":"vet","fcmToken":"<FCM token from the app>"}'
```

Upserts `device_tokens` keyed by `(app_id, uid, role)` →
`200 { "success": true, "device": { "id", "appId", "uid", "role", "updatedAt" } }`.

### Push helper (core)

`src/core/services/push.js` exports `sendPushToUid(uid, title, body)` — looks up
the user's `fcm_token` in `device_tokens` and sends via
`firebase-admin` `sendEachForMulticast`. Used by cluster detection: when a new
cluster alert is created, every `device_tokens` row with `role='vet'` and the
same `app_id` receives the alert (best-effort; failures never fail the report).

### Firebase-protected module routes

- `POST /api/v1/pashu-health/reports` now also requires
  `Authorization: Bearer <idToken>` (in addition to `X-App-Key`) — report
  submissions are tied to an authenticated farmer (`req.uid`).
- All `GET /pashu-health/*` routes remain app-key only.

## Pashu-Health API (Phase 2)

All routes below live under `/api/v1/pashu-health` and are protected by
`verifyAppKey` (the whole router in `src/routes.js`) — every request needs:

```
X-App-Key: <apiKey issued at app registration>
```

Missing/invalid key → `401 { "error": "missing_app_key" | "invalid_app_key", ... }`.

Database tables (migration `001_pashu_health.js`, all prefixed `pashu_`):
`pashu_animals`, `pashu_vaccinations`, `pashu_symptom_reports`, `pashu_vets`,
`pashu_alerts`, `pashu_farmers` — columns mirror the Android app's `Migrations.kt`.

### Reports

| Method & path | Purpose |
|---|---|
| `POST /api/v1/pashu-health/reports` | Upsert a symptom report by `id` (idempotent — safe for app retries) → `200 { "success": true }`. After a successful save, runs **cluster detection** (see below). |
| `GET /api/v1/pashu-health/reports` | List all reports (newest first) → `{ "reports": [...] }` |
| `GET /api/v1/pashu-health/reports/:id` | Single report → `{ "report": {...} }` or `404` |

`POST` body (the Android `SymptomReport` push shape — camelCase; snake_case
aliases from `ReportPushApi.buildPayload` are also accepted; `symptoms` and
`riskBreakdown` are JSON strings):

```json
{
  "id": "uuid",
  "animalId": "uuid",
  "farmerId": "uid-or-phone",
  "symptoms": "[\"Fever\",\"Cough\"]",
  "photoRemoteUrl": "https://… or null",
  "latitude": 12.34,
  "longitude": 56.78,
  "riskScore": 45,
  "riskBreakdown": "{\"Fever\":20,\"multiple_symptoms\":10}",
  "status": "reported",
  "createdAt": 1790084141971
}
```

`status` must be one of: `reported`, `vet_assigned`, `examined`, `sample_sent`,
`confirmed`, `resolved`.

Example:

```bash
curl -X POST http://localhost:3000/api/v1/pashu-health/reports \
  -H "Content-Type: application/json" \
  -H "X-App-Key: $APP_KEY" \
  -d '{"id":"r1","animalId":"a1","farmerId":"f1","symptoms":"[\"Fever\"]","photoRemoteUrl":null,"latitude":12.3,"longitude":77.1,"riskScore":20,"riskBreakdown":"{\"Fever\":20}","status":"reported","createdAt":1790084141971}'
```

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

> `pashu_vets` / `pashu_farmers` tables exist (migration `001`) but have no
> endpoints yet.

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

## Environment variables

See `.env.example`:

| Var | Required | Purpose |
|---|---|---|
| `TURSO_DATABASE_URL` | yes | Turso `libsql://…` URL (or `file:…` for local dev) |
| `TURSO_AUTH_TOKEN` | yes | Turso auth token |
| `PORT` | yes | HTTP port |
| `CORS_ALLOWED_ORIGINS` | yes | Comma-separated allowed origins |
| `ADMIN_SECRET` | yes | Value of the `X-Admin-Secret` header for admin routes |
| `FIREBASE_SERVICE_ACCOUNT_JSON` | for auth/push | One-line service-account JSON (see Firebase Auth section). Missing → `503` on Firebase-protected routes, server still starts. |

The server refuses to start and prints every missing variable if any are absent.

## Migrations

Migrations live in `src/db/migrations/*.js`, each exporting:

```js
export default {
  version: 2,          // unique, monotonic
  name: '001_pashu_health',
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
├── db/migrations/           # numbered migration files (000_core.js, 001_pashu_health.js, …)
├── core/
│   ├── middleware/          # verifyAppKey.js, adminSecret.js, verifyFirebaseToken.js
│   ├── routes/              # apps.js, devices.js
│   └── services/            # push.js (sendPushToUid)
├── modules/
│   ├── pashu-health/routes/      # reports.js, animals.js, vaccinations.js, alerts.js, clusters.js, index.js
│   └── pashu-health/services/    # clusterDetection.js (7-day / 5 km / 3+ rule)
├── routes.js                # mounts core + pashu-health (+ future modules) under /api/v1
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
4. Mount it in `src/routes.js` (the commented placeholder shows where):

   ```js
   import pashuHealthRoutes from './modules/pashu-health/routes/index.js';
   router.use('/pashu-health', verifyAppKey, pashuHealthRoutes);
   ```

   → served at `/api/v1/pashu-health/...`.

5. Keep project-specific tables inside your module's migrations; core tables
   (`apps`, `device_tokens`) stay generic and shared.

## Security notes

- `POST /api/v1/core/apps` returns `apiKey` **once** — store it securely; the
  GET route never returns keys.
- `.env` is git-ignored; only `.env.example` (no secrets) is committed.
- CORS rejects any origin not listed in `CORS_ALLOWED_ORIGINS`.
- Every `/api/v1/pashu-health/*` request must send a valid `X-App-Key`
  (`verifyAppKey` is applied to the whole module router in `src/routes.js`).
- `POST /api/v1/core/devices` and `POST /api/v1/pashu-health/reports` also
  require a valid Firebase ID token (`Authorization: Bearer …`).
- Push and report POSTs degrade to a clear `503 firebase_not_configured` until
  `FIREBASE_SERVICE_ACCOUNT_JSON` is set — the server still boots without it.
