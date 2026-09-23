# Vet Registration

A standalone React (Vite) web app where veterinarians register, verify their
email with an OTP, set a 6-digit PIN or password, submit their application,
and track approval status — all against the **existing** `shared-backend`
(no Firebase Auth; OTPs come from the backend via Gmail SMTP).

## Flow

1. **Sign in / register** — phone + email → backend emails a 6-digit OTP.
2. **Verify OTP** → **set credential** (6-digit PIN or password).
3. **Application** — professional details + pincode → server resolves district
   → **coverage-area multi-select** → submitted to that District Magistrate.
4. **Status page** — pending / approved / rejected (with reviewer note;
   rejected apps can be edited and resubmitted).

Returning vets sign in with phone + PIN/password; while `pending`/`rejected`
they see the status page, once `approved` they can log in to the Vet App.

## Prerequisites

- Node.js 18+
- `shared-backend` running (see its README)

## Setup

### 1. Register this app with the backend (one-time)

```bash
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET from shared-backend/.env>" \
  -d '{"name":"Vet Registration Web"}'
```

Response contains `apiKey` — **shown once only**.

### 2. Configure environment

```bash
cp .env.example .env
```

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000` (dev) or deployed backend URL |
| `VITE_APP_API_KEY` | The `apiKey` from step 1 |

### 3. Run

```bash
npm install
npm run dev
```

Opens at `http://localhost:5173` (allow-listed in backend CORS).

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
vet-registration/
├── .env.example
├── README.md
├── index.html
├── package.json
├── vite.config.js
└── src/
    ├── main.jsx                  # React entry
    ├── App.jsx                   # Stage machine (register/otp/credential/app/status)
    ├── api.js                    # Backend API client (session token in localStorage)
    ├── styles.css
    └── components/
        ├── RegisterStart.jsx     # Phone + email → send OTP
        ├── OtpVerify.jsx         # 6-digit OTP entry (resend supported)
        ├── SetCredential.jsx     # Choose PIN or password
        ├── SignInForm.jsx        # Phone + PIN/password sign-in
        ├── ForgotStart.jsx       # Email → send reset OTP
        ├── ResetCredential.jsx   # OTP → set new PIN/password
        ├── CredentialInput.jsx   # PIN (6-digit) or password input
        ├── ApplicationForm.jsx   # Step 1 details → step 2 coverage areas
        └── StatusPage.jsx        # pending/approved/rejected + review note
```

## API endpoints used

All under `http://localhost:3000/api/v1`, with `X-App-Key` header.
Session token (from login/OTP flows) goes in `Authorization: Bearer <token>`.

| Method | Endpoint | Auth |
|---|---|---|
| POST | `/core/auth/vet/register/send-otp` | App key |
| POST | `/core/auth/vet/register/verify-otp` | App key |
| POST | `/core/auth/vet/setup-pin` | Verify token (from OTP) |
| POST | `/core/auth/vet/login` | App key |
| POST | `/core/auth/vet/reset-pin/request` | App key |
| POST | `/core/auth/vet/reset-pin/confirm` | Verify token |
| GET | `/core/auth/me` | Session token |
| POST | `/pashu-health/vet-applications` | Session token |
| GET | `/pashu-health/vet-applications/me` | Session token |
| GET | `/core/pincode/:code` | Public |
