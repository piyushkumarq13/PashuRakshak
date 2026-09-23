# Government Portal (District Magistrate)

A standalone React (Vite) web app for District Magistrates to review and
approve/reject veterinarian applications, and monitor platform health —
all against the **existing** `shared-backend`.

## What magistrates can do

1. **Sign in** — registered email → 6-digit OTP (no password).
2. **Overview** — platform counts (farmers, vets, applications, reports,
   clusters, alerts).
3. **Vet Applications** — district-scoped list (routed from application
   pincode); approve or reject with a review note shown to the applicant.
4. **Reports** — all symptom reports, filterable by status.
5. **Clusters** — active outbreak clusters (7-day / 5 km / 3+ rule).
6. **Gov Alerts** — acknowledge government alerts from risky field visits.
7. **Users** — farmers & vets listing with profiles.

## Prerequisites

- Node.js 18+
- `shared-backend` running (see its README)

## Setup

### 1. Seed a magistrate (one-time, via backend admin)

```bash
curl -X POST http://localhost:3000/api/v1/core/admin/magistrates \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET from shared-backend/.env>" \
  -d '{"email":"dm.rampur@example.gov.in","name":"Jane Doe","district":"Rampur","state":"Uttar Pradesh"}'
```

`district` **must match** the district resolved from application pincodes
(postal API) for those applications to appear in the portal.

List / remove:

```bash
curl http://localhost:3000/api/v1/core/admin/magistrates -H "X-Admin-Secret: ..."
curl -X DELETE http://localhost:3000/api/v1/core/admin/magistrates/<id> -H "X-Admin-Secret: ..."
```

### 2. Register this app with the backend (one-time)

```bash
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET from shared-backend/.env>" \
  -d '{"name":"Government Portal"}'
```

Response contains `apiKey` — **shown once only**.

### 3. Configure environment

```bash
cp .env.example .env
```

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000` (dev) or deployed backend URL |
| `VITE_APP_API_KEY` | The `apiKey` from step 2 |

### 4. Run

```bash
npm install
npm run dev
```

Opens at `http://localhost:5174` (allow-listed in backend CORS).

## Build

```bash
npm run build
```

Outputs to `dist/`.

## Deploy

Same as any Vite static site (Vercel / Netlify / Render):

- Build: `npm run build`
- Output: `dist`
- Set `VITE_*` env vars, then add the deployed URL to the backend's
  `CORS_ALLOWED_ORIGINS`.

## Project structure

```
gov-portal/
├── .env.example
├── README.md
├── index.html
├── package.json
├── vite.config.js          # dev/preview port 5174
└── src/
    ├── main.jsx            # React entry
    ├── App.jsx             # Login vs dashboard + sign-out
    ├── api.js              # Backend API client (session token in localStorage)
    ├── styles.css
    └── components/
        ├── LoginScreen.jsx     # Email → OTP → session
        ├── Dashboard.jsx       # Tab shell
        ├── OverviewTab.jsx     # Platform counts
        ├── ApplicationsTab.jsx # Approve / reject (district-scoped)
        ├── ReportsTab.jsx      # Symptom reports table
        ├── ClustersTab.jsx     # Active outbreak clusters
        ├── GovAlertsTab.jsx    # Acknowledge alerts
        └── UsersTab.jsx        # Farmers & vets listing
```

## API endpoints used

All under `http://localhost:3000/api/v1`, with `X-App-Key` header.
Session token (from OTP login) goes in `Authorization: Bearer <token>`.

| Method | Endpoint | Auth |
|---|---|---|
| POST | `/auth/gov/request-otp` | App key |
| POST | `/auth/gov/verify` | App key |
| GET | `/gov/overview` | Gov session |
| GET | `/gov/vet-applications?status=` | Gov session |
| POST | `/gov/vet-applications/:id/approve` | Gov session |
| POST | `/gov/vet-applications/:id/reject` | Gov session |
| GET | `/gov/reports` | Gov session |
| GET | `/gov/clusters` | Gov session |
| GET | `/gov/gov-alerts` | Gov session |
| POST | `/gov/gov-alerts/:id/acknowledge` | Gov session |
| GET | `/gov/users` | Gov session |
