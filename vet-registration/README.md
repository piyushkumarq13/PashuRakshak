# Vet Registration

A standalone React (Vite) web app for veterinarians to register on PashuRakshak.

It calls the **existing** `shared-backend` API only — no new backend, no new database.

## What it does

1. **Phone auth** — Firebase Phone Auth (OTP) using the same Firebase project as the Android app (`pashurakshak-961c1`).
2. **Landing check** — after OTP, calls `GET /api/v1/core/users/me`.
   - **404** → shows registration form (name, email, pincode → fetch areas → checkbox select → `POST /core/users` + `POST /pashu-health/vet-profiles`).
   - **200** → shows profile page pre-filled from `GET /pashu-health/vet-profiles/me`, editable, saves via `PUT /core/users/me` + `POST /pashu-health/vet-profiles` (upsert).

## Prerequisites

- Node.js 18+
- The `shared-backend` running (see its README)
- A Firebase **Web app** registered in the Firebase console for project `pashurakshak-961c1`

## Setup

### 1. Register this app with the backend (one-time)

```bash
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: <ADMIN_SECRET from shared-backend/.env>" \
  -d '{"name":"Vet Registration Web"}'
```

The response contains `apiKey` — **store it now, it is shown only once**.

### 2. Get Firebase Web config

Firebase console → **Project Settings** → **Your apps** → **Web** (`</>` icon).

Copy the config values (apiKey, appId, etc.).

### 3. Configure environment

```bash
cp .env.example .env
```

Fill in:

| Variable | Value |
|---|---|
| `VITE_API_BASE_URL` | `http://localhost:3000` (dev) or your deployed backend URL |
| `VITE_APP_API_KEY` | The `apiKey` from step 1 |
| `VITE_FIREBASE_API_KEY` | From Firebase console web config |
| `VITE_FIREBASE_APP_ID` | From Firebase console web config |
| `VITE_FIREBASE_AUTH_DOMAIN` | `pashurakshak-961c1.firebaseapp.com` |
| `VITE_FIREBASE_PROJECT_ID` | `pashurakshak-961c1` |
| `VITE_FIREBASE_STORAGE_BUCKET` | `pashurakshak-961c1.firebasestorage.app` |
| `VITE_FIREBASE_MESSAGING_SENDER_ID` | `214607902311` |

### 4. Run

```bash
npm install
npm run dev
```

Opens at `http://localhost:5173` (already allow-listed in backend CORS).

## Build

```bash
npm run build
```

Outputs to `dist/`.

## Deploy

### Vercel (free)

```bash
npm i -g vercel
vercel
```

- Framework: **Vite**
- Build command: `npm run build`
- Output directory: `dist`
- Add all `VITE_*` env vars in Vercel project settings

### Netlify (free)

```bash
npm i -g netlify-cli
netlify deploy --prod --dir=dist
```

Or connect the repo in the Netlify dashboard:

- Build command: `npm run build`
- Publish directory: `dist`
- Add `VITE_*` env vars in site settings

### Render (static site, free)

1. New → **Static Site**
2. Build command: `npm install && npm run build`
3. Publish directory: `dist`
4. Add `VITE_*` env vars

**After deploying**, add the deployed URL to `CORS_ALLOWED_ORIGINS` in the backend `.env`:

```
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,https://your-app.vercel.app
```

## Project structure

```
vet-registration/
├── .env.example
├── README.md
├── index.html
├── package.json
├── vite.config.js
└── src/
    ├── main.jsx          # React entry
    ├── App.jsx           # Stage machine: auth → checking → register | profile
    ├── firebase.js       # Firebase init + reCAPTCHA
    ├── api.js            # All backend API calls
    ├── styles.css
    └── components/
        ├── PhoneAuth.jsx        # Phone → OTP
        ├── RegistrationForm.jsx # Name, email, pincode → areas
        └── ProfilePage.jsx      # View / edit profile
```

## API endpoints used

| Method | Endpoint | Headers |
|---|---|---|
| GET | `/api/v1/core/users/me` | `X-App-Key` + `Authorization: Bearer <idToken>` |
| POST | `/api/v1/core/users` | `X-App-Key` + `Authorization: Bearer <idToken>` |
| PUT | `/api/v1/core/users/me` | `X-App-Key` + `Authorization: Bearer <idToken>` |
| GET | `/api/v1/core/pincode/:code` | none (public) |
| GET | `/api/v1/pashu-health/vet-profiles/me` | `X-App-Key` + `Authorization: Bearer <idToken>` |
| POST | `/api/v1/pashu-health/vet-profiles` | `X-App-Key` + `Authorization: Bearer <idToken>` |
