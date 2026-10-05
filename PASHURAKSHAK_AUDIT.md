# PASHURAKSHAK — TECHNICAL AUDIT REPORT

Generated from a read-only review of the repository at C:\Users\piyus\Desktop\PasuRakshak.
Nothing was executed, deployed, or migrated. Secrets are never printed; only env var NAMES appear.

=====================================================================
1. OVERVIEW
=====================================================================

PasuRakshak ("livestock protector") is an offline-first Android livestock
disease-reporting app pair (farmer + vet) backed by a multi-tenant Express
API, an admin government dashboard web app, and a vet self-registration web
app. Farmers report sick animals with photos/location/symptoms; the backend
scores risk, groups nearby reports into outbreak clusters, auto-assigns a vet
by village coverage, generates an AI advisory, and vets verify visits by
scanning the animal's QR code; risky findings create government alerts.

Parts of the project:

- Android farmer + vet apps (one shared Kotlin module, two thin launchers):
  `core/` (all code), `app-farmer/`, `app-vet/` (Gradle applicationIds
  `com.pashurakshak.app` and `com.pashurakshak.vet`).
- Shared backend API (Express, Turso/libSQL): `shared-backend/`.
- Vet registration web app (React/Vite): `vet-registration/`.
- Government district-magistrate dashboard (React/Vite): `gov-portal/`.
- Misc: `scripts/` (Python APK alignment helpers), `gradle/` (version catalog),
  root Gradle wrapper. Also a pre-existing `PROJECT_AUDIT.md` and `README.md`
  at root, and a checked-in `shared-backend/local.db` SQLite file.

=====================================================================
2. TECH STACK
=====================================================================

ANDROID (`core/build.gradle.kts`, `gradle/libs.versions.toml`)
- Kotlin via AGP 9.4.1 built-in Kotlin; Compose compiler plugin 2.2.10;
  Compose BOM 2026.09.00; activity-compose 1.13.0; navigation-compose 2.8.5;
  lifecycle 2.11.0; kotlinx-coroutines-android 1.11.0.
- Local DB: embedded Turso libSQL `tech.turso.libsql:libsql` 0.1.2 (raw SQL,
  no Room). No Ktor/Retrofit/OkHttp — raw `HttpURLConnection` everywhere.
  No Hilt — hand-written `di/ServiceLocator.kt`.
- WorkManager 2.10.1 (periodic + one-shot sync), play-services-location
  21.4.0, ZXing Android Embedded 4.3.0 (QR scan/generate), Coil 2.7.0,
  Firebase BOM 33.16.0 + firebase-messaging (FCM push), core-splashscreen.
- Gradle wrapper 9.6.0; minSdk 24, targetSdk 37, compileSdk 37.
- Tests: only JUnit 4.13.2 tests for B2 upload helper (2 files).

BACKEND (`shared-backend/package.json`)
- Node >= 18, ES modules, Express 4.21, `cors` 2.8.5, `dotenv` 16.4.5,
  `@libsql/client` 0.15 (Turso remote `libsql://` or local `file:`),
  `firebase-admin` 14.4 (FCM only), `groq-sdk` 1.6 (AI), `nodemailer` 6.9.16
  (OTP email). No test framework, no lint/typecheck.
- DB: Turso/libSQL (SQLite dialect). Migrations tracked in
  `migrations_applied` table; run on server start and via `npm run migrate`.
- AI provider: Groq. Model actually used: `openai/gpt-oss-120b`
  (`src/modules/pashu-health/services/aiAdvisory.js:8`). NOTE: README claims
  `llama-3.3-70b-versatile` — stale.
- Push: Firebase Cloud Messaging via service account JSON.
- File storage: Backblaze B2 for report photos (native B2 API from Android;
  server only stores `photo_remote_url`; backend env also holds B2 keys but
  no server-side upload code exists).
- Deploy target: Render (`shared-backend/render.yaml`, `Procfile`), health
  check `/health`, Node 20.

WEB APPS (`vet-registration/package.json`, `gov-portal/package.json`)
- React 19.1, react-dom 19.1, Vite 6.3.5, @vitejs/plugin-react 4.5.2.
  No router library (stage-based state machine in App.jsx). No auth
  libraries — hand-rolled token storage in localStorage.

=====================================================================
3. FOLDER AND FILE MAP
=====================================================================

ROOT
- `settings.gradle.kts` — includes `:core`, `:app-farmer`, `:app-vet`.
- `build.gradle.kts` — plugin declarations (AGP, compose, google-services).
- `gradle.properties` / `gradle/libs.versions.toml` / `gradle/wrapper/*`.
- `local.properties` (git-ignored but populated on disk: live API URL,
  APP_API_KEY, real B2 key id/key/bucket — flag as compromised) and
  `local.properties.example` (template keys listed in section 10).
- `scripts/align-apk.py`, `scripts/align-elf-16k.py` — 16 KB ELF alignment.
- `README.md`, `PROJECT_AUDIT.md` (pre-existing audit), `.gitignore`.

ANDROID — `core/src/main/java/com/pashurakshak/app/`
- `MainActivity.kt` — launcher; splash, notification permission, deep-link
  to alerts via `EXTRA_DESTINATION`.
- `PashuRakshakApplication.kt` — init SessionManager/ServiceLocator/channel;
  schedules periodic sync + one-shot sync + network reconnect observer.
- `navigation/Screen.kt` — route definitions.
- `ui/navigation/AppNavHost.kt` — full nav graph (details in section 4).
- `ui/HomeScreens.kt` — FarmerHomeScreen, VetHomeScreen; `ui/HomeViewModel.kt`,
  `ui/vet/VetHomeViewModel.kt` — local-first counts then `RemoteSync.pullAll()`.
- `ui/OnboardingScreen.kt` + VM — legacy farmer profile completion.
- `ui/auth/` — `LoginScreen/ViewModel`, `RegisterScreen/ViewModel`,
  `ForgotPinScreen/ViewModel`, `AuthComponents.kt`.
- `ui/farmer/` — ReportSickAnimal(+VM), MyAnimals(+VM), MyReports(+VM),
  VaccinationStatus(+VM), Alerts(+VM), QrPassport(+VM), StatusLabels, DateFormats.
- `ui/vet/` — VetCaseQueue(+VM), CaseDetail(+VM), RiskLevel, VetHomeViewModel.
- `ui/aiInsight/` — AiInsightScreen (chat) + VM.
- `ui/profile/` — ProfileScreen + VM.
- `ui/components/` — AppComponents (incl. LanguageSelector), AppDialogs,
  FarmerBottomBar, VetBottomBar, ReportPhoto, SyncStatusBanner.
- `ui/theme/Theme.kt`, `ui/debug/B2UploadTestScreen.kt`.
- `data/` — `AuthRepository.kt`, `AnimalRepository.kt`, `AlertRepository.kt`,
  `FarmerRepository.kt`, `ReportRepository.kt`, `SessionManager.kt`,
  `VaccinationRepository.kt`, `VetRepository.kt`.
- `data/local/` — `TursoClient.kt`, `Migrations.kt`, `RowValues.kt`,
  `Animal.kt`, `Alert.kt`, `Vaccination.kt`, `Vet.kt`, `SymptomReport.kt`.
- `data/remote/` — `B2UploadService.kt`, `PushMessagingService.kt`,
  `ReportPushApi.kt`.
- `data/sync/` — `RemoteSync.kt`, `SyncScheduler.kt`, `SyncWorker.kt`,
  `NetworkReconnectObserver.kt`, `SyncStatusHolder.kt`.
- `di/ServiceLocator.kt`, `notifications/AppNotifications.kt`.
- `core/src/test/.../B2UploadServiceTest.kt`, `B2UploadRetryTest.kt`.
- `app-farmer/`, `app-vet/` — only AndroidManifest stubs + strings.xml;
  `app-farmer/google-services.json` present, `app-vet` one absent.

BACKEND — `shared-backend/src/`
- `app.js` — Express setup, CORS, `/health`, `/otp` debug page, 404 + error
  middleware, starts server after migrations.
- `routes.js` — mounts `/core`, `/auth` (verifyAppKey), `/gov` (verifyAppKey),
  `/pashu-health` (verifyAppKey).
- `config/env.js` — env loading + required-var validation.
- `db/client.js` — libSQL client; `db/migrate.js` — runner;
  `db/migrations/000_core.js` … `007_auth_and_vet_applications.js`.
- `core/middleware/` — `verifyAppKey.js`, `verifySession.js` (+requireRole),
  `adminSecret.js`.
- `core/routes/` — `apps.js`, `auth.js`, `devices.js`, `users.js`,
  `pincode.js`, `gov.js`, `adminMagistrates.js`.
- `core/services/` — `sessionToken.js`, `otp.js`, `otpLog.js`, `mailer.js`,
  `credentials.js`, `phone.js`, `pincode.js`, `push.js`, `firebaseAdmin.js`.
- `modules/pashu-health/routes/` — `index.js` + reports, animals,
  vaccinations, alerts, clusters, farmerProfiles, vetProfiles,
  vetApplications, vetsAvailable, aiChat, visits, govAlerts.
- `modules/pashu-health/services/` — `clusterDetection.js` (also hosts
  `categorizeAndAssign`), `aiAdvisory.js`.

VET REGISTRATION WEB — `vet-registration/src/`
- `App.jsx` (stage state machine), `api.js` (hardcoded prod fallback URL +
  app key), `main.jsx`, `styles.css`, `components/` — `RegisterStart.jsx`,
  `OtpVerify.jsx`, `SetCredential.jsx`, `ApplicationForm.jsx`,
  `SignInForm.jsx`, `CredentialInput.jsx`, `ForgotStart.jsx`,
  `ResetCredential.jsx`, `StatusPage.jsx`.

GOV PORTAL WEB — `gov-portal/src/`
- `App.jsx`, `api.js` (hardcoded prod fallback URL + app key), `main.jsx`,
  `styles.css`, `components/` — `LoginScreen.jsx`, `Dashboard.jsx`,
  `OverviewTab.jsx`, `ApplicationsTab.jsx`, `ReportsTab.jsx`,
  `ClustersTab.jsx`, `GovAlertsTab.jsx`, `UsersTab.jsx`.

=====================================================================
4. ANDROID APP
=====================================================================

STRUCTURE
- All Kotlin in `core/` (library `:core`); `app-farmer`/`app-vet` are thin
  launchers. Role is decided by package name:
  `SessionManager.appRole` (`core/src/main/java/com/pashurakshak/app/data/SessionManager.kt:88`)
  = VET iff packageName == "com.pashurakshak.vet", else FARMER.

SCREENS (path prefix `core/src/main/java/com/pashurakshak/app/`)

Login — `ui/auth/LoginScreen.kt` + `LoginViewModel.kt`
- Phone (+91 prefix) + credential field ("6-digit PIN" farmer / "PIN or
  password" vet). "Create account" only on farmer. "Forgot PIN?" both.
- Calls POST `/api/v1/auth/farmer/login` (`data/AuthRepository.kt:63-70`) or
  `/api/v1/auth/vet/login` with client:"app" (`:106-113`).
- On success `establishSession` (`:154-176`); profileComplete = profile has
  village (`:160`); default language "hi" (`:168`); registers FCM token
  (`:207`); inserts local farmers/vets row (`:185-211`).

Register (farmer only) — `ui/auth/RegisterScreen.kt` + VM
- 3 steps CONTACT -> OTP -> DETAILS (`RegisterViewModel.kt:35`). Phone,
  email, OTP, then name/PIN/confirm/animal count/village/pincode +
  LanguageSelector.
- Calls POST `/api/v1/auth/otp/send` (purpose "farmer_register"),
  `/api/v1/auth/otp/verify`, `/api/v1/auth/farmer/register`
  (`AuthRepository.kt:126-149`, `:72-93`). Cooldown timer `:180-190`.

Forgot PIN — `ui/auth/ForgotPinScreen.kt` + VM
- Email -> OTP -> new PIN. purpose = "vet_reset_pin" or "farmer_reset_pin"
  (`ForgotPinViewModel.kt:42-43`). Calls otp/send, otp/verify, then
  POST `/api/v1/auth/farmer/reset-pin` or `/api/v1/auth/vet/reset-pin`
  (`AuthRepository.kt:95-102`, `:115-122`).

Onboarding — `ui/OnboardingScreen.kt` + VM
- Name/email/animal count/village/pincode + language; submit posts
  `/api/v1/core/users` then `/api/v1/pashu-health/farmer-profiles`
  (`OnboardingViewModel.kt:27-70`, `FarmerRepository.kt:130-167`, `:169-204`).
- Shown when logged in but profileComplete == false.

Farmer Home — `ui/HomeScreens.kt:88` + `HomeViewModel.kt`
- Greeting, stat cards (animals/reports/active), tiles: Report sick
  animal, Vaccination, My reports, My animals; debug B2 test tile gated by
  `BuildConfig.DEBUG` (`HomeScreens.kt:234-259`); unread-alert badge;
  rotating DAILY_TIPS (`:70-86`); vaccinations due within 30 days
  (`HomeViewModel.kt:65-84`); local-first then `RemoteSync.pullAll()`
  (`:41-63`).

Vet Home — `ui/HomeScreens.kt:588` + `VetHomeViewModel.kt`
- In queue / High risk / Examined counts, tiles: Case queue, Outbreak
  alerts, Profile. Same local-first + pull pattern (`:35-55`).

Report sick animal — `ui/farmer/ReportSickAnimalScreen.kt` + VM
- Animal dropdown, symptom FilterChips, up to 5 photos (camera via
  FileProvider+TakePicture, or gallery bounded-decode to 2048px JPEG q85,
  MaxPhotos=5 `data/local/SymptomReport.kt:58`), village, lat/long with
  "Use current location" (play-services-location), risk preview, post-submit
  AI dialog.
- Risk score is computed CLIENT-SIDE placeholder table
  (`ReportSickAnimalViewModel.kt:23-37`).
- submit() (`:188-270`) inserts report locally, inserts local alerts, posts
  directly via `ReportPushApi`, on failure falls back to
  `SyncScheduler.triggerNow` (`:250-266`).
- `ReportPushApi.kt:30-53` POSTs `/api/v1/pashu-health/reports` and parses
  `aiAdvisory` from the response.

My animals — `ui/farmer/MyAnimalsScreen.kt` + VM
- List with species emoji + health badge; add/edit dialog; delete confirm.
  `addAnimal` generates `QR-<UUID>` (`MyAnimalsViewModel.kt:76-93`); edits/
  deletes push via `RemoteSync` (`:95-117`).

My reports — `ui/farmer/MyReportsScreen.kt` + VM
- Cards with risk stripe, status chip, symptoms, thumbnail; tap opens
  AiInsight chat (`AppNavHost.kt:270-278`).

Vaccinations — `ui/farmer/VaccinationStatusScreen.kt` + VM
- Overdue styling; add/delete/edit push to backend
  (`VaccinationStatusViewModel.kt:79-141`); alert insertion on add.

Alerts — `ui/farmer/AlertsScreen.kt` + VM
- Unread highlight, tap-to-read (`AlertsViewModel.kt:52-68`); supports both
  recipient-specific (farmer) and role-wide (vet) queries.

QR passport (farmer) — `ui/farmer/QrPassportScreen.kt` + VM
- Displays animal passport banner + QR bitmap via ZXing `BarcodeEncoder`
  (`QrPassportScreen.kt:132-143`) + vaccination summary. Generation only —
  farmer does NOT scan.

Vet case queue — `ui/vet/VetCaseQueueScreen.kt` + VM
- Queue of REPORTED / VET_ASSIGNED sorted by risk (`VetCaseQueueViewModel.kt:81-98`);
  "Scan QR" (auto-opens scanner on case detail) and "Examined"
  (`:45-59`) actions.

Case detail (vet) — `ui/vet/CaseDetailScreen.kt` + VM
- Risk card, "Why this score?" breakdown (client placeholder,
  `CaseDetailScreen.kt:616`), QR scan button, assessment chips
  (risky/moderate/mild), location fields, submit.
- `submitVisit` POSTs `/api/v1/pashu-health/visits`, REQUIRES response
  `matched == true` (`CaseDetailViewModel.kt:179-267`), then marks report
  EXAMINED, sets assignedVetId, triggers sync (`:234`), inserts farmer alert.
- Known issue: `canSubmitVisit` (`CaseDetailViewModel.kt:51-57`) requires
  `assessmentText.isNotBlank()`, but the screen never wires a field to
  `onAssessmentTextChanged` — submit appears un-enableable as built.

AI insight — `ui/aiInsight/AiInsightScreen.kt` + VM
- Chat UI with markdown renderer; GET then word-by-word reveal of POST reply
  (`AiInsightViewModel.kt:67-198`). Calls
  GET/POST `/api/v1/pashu-health/reports/{id}/chat` (`:72`, `:176`).

Profile — `ui/profile/ProfileScreen.kt` + VM
- View/edit name/email/village/pincode/animal count/language; phone read-only;
  sign out. Calls GET `/api/v1/core/users/me`,
  GET `/api/v1/pashu-health/farmer-profiles/me`,
  PUT `/api/v1/core/users/me`, POST farmer-profiles
  (`ProfileViewModel.kt:50-219`).

Debug — `ui/debug/B2UploadTestScreen.kt`
- Lists reports with pending photo upload, "upload all"/"upload one", shows
  credential configured status. Route always registered
  (`AppNavHost.kt:297-299`), tile only shown in DEBUG.

NAVIGATION FLOW (`navigation/Screen.kt`, `ui/navigation/AppNavHost.kt`)
- Start destination (`:69-76`): not logged in -> `login`; role VET ->
  `vet_home`; farmer profile complete -> `farmer_home`; else `onboarding`.
- login -> (vet) vethome | onboarding | farmerhome, popping login.
- register -> farmerhome; forgotpin -> same split; onboarding -> farmerhome.
- farmerhome -> my_reports | my_animals | report_sick_animal |
  vaccination_status | alerts | profile | b2_upload_test; its "open QR scan"
  callback actually goes to MyAnimals (`:180`).
- my_animals -> qr_passport/{animalId}. my_reports -> ai_insight/{reportId}.
- vethome -> vet_case_queue | vet_alerts | profile.
- vet_case_queue -> case_detail/{reportId}/{scanQrAuto}.
- vet_alerts -> AlertsScreen(recipientRole="vet", recipientId=null).
- Floating role bottom bars on main tabs (`FarmerBottomBar.kt`,
  `VetBottomBar.kt`); hidden on auth/onboarding/qr/case detail/ai/b2 routes.
- Notification tap: MainActivity sets `destination=alerts` extra; NavHost
  effect routes to Alerts (farmer) or VetAlerts (vet) (`:79-98`).

LOCAL STORAGE (offline-first)
- Embedded libSQL file `pashurakshak.db` (`data/local/TursoClient.kt:92-94`),
  Mutex-serialized, `PRAGMA foreign_keys=ON`. No remote Turso replication —
  doc says "Remote Turso sync will be added in a later phase" (`:17`);
  implemented sync is REST via `RemoteSync`.
- Local tables (`data/local/Migrations.kt`): animals, vaccinations,
  symptom_reports (incl. assigned_vet_id, ai_risk_category, vet_assessment,
  village), vets, alerts, pashu_farmer_profiles, pashu_visit_verifications,
  farmers.
- Session in SharedPreferences `pashurakshak_session`: token, uid, phone,
  role, name, email, preferredLanguage, animalCount, village, pincode,
  profileComplete (`SessionManager.kt:21-32`). Sessions without backend token
  cleared at init (`:100-105`).

OFFLINE QUEUE + AUTO-SYNC
- Queue = `symptom_reports.synced = 0` rows (`ReportRepository.kt:66-70`)
  plus photo-pending rows (`photo_local_path` non-empty, remote URL empty,
  `:72-85`).
- SyncWorker cycle (`data/sync/SyncWorker.kt:28-99`): 1) upload pending
  photos to B2, 2) re-push synced reports with new photos, 3) push unsynced
  reports via ReportPushApi (success sets synced=1), 4) RemoteSync.syncAll()
  (push animals/vaccinations/alerts + pull all), 5) always Result.success()
  (no WorkManager backoff — retries next cycle).
- Triggers: app start one-shot + periodic 15 min on CONNECTED network
  (`SyncScheduler.kt:23-60`), NetworkReconnectObserver onAvailable
  (`NetworkReconnectObserver.kt:29-34`), after visit submit
  (`CaseDetailViewModel.kt:234`), after report push failure
  (`ReportSickAnimalViewModel.kt:250-266`).
- RemoteSync pull coalescing: 4 s min interval + pullMutex (`RemoteSync.kt:39-42`).
- `SyncStatusBanner` shows "N reports pending sync"/"Syncing…" (refreshes
  pending count every 30 s).

LANGUAGE
- `LanguageSelector` (`ui/components/AppComponents.kt:588-629`): "hi"/"en"
  segmented pill, used in Register DETAILS, Onboarding, Profile.
- Stored in SessionManager + sent as preferredLanguage; read back as
  preferred_language. Default "hi".
- IMPORTANT: no Android resource localization — all UI strings hardcoded in
  Kotlin; the toggle does NOT translate any screen. It only affects the
  language the backend AI replies in (aiAdvisory passes `preferred_language`
  into the system prompt).

PUSH NOTIFICATIONS
- `PushMessagingService.onMessageReceived` builds a local notification from
  data-only `{title, body}` payloads (`data/remote/PushMessagingService.kt:22-30`).
- Channel "alerts", default via manifest metadata; tap opens Alerts
  (`notifications/AppNotifications.kt`).
- Token registration POSTs `/api/v1/core/devices`
  (`AuthRepository.kt:216-264`); silent no-op when google-services.json
  absent. Vet APK currently HAS no google-services.json -> no FCM in vet app.

PHOTO UPLOAD FLOW
- Capture/gallery -> local JPEG (<=2048px, q85) in filesDir/photos.
- SyncWorker phase 1 calls `B2UploadService.uploadReportPhotos`:
  b2_authorize_account -> b2_list_buckets -> b2_get_upload_url ->
  POST file to `reports/{reportId}/{filename}` with SHA-1
  (`data/remote/B2UploadService.kt:83,:190-255`).
- Retries: 3 attempts, exponential 1s/2s/4s (`:284-332`); immediate Failure
  on B2HttpException; offline IOException -> Skipped.
- Private bucket downloads use cached `b2_get_download_authorization` token
  (12 h, `:162-187,:318`) appended as `?Authorization=`.
- RemoteSync re-pushes report with photo_remote_url; server stores it.

QR SCANNING FLOW
- Client: ZXing embedded `CaptureActivity`, `MODE=QR_CODE`
  (`CaseDetailScreen.kt:290-310`), launched from queue "Scan QR"
  (scanQrAuto=true) or case detail button; result ->
  `CaseDetailViewModel.onQrScanned` -> submitVisit POSTs /visits and
  requires matched==true. Farmer side: QR only generated for display.

=====================================================================
5. BACKEND API
=====================================================================

Base: `/api/v1`. `X-App-Key` required on ALL of it (enforced per-router in
`routes.js`; `/core` routes enforce app key inside each route where noted,
in practice /core mounts are NOT behind a router-level verifyAppKey — the
admin routes use X-Admin-Secret, app key is required inside devices/users/
pincode? See per-row notes below).

AUTH LEGEND: [appkey] = X-App-Key; [session] = Bearer token; [admin] =
X-Admin-Secret; role = farmer|vet|gov when enforced.

CORE (`/api/v1/core`)

- POST /core/apps [admin] — body {name}; 201 {id,name,apiKey (shown once),
  message,keyShownOnce,createdAt}. File `core/routes/apps.js:13`.
- GET /core/apps [admin] — {apps:[{id,name,created_at}]} (no keys).
  `apps.js:65`.
- POST /core/devices [appkey + session] — body {role, fcmToken}; upserts
  device_tokens by (app_id, uid, role); 200 {success, device}.
  `core/routes/devices.js:23`.
- POST /core/users [appkey + session] — body {phone, role, name?, email?,
  preferredLanguage?}; upsert by phone; 200 {user} (pin_hash stripped).
  `core/routes/users.js:27`.
- GET /core/users/me [appkey + session] — by session sub; {user} or
  404 {exists:false}. `users.js:86`.
- PUT /core/users/me [appkey + session] — partial {name,email,
  preferredLanguage}; {user} or 404. `users.js:116`.
- GET /core/pincode/:code [PUBLIC — no app key] — proxies api.postalpincode.in;
  {pincode, offices:[{name,branchType,deliveryStatus,district,state,
  areaName,pincode}]}; 404 pincode_not_found. `core/routes/pincode.js:11`.
- POST /core/admin/magistrates [admin] — upsert gov_magistrates by email;
  {magistrate}. `adminMagistrates.js:34`.
- GET /core/admin/magistrates [admin] — list. `:80`.
- DELETE /core/admin/magistrates/:id [admin] — delete. `:91`.

AUTH (`/api/v1/auth`, all [appkey])  (`core/routes/auth.js`)

- POST /auth/otp/send — {email, purpose, phone?}; purpose in
  vet_register|farmer_register|farmer_reset_pin|vet_reset_pin (gov_login
  rejected). 200 {success, email(masked), purpose, expiresInSeconds,
  cooldownSeconds, retryAfterSeconds?, message}. Rate limit: 1/60s and
  5/hour per (email,purpose) or 429/429-ish via {retryAfterSeconds}.
  `:97`.
- POST /auth/otp/verify — {email, purpose, code}; 200 {success,
  verifyToken (10 min), email}. `:132`.
- POST /auth/farmer/check — {phone}; {exists, hasCredential,
  requiresPinSetup, hasEmail}. `:334`.
- POST /auth/farmer/login — {phone, pin}; 200 {token, user, profile};
  403 pin_not_set; 401 invalid_credential. `:360`.
- POST /auth/farmer/register — {verifyToken(farmer_register), phone, pin,
  name, preferredLanguage?, animalCount?, village, pincode}; creates/updates
  user + pashu_farmer_profiles; {token, user, profile}. `:397`.
- POST /auth/farmer/reset-pin — {verifyToken(farmer_reset_pin), pin};
  {token, user, profile}. `:499`.
- POST /auth/vet/setup-pin — {verifyToken(vet_register), phone, pin,
  pinType?}; upsert vet user, email_verified=1; {token, user}. `:163`.
- POST /auth/vet/login — {phone, credential, client:'app'|'web'}; 'app'
  requires applicationStatus=='approved' else 403 {error:'not_approved',
  applicationStatus, reviewNote}; {token, user, applicationStatus,
  application}. `:241`.
- POST /auth/vet/reset-pin — {verifyToken(vet_reset_pin), pin, pinType?};
  {token, user}. `:292`.
- POST /auth/gov/request-otp — {email}; same success shape whether or not
  the magistrate exists (no enumeration). `:541`.
- POST /auth/gov/verify — {email, code}; {token (role 'gov'), magistrate}.
  `:573`.

GOV (`/api/v1/gov`, [appkey] + session role gov)  (`core/routes/gov.js`)
- GET /gov/overview — platform counts + district-scoped application counts,
  activeClusters via getActiveClusters. `:70`.
- GET /gov/vet-applications?status=pending|approved|rejected|all — rows for
  magistrate's district (LOWER(TRIM()) match; '*'/'all'/'' sees all);
  {applications, district}. `:127`.
- POST /gov/vet-applications/:id/approve — {note?}; district-checked,
  409 already_reviewed. `:217`.
- POST /gov/vet-applications/:id/reject — {note}; same. `:220`.
- GET /gov/reports — all reports joined with animal species/name/qr. `:226`.
- GET /gov/clusters — getActiveClusters(). `:244`.
- GET /gov/gov-alerts — all, created_at DESC. `:255`.
- POST /gov/gov-alerts/:id/acknowledge — sets acknowledged=1. `:266`.
- GET /gov/users — farmers (+profile) and vets (+profile+application
  status/district). `:283`.

PASH-HEALTH (`/api/v1/pashu-health`, router-level [appkey])

Reports (`modules/pashu-health/routes/reports.js`)
- POST /reports [appkey+session] — idempotent upsert by id. Body: id*,
  animalId*|animal_id, farmerId (default req.userId), symptoms (JSON string),
  photoLocalPath, photoRemoteUrl, latitude*, longitude*, riskScore,
  riskBreakdown (JSON string), status (enum reported|vet_assigned|examined|
  sample_sent|confirmed|resolved), createdAt, village?, assignedVetId?,
  vetAssessment?. Auto-creates placeholder animal if missing
  (`QR-PENDING-<id>`). After save runs detectClusterForReport,
  categorizeAndAssign, generateAdvisory. 200 {success, aiAdvisory}.
  `:57` (logic :102-159, post-save :161-213).
- GET /reports — all, created_at DESC. `:226`.
- GET /reports/:id — one or 404. `:239`.

Animals (`routes/animals.js`)
- POST /animals — create/upsert {ownerFarmerId*, species*, name*,
  qrCodeId*} (id server-generated if omitted... actually client sends id;
  duplicate qrCodeId 409). `:24`.
- GET /animals — list. `:70`. GET /animals/:id — one. `:81`.
- PUT /animals/:id — full update, 409 duplicate qr. `:99`.
- DELETE /animals/:id — `:149`.

Vaccinations (`routes/vaccinations.js`)
- POST /vaccinations — upsert {animalId*, vaccineName*, dateGiven*,
  nextDue*}. `:20`. GET / `:72`. GET /:id `:85`. DELETE /:id `:103`.

Alerts (`routes/alerts.js`)
- POST /alerts — {recipientRole*, recipientId*, message*}. `:24`.
- GET / `:64`. GET /:id `:75`. PUT /:id (partial, e.g. read) `:93`.
  DELETE /:id `:131`.

Clusters (`routes/clusters.js`)
- GET /clusters — active clusters from getActiveClusters(). `:7`.

Farmer profiles (`routes/farmerProfiles.js`) [appkey+session]
- POST /farmer-profiles — {animalCount*, village*, pincode*} upsert by
  session user. `:24`.
- GET /farmer-profiles/me — {profile} or 404 {exists:false}. `:88`.

Vet profiles (`routes/vetProfiles.js`)
- POST /vet-profiles [session] — {pincode*, serviceAreas:string[]*}. `:19`.
- GET /vet-profiles/me [session]. `:84`.

Vet applications (`routes/vetApplications.js`) [appkey+session]
- POST /vet-applications — {fullName*, qualification*, licenseNumber*,
  experienceYears?, clinicName?, address*, village*, pincode*6digits,
  serviceAreas:string[]*}; resolves district/state via
  api.postalpincode.in (`core/services/pincode.js`); upserts
  vet_applications + pashu_vet_profiles. `:42` (validation :57-61,
  district resolve :78-82).
- GET /vet-applications/me — own application or {application:null}. `:166`.

Vets available (`routes/vetsAvailable.js`)
- GET /vets and GET /vets/available?area= — vets whose service_areas LIKE
  %area%. `:45-46` (SQL LIKE :31).

AI chat (`routes/aiChat.js`)
- POST /reports/:id/chat [session] — {message*}; uses report's farmer_id +
  caller's preferred_language; full history saved, last 20 msgs sent to
  Groq. 200 {reply}. `:21`.
- GET /reports/:id/chat [session] — FULL stored history. 200 {messages}.
  `:75`.

Visits (`routes/visits.js`) [appkey+session]
- POST /visits — {reportId*, scannedQrCodeId, latitude, longitude,
  assessment* risky|moderate|mild}. 403 if session user != assigned vet
  (unless assigned_vet_id null). matched = scannedQrCodeId === animal
  qr_code_id. Sets reports.vet_assessment + status='examined'. assessment
  'risky' -> pashu_gov_alerts severity 'high'. 200 {success, visit}. `:35`.

Government alerts (`routes/govAlerts.js`)
- GET /gov-alerts — all rows DESC. `:11`.

MIDDLEWARE SUMMARY
- verifyAppKey: X-App-Key must match apps.api_key -> req.appId; 401.
  `core/middleware/verifyAppKey.js:9-33`.
- verifySession: Bearer HMAC token (typ 'session') -> req.session/req.userId/
  req.magistrateId/req.role; 401; requireRole(...roles) -> 403.
  `core/middleware/verifySession.js:14-53`.
- adminSecret: X-Admin-Secret == env.ADMIN_SECRET; 401. `:adminSecret.js`.
- Error handling: express.json 2mb limit; invalid JSON -> 400 invalid_json;
  404 handler; 500 internal_error handler; async route errors mostly
  try/catch -> console.error + 500 JSON.
- CORS: allow no-Origin, "*" in allow-list, or listed origins; else no CORS
  header. `app.js:8-24`.
- Rate limiting: NO general API rate limiter. Only OTP send limits
  (1 send/60s, 5 sends/hour per email+purpose) `core/services/otp.js:60-90`.
- Logging: console.log/warn/error only; OTP debug page `/otp` (see env).
- Validation: manual per-route checks; no schema library.

<!-- PART 3 FIXED -->
=====================================================================
6. DATABASE
=====================================================================

Engine: Turso/libSQL (SQLite dialect), via @libsql/client. All ids TEXT
(UUID strings), timestamps INTEGER epoch ms, booleans INTEGER 0/1.
Migrations run by `shared-backend/src/db/migrate.js` (tracked in table
`migrations_applied`), executed on `npm start` or `npm run migrate`.

TABLES (from `shared-backend/src/db/migrations/*.js`):

apps (000_core, version 1)
- id TEXT PK, name TEXT NOT NULL, api_key TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL.
- Purpose: registered client apps; verifyAppKey matches api_key.

device_tokens (000_core)
- id TEXT PK, app_id TEXT NOT NULL REFERENCES apps(id), uid TEXT, role TEXT,
  fcm_token TEXT, updated_at INTEGER.
- Purpose: FCM push targets (cluster alerts pushed to all vet rows per app).

pashu_animals (001_pashu_health, version 2)
- id TEXT PK, owner_farmer_id TEXT NOT NULL, species TEXT NOT NULL,
  name TEXT NOT NULL, qr_code_id TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL.

pashu_vaccinations (001)
- id TEXT PK, animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
  vaccine_name TEXT NOT NULL, date_given INTEGER NOT NULL,
  next_due INTEGER NOT NULL.

pashu_symptom_reports (001, extended by 004)
- id TEXT PK, animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
  farmer_id TEXT NOT NULL, symptoms TEXT NOT NULL (JSON string),
  photo_local_path TEXT NOT NULL, photo_remote_url TEXT,
  latitude REAL NOT NULL, longitude REAL NOT NULL,
  risk_score INTEGER NOT NULL, risk_breakdown TEXT NOT NULL (JSON string),
  status TEXT NOT NULL CHECK IN (reported, vet_assigned, examined,
  sample_sent, confirmed, resolved), synced INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL.
- 004 adds: assigned_vet_id TEXT, ai_risk_category TEXT,
  vet_assessment TEXT, village TEXT.

pashu_vets (001)
- id TEXT PK, name TEXT NOT NULL, phone TEXT NOT NULL,
  assigned_village TEXT NOT NULL. Purpose: legacy/local vet cache shape.

pashu_alerts (001)
- id TEXT PK, recipient_role TEXT NOT NULL, recipient_id TEXT NOT NULL,
  message TEXT NOT NULL, read INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL. Cluster + assignment alerts land here.

pashu_farmers (001)
- id TEXT PK, phone TEXT NOT NULL, created_at INTEGER NOT NULL.

users (002_users, version 3)
- id TEXT PK, phone TEXT UNIQUE, firebase_uid TEXT UNIQUE (legacy),
  role TEXT CHECK IN (farmer, vet, admin), name TEXT, email TEXT,
  preferred_language TEXT DEFAULT 'hi', created_at, updated_at INTEGER NOT
  NULL.
- 007 adds: pin_hash TEXT, pin_type TEXT CHECK IN (pin, password),
  email_verified INTEGER NOT NULL DEFAULT 0.

pashu_farmer_profiles (003_pashu_profiles, version 4)
- user_id TEXT PK REFERENCES users(id), animal_count INTEGER, village TEXT,
  pincode TEXT, created_at INTEGER NOT NULL.

pashu_vet_profiles (003)
- user_id TEXT PK REFERENCES users(id), pincode TEXT,
  service_areas TEXT NOT NULL (JSON array string), created_at, updated_at.

pashu_ai_responses (005_ai_tables, version 6)
- id TEXT PK, report_id TEXT NOT NULL REFERENCES pashu_symptom_reports(id),
  farmer_id TEXT NOT NULL REFERENCES users(id), response_text TEXT NOT NULL,
  model TEXT NOT NULL, created_at INTEGER NOT NULL.

pashu_ai_conversations (005)
- id TEXT PK, report_id TEXT NOT NULL REFERENCES reports(id),
  farmer_id TEXT NOT NULL REFERENCES users(id), full_history TEXT NOT NULL
  (JSON), created_at, updated_at INTEGER NOT NULL.

pashu_visit_verifications (006_visits_and_gov_alerts, version 7)
- id TEXT PK, report_id REFERENCES reports(id), vet_id REFERENCES users(id),
  scanned_qr_code_id TEXT, matched INTEGER, latitude REAL, longitude REAL,
  verified_at INTEGER NOT NULL.

pashu_gov_alerts (006)
- id TEXT PK, report_id REFERENCES reports(id),
  severity TEXT NOT NULL CHECK IN (high, medium, low),
  message TEXT NOT NULL, acknowledged INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL.

auth_otps (007_auth_and_vet_applications, version 8)
- id TEXT PK, email TEXT NOT NULL, purpose TEXT NOT NULL, phone TEXT,
  code_hash TEXT NOT NULL (sha256 of sessionSecret:email:code),
  attempts INTEGER NOT NULL DEFAULT 0, expires_at INTEGER NOT NULL,
  consumed INTEGER NOT NULL DEFAULT 0, created_at INTEGER NOT NULL.
- Index: idx_auth_otps_email_purpose ON (email, purpose, created_at).

vet_applications (007)
- id TEXT PK, user_id TEXT NOT NULL UNIQUE REFERENCES users(id),
  status TEXT NOT NULL DEFAULT 'pending' CHECK IN (pending, approved,
  rejected), full_name, phone, email, qualification NOT NULL,
  license_number NOT NULL, experience_years INTEGER, clinic_name,
  address NOT NULL, village NOT NULL, pincode NOT NULL, district NOT NULL,
  state, service_areas TEXT NOT NULL (JSON), review_note, reviewed_by,
  reviewed_at INTEGER, created_at, updated_at INTEGER NOT NULL.
- Index: idx_vet_applications_district_status ON (district, status).

gov_magistrates (007)
- id TEXT PK, email TEXT NOT NULL UNIQUE, name TEXT NOT NULL,
  district TEXT NOT NULL ('*' allowed for all districts), state,
  created_at INTEGER NOT NULL.

MIGRATIONS SUMMARY
- 000_core.js (v1): apps, device_tokens.
- 001_pashu_health.js (v2): pashu_animals/vaccinations/symptom_reports/
  vets/alerts/farmers.
- 002_users.js (v3): users.
- 003_pashu_profiles.js (v4): pashu_farmer_profiles, pashu_vet_profiles.
- 004_report_ai_fields.js (v5): adds assigned_vet_id, ai_risk_category,
  vet_assessment, village to symptom_reports.
- 005_ai_tables.js (v6): pashu_ai_responses, pashu_ai_conversations.
- 006_visits_and_gov_alerts.js (v7): pashu_visit_verifications,
  pashu_gov_alerts.
- 007_auth_and_vet_applications.js (v8): users.pin_hash/pin_type/
  email_verified, canonicalizes phone (strips +91/0), auth_otps,
  vet_applications (+index), gov_magistrates.

RELATIONSHIPS (plain text)
- apps 1:N device_tokens (app_id -> apps.id).
- users 1:1 pashu_farmer_profiles (user_id PK/FK), 1:1 pashu_vet_profiles.
- users 1:1 vet_applications (user_id UNIQUE FK).
- pashu_animals 1:N pashu_vaccinations (animal_id FK);
  pashu_animals 1:N pashu_symptom_reports (animal_id FK).
- pashu_symptom_reports 1:N pashu_ai_responses (report_id FK),
  1:N pashu_ai_conversations, 1:N pashu_visit_verifications,
  1:N pashu_gov_alerts.
- pashu_symptom_reports.assigned_vet_id -> users.id (logical FK, no FK
  constraint). device_tokens.uid -> users.id (logical).
- farmers (pashu_farmers) and pashu_vets tables are legacy/cache shapes;
  the live farmer/vet identity is users + profiles.

=====================================================================
7. CORE AI AND LOGIC (exact)
=====================================================================

A) RISK CATEGORIZATION — rule-based, NO AI prompt involved.
File: shared-backend/src/modules/pashu-health/services/clusterDetection.js
Function categorizeAndAssign, lines 133-192.
Inputs: report.riskScore (number, default 0), report.clustered (bool from
cluster detection result), report.village (string|null).
Thresholds / weights (exact, lines 138-145):
- category = 'high' if riskScore >= 60 OR report was flagged in a cluster.
- else 'mid' if riskScore >= 30.
- else 'low'.
The Android client computes the same 60/30 bands and a placeholder symptom
point table (ReportSickAnimalViewModel.kt:23-37, ui/vet/RiskLevel.kt:18-22
commented "Placeholder thresholds — refined server-side later").
How it is applied: UPDATE pashu_symptom_reports SET ai_risk_category
(:148-151). If no village -> warning, assigned_vet_id left null (:153-156).

B) VET AUTO-ASSIGNMENT — exact rules.
File: clusterDetection.js:158-191.
Query: SELECT u.id FROM users u JOIN pashu_vet_profiles v ON u.id=v.user_id
LEFT JOIN vet_applications a ON a.user_id=u.id WHERE u.role='vet' AND
(a.status='approved' OR a.id IS NULL) AND v.service_areas LIKE %village%
LIMIT 1 (:158-167).
Exact rules: first row only, no ORDER BY (arbitrary match among vets whose
service_areas JSON text contains the village substring — substring, not
token match); must have an approved application or no application row.
If found: reports.assigned_vet_id = vetUserId (:177-180) and insert
pashu_alerts row recipient_role='vet', recipient_id=vetUserId, message
"AI risk <category> — report <id> in <village> assigned to vet" (:182-188).
If not found: warning logged, no assignment, catch-all swallows errors
(:169-171, :189-191). NO proximity/distance, NO caseload balancing, NO
explicit availability flag — those are NOT implemented. The README's
wording "attempts to assign a vet whose service_areas contains the village"
is accurate; claims of priority/coverage-distance logic do not exist.

C) CLUSTER DETECTION — exact algorithm.
File: clusterDetection.js:45-119 (detectClusterForReport) and :221-286
(getActiveClusters).
Constants (:13-16): WINDOW_MS = 7*24*60*60*1000 (7 days), BBOX_DEG = 0.05,
RADIUS_KM = 5, MIN_REPORTS = 3.
Steps:
1. Skip if lat/lon not finite.
2. Candidate SQL pre-filter (:55-69): reports with created_at >= now-7d,
   id != trigger, latitude BETWEEN lat±0.05, longitude BETWEEN lon±0.05.
3. Haversine refine (:71-73): keep rows with true distance <= 5 km
   (Earth radius 6371; :21-28).
4. involvedIds = trigger + nearby ids; if count < 3 -> not clustered
   (:75-78).
5. On cluster: deterministic message "Disease cluster detected: N reports
   within 5 km of (lat, lng) in the last 7 days. Field check recommended."
   (rounded to 3 decimals, :81-83); dedupe — reuse existing pashu_alerts
   row with same message created within the window (:85-102); otherwise
   INSERT pashu_alerts recipient_role='vet', recipient_id='all-vets',
   message, read=0 and best-effort FCM push to every device_tokens row
   with role='vet' for this appId (:195-215).
6. UPDATE reports SET status='vet_assigned' WHERE status='reported' AND
   id IN (involvedIds) (:104-109).
getActiveClusters (:221-286): greedy single-linkage grouping over all
non-resolved reports from last 7 days (any member within 5 km extends the
group); groups with >= 3 become clusters: {reportCount, reportIds,
centerLat/centerLng (mean), radiusKm:5, windowDays:7, firstReportAt,
lastReportAt, statuses}.
This `clustered` boolean then feeds rule (A) above (any clustered report
becomes category 'high' even if riskScore < 60).

D) QR VISIT VERIFICATION — exact flow.
Generation: client-side; animal created with qrCodeId = "QR-<UUID>"
(MyAnimalsViewModel.kt:76-93); stored on pashu_animals.qr_code_id; farmer
display QR encodes that id (QrPassportScreen.kt:132-143).
Scan: vet app scans via ZXing CaptureActivity (CaseDetailScreen.kt:290-310),
captured id -> CaseDetailViewModel.onQrScanned (:150-177), then submitVisit
(:179-267) POSTs POST /api/v1/pashu-health/visits (backend
routes/visits.js:35-~140) with {reportId, scannedQrCodeId, latitude,
longitude, assessment in risky|moderate|mild}.
Verification (visits.js):
- Requires report to exist (join pashu_animals for qr_code_id); 404 else.
- 403 if report.assigned_vet_id NOT NULL and != session user (fallback
  allowed when null).
- matched = 1 if scannedQrCodeId === pashu_animals.qr_code_id else 0.
- INSERT pashu_visit_verifications (id, report_id, vet_id,
  scanned_qr_code_id, matched, latitude, longitude, verified_at).
- UPDATE pashu_symptom_reports SET vet_assessment=assessment,
  status='examined'. Client-side also REQUIRES matched==true, else shows
  "QR code did not match" and aborts (CaseDetailViewModel.kt:221-224).
- assessment === 'risky' -> INSERT pashu_gov_alerts {severity:'high',
  message "Gov alert: <species> in <village> flagged as risky — field
  visit verified by vet", acknowledged:0}. moderate/mild produce NO gov
  alert.
Note: backend does not enforce matched=1 (it records matched=0 and still
sets status='examined'); enforcement lives only in the Android client.

E) AI ADVISORY & CHAT — Groq.
File: shared-backend/src/modules/pashu-health/services/aiAdvisory.js.
Provider/model: Groq, model 'openai/gpt-oss-120b' (:8). NOTE README says
llama-3.3-70b-versatile — stale.
Params: temperature 0.7, max_tokens 16384, reasoning_effort 'low' (:62-64).
System prompt (exact, :17-27):
"You are a livestock health advisory assistant for a farming app called
PashuRakshak.

Your role: give general preventive and first-aid guidance only.

Guidelines:
- Suggest isolation of the affected animal, ensuring hydration, limiting
  herd contact, and maintaining hygiene.
- NEVER prescribe specific medications, dosages, or treatment protocols.
- Always close by saying: \"A vet will follow up shortly. This is not a
  diagnosis.\"
- Use simple language a farmer who may not be highly literate can
  understand.

Language: respond entirely in Hindi if the farmer's preferred language is
'hi', otherwise respond entirely in English."
Each request appends "\n\nRespond in <Hindi|English>." (:58, :136).
User prompt (:29-40): "Here are the details of a reported case:\n\nSpecies:
<...>\nSymptoms: <...>\nRisk category: <...>\nVillage: <...>\nRisk score:
<...>\nRisk breakdown: <...>\n\nPlease provide general preventive and
first-aid guidance based on the information above."
Advisory generation is triggered automatically inside POST /reports after
cluster/categorize (reports.js:186-210): looks up user's preferred_language,
builds report object, calls generateAdvisory; result saved to
pashu_ai_responses (:70-74) and returned as `aiAdvisory` in the POST
response (reports.js:215). On Groq failure -> null, report save still
succeeds. Also seeds pashu_ai_conversations with the assistant reply
(:76-81).
Chat (continueConversation, :101-182): loads pashu_ai_conversations row by
(report_id, farmer_id), appends user message to FULL history (persisted
even if Groq fails), sends system prompt + LAST 20 messages to Groq
(:134) — the 20-message cap is the only context/cost control (no token
counter, no per-user rate limit); appends assistant reply (or the
fallback "AI assistance is unavailable right now. Please try again
shortly." :99), persists the FULL history back. GET chat returns the FULL
stored history (routes/aiChat.js:75-102).
Cost-control caps present: 16k max_tokens, reasoning_effort low, last-20
message window. Not present: rate limits, auth on GET chat beyond session,
per-farmer throttling.

F) GOVERNMENT ALERTS — what is stored vs NOT built.
Stored: pashu_gov_alerts rows created ONLY when a vet submits a visit with
assessment 'risky' (routes/visits.js, gov_alerts table, severity 'high',
acknowledged 0). Readable via GET /api/v1/gov/gov-alerts and
GET /api/v1/pashu-health/gov-alerts; acknowledge via
POST /api/v1/gov/gov-alerts/:id/acknowledge.
NOT built: no automatic push/SMS/email to any authority on risky alerts,
no escalation/SLA, no alert geo-fencing/district routing on the alert
itself (district scoping applies only to vet applications), no admin
creation UI for gov alerts, no mobile UI for gov, no analytics pipeline.

G) VET CASEQUEUE / "high risk" FILTER — client-side only.
VetCaseQueueViewModel.kt:81-98 filters local reports to REPORTED/
VET_ASSIGNED and sorts by risk; RiskLevel thresholds 60/30 in
ui/vet/RiskLevel.kt:18-22 are placeholder ("refined server-side later").

=====================================================================
8. VET REGISTRATION WEB PAGE
=====================================================================

Location: `vet-registration/` (React 19.1, Vite 6.3.5, no router; stage
machine in `src/App.jsx:12-23,136-289`). Dev port 5173 (vite.config.js).

Stages: checking -> landing -> register -> otp -> set-credential ->
application -> status; plus sign-in, forgot-start, forgot-otp,
reset-credential. On load, stored token -> GET /core/users/me; existing
application -> status page else application form.

Pages/components (all under vet-registration/src/components/):
- RegisterStart.jsx — phone (+91, 10 digits) + email; then OtpVerify
  auto-sends OTP on mount with purpose 'vet_register'
  (api.js:42-56). Resend cooldown from server cooldownSeconds/
  retryAfterSeconds.
- OtpVerify.jsx — 6-digit code -> verifyOtp -> verifyToken.
- SetCredential.jsx — PIN exactly 6 digits OR password >= 6 chars,
  pinType toggle. -> POST /auth/vet/setup-pin {verifyToken, phone, pin,
  pinType}.
- ApplicationForm.jsx — Step 1: fullName*, qualification*, licenseNumber*,
  experienceYears?, clinicName?, address*, village*, pincode* (6 digits).
  "Find coverage areas" -> GET /api/v1/core/pincode/:code -> checkbox list
  of areas (deduped by areaName||name) (api.js:107-109,
  ApplicationForm.jsx:34-70). Step 2: submit -> POST
  /api/v1/pashu-health/vet-applications {fullName, qualification,
  licenseNumber, experienceYears, clinicName, address, village, pincode,
  serviceAreas:[...selected]} (api.js:88-93). Server resolves
  district/state from pincode via api.postalpincode.in and upserts
  pashu_vet_profiles. Edit-and-resubmit pre-fills from existing
  application (StatusPage -> ApplicationForm existing prop).
- StatusPage.jsx — status badge (pending/approved/rejected/none), full
  profile view incl. district/state and coverage tags, review_note,
  Refresh, Edit & resubmit (when rejected/none), Sign out.
- SignInForm.jsx — phone + PIN/password -> POST /auth/vet/login with
  client:'web' (api.js:66-72). 403 not_approved with applicationStatus
  still routes to status page (SignInForm.jsx:31-33).
- ForgotStart.jsx -> sendOtp(email,'vet_reset_pin'); ResetCredential.jsx
  -> vetResetPin -> POST /auth/vet/reset-pin -> stores token.
- CredentialInput.jsx — shared PIN/password input with toggle.

API client: src/api.js — base URL fallback hardcoded
`https://pashurakshak-ilol.onrender.com`, app key fallback HARDCODED in
source (api.js:1-2, same pattern in gov-portal/src/api.js:1-2; exact key
values omitted here). Token in localStorage `vet_registration_token`
(api.js:4). updateMe() (PUT /core/users/me) exists but is NEVER called —
no profile-edit UI.
NOTE: vet-registration/README.md endpoint table lists legacy paths
(/core/auth/vet/register/send-otp etc.) that do NOT exist in the backend;
actual code calls the /api/v1/auth/* paths above.

=====================================================================
9. AUTH AND SECURITY
=====================================================================

LOGIN (farmers/vets)
- Farmer: POST /auth/farmer/check (phone exists?) -> POST
  /auth/farmer/login {phone, pin} -> scrypt verify against pin_hash ->
  HMAC session token (30-day TTL) + user + farmer profile. 6-digit PIN
  only for farmers.
- Vet: POST /auth/vet/login {phone, credential, client}. Credential is
  PIN (6 digits) or password (>=6 chars), stored scrypt:<salt>:<hash>
  (core/services/credentials.js:8-11, timingSafeEqual verify). client='app'
  blocked unless vet_applications.status=='approved'; client='web' always
  allowed (shows status).
- Gov: POST /auth/gov/request-otp -> otp emailed (same response shape if
  email unknown) -> POST /auth/gov/verify -> gov session token with
  district embedded. District re-read from DB each gov request (gov.js
  loadMagistrateDistrict).

TOKENS
- Stateless HMAC-SHA256 signed tokens, payload {typ:'session', sub, role,
  phone?, email?, district?, exp}, SESSION_TTL_MS = 30 days
  (core/services/sessionToken.js:14-23). Signed with SESSION_SECRET
  (falls back to sha256("pashurakshak-session:"+ADMIN_SECRET) when unset,
  config/env.js:50-53).
- Verify tokens {typ:'verify', purpose, email, phone?, exp}, 10 min TTL,
  used to complete register/reset-pin (auth.js:79-85).
- Stored client-side: SharedPreferences (Android) / localStorage (webs).

OTP
- 6-digit codes from randomInt, SHA-256 hashed at rest with
  sessionSecret:email:code (core/services/otp.js:42-43), 10-min TTL
  (:21), 5 attempts (:24), resend cooldown 60 s and max 5 sends/hour per
  (email, purpose) (:60-90). Delivered via MAIL_MODE=log (console) or
  smtp (nodemailer/Gmail app password). Dev `/otp` HTML page lists codes
  (disabled with OTP_PAGE=false; requires ?key=<ADMIN_SECRET> unless
  OTP_PAGE_OPEN=true) — a debug backdoor; must be off in production.

PASSWORDS / PINs
- scrypt hash, per-password random salt; format "scrypt:<salt>:<hash>".
  PIN rules: exactly 6 digits for pinType 'pin'; password >= 6 chars
  (validateCredential). Stored in users.pin_hash/pin_type.

ROLE CHECKS
- verifySession sets req.role; requireRole(...roles) -> 403
  (verifySession.js:56-65). gov routes: router.use(verifySession,
  requireRole('gov')). Vet app login gated to approved applications.
  NOTE: most pashu-health routes accept ANY authenticated session role —
  a vet token can POST a farmer report, etc. requireRole is applied only
  to gov routes.

INPUT VALIDATION
- Manual checks per route (required fields, enums, pincode 6 digits,
  email format, phone canonicalization in core/services/phone.js).
  No schema/ORM validation layer. SQL is parameterized throughout
  (sql args arrays) — no obvious injection.

RATE LIMITS
- Only OTP send/verify throttling. No global rate limit, no login
  attempt lockout beyond OTP attempt counter and scrypt cost
  (scryptSync, ~100ms — cheap DoS amplification possible on login).

WEAKNESSES / RISKS NOTICED
- Vet APK has no google-services.json -> no FCM; device token registration
  silently no-ops (AuthRepository.kt:216-264).
- Real B2 keys + APP_API_KEY present in local.properties on disk
  (git-ignored but exposed locally); committed hardcoded fallback app keys
  in core/build.gradle.kts:25, vet-registration/src/api.js:2,
  gov-portal/src/api.js:2 — different keys, all prod-looking.
- GET /api/v1/core/pincode/:code is UNAUTHENTICATED (public proxy).
- GET /pashu-health/gov-alerts requires only app key, exposes all gov
  alerts to any holder of the (hardcoded) app key.
- Cluster/categorize/assignment run AFTER upsert without transaction —
  a crash between save and categorize leaves report uncategorized; all
  post-save errors are swallowed by try/catch (reports.js:161-213).
- Auto-assignment has no ORDER BY -> nondeterministic vet pick.
- Backend marks visit 'examined' even when scanned QR did not match
  (matched=0) — only the Android client blocks; a crafted call bypasses.
- Session tokens are stateless — no revocation/logout server-side; 30-day
  lifetime. Vet district changes handled, but role/phone changes via
  users table are not propagated into already-issued tokens.
- /otp debug page + MAIL_MODE=log will print OTP codes to logs; combined
  with `local.db` checked in, dev-grade secrets in repo.
- No helmet/security headers; express.json limit 2mb only.

=====================================================================
10. CONFIGURATION AND DEPLOYMENT
=====================================================================

shared-backend ENV VARS (names + purpose; see shared-backend/.env.example)
- TURSO_DATABASE_URL — libsql:// remote URL or file: local.db (required)
- TURSO_AUTH_TOKEN — Turso token (required)
- PORT — HTTP port (required)
- CORS_ALLOWED_ORIGINS — comma list, or "*" for all (required)
- ADMIN_SECRET — X-Admin-Secret for admin routes (required)
- SESSION_SECRET — HMAC signing secret; derived from ADMIN_SECRET if unset
- MAIL_MODE — 'log' (console) or 'smtp' (default log)
- SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, MAIL_FROM — SMTP config
- FIREBASE_SERVICE_ACCOUNT_JSON — one-line service-account JSON (FCM only)
- B2_APPLICATION_KEY_ID, B2_APPLICATION_KEY, B2_BUCKET_NAME — mirror of
  Android local.properties (server never uploads; client uploads)
- GROQ_API_KEY — enables AI advisory/chat; missing -> null/fallback
- OTP_PAGE (default true), OTP_PAGE_OPEN (default false) — debug page.
Render envVars mirror these; render.yaml sets healthCheckPath
/health, Node 20, CORS "*" placeholder comment. Server fails fast listing
all missing required vars (config/env.js:14-30).

Android config (root local.properties; template local.properties.example)
- sdk.dir, B2_APPLICATION_KEY_ID, B2_APPLICATION_KEY, B2_BUCKET_NAME,
  API_BASE_URL (default https://pashurakshak-ilol.onrender.com),
  APP_API_KEY (default hardcoded in core/build.gradle.kts:25).
  Injected as BuildConfig fields (core/build.gradle.kts:38-58).
- app-farmer/google-services.json present (project pashurakshak-961c1);
  app-vet one absent -> vet app has no FCM and ID (com.pashurakshak.vet)
  is not registered in Firebase.

Web apps: VITE_API_BASE_URL / VITE_APP_API_KEY env vars with hardcoded
fallbacks in src/api.js (both apps).

RUN LOCALLY
- Backend: cd shared-backend; npm install; cp .env.example .env (fill
  values); npm run migrate; npm start -> http://localhost:3000/health.
  Local DB alternative: TURSO_DATABASE_URL=file:local.db.
- Vet registration: cd vet-registration; npm install; npm run dev (5173).
- Gov portal: cd gov-portal; npm install; npm run dev (5174).
- Android: open root in Android Studio; Gradle sync; run app-farmer /
  app-vet. Requires google-services.json for FCM (farmer only currently).

DEPLOYMENT
- Backend on Render web service via render.yaml/Procfile (`web: npm
  start`, runs migrations at boot). Webs are Vite static SPAs presumably
  deployed separately (no Render config in repo).

HEALTH CHECK / LOGGING
- GET /health -> {"status":"ok"} (app.js:25-27).
- Logging: console.* only; no request logger middleware, no structured
  logs, no metrics/tracing.

=====================================================================
11. WHAT WORKS END TO END (user journey)
=====================================================================

1. Farmer registers/logs in (phone + PIN, OTP by email).
   Backend /auth/farmer/* -> WORKING. App UI -> WORKING. FCM token
   registration -> WORKING only for farmer APK (vet APK lacks
   google-services.json -> NO FCM for vet, silently disabled).

2. Farmer reports a sick animal (photo, location, symptoms, risk score).
   App: local insert + ReportPushApi POST /reports. Backend: upsert ->
   (placeholder animal created if needed) -> cluster detection ->
   categorizeAndAssign -> AI advisory -> {success, aiAdvisory}.
   - Report storage: WORKING (idempotent upsert, snake/camel aliases).
   - Risk categorization: WORKING but rule-based; client score is a
     placeholder; no AI involvement.
   - Cluster detection: WORKING (7-day/5km/3+ rule), creates alert,
     promotes statuses, FCM to vet devices.
   - AI advisory: WORKING only when GROQ_API_KEY set and Groq reachable;
     otherwise aiAdvisory=null (graceful). Otherwise fully degrades.
   - Vet auto-assignment: PARTIAL — works only when report has village AND
     a vet's service_areas LIKE-matches it AND that vet has approved
     application; first match, nondeterministic; no proximity/caseload.

3. Vet is assigned + notified.
   Targeted pashu_alerts row -> farmer/vet Alerts screens (local pull).
   Push notification to vet devices -> WORKING IF vet APK had FCM
   configured (currently it does NOT — vet gets in-app alert on next
   sync only). Farmer APK push -> WORKING (sendPushToUid on cluster
   hits vet devices only; cluster alert recipient_id='all-vets').

4. Vet opens case queue, scans QR.
   ZXing scan -> submitVisit -> POST /visits -> matched computed
   server-side, but ONLY the client blocks on matched==false; anyone can
   POST a "successful" visit. Status -> 'examined'. QR match
   enforcement: PARTIAL (client-only).

5. Vet assessment.
   risky/moderate/mild saved to vet_assessment + visit_verifications.
   risky -> pashu_gov_alerts row. WORKING (server-side write path fully
   functional). NOTE app-side submit-button likely broken because
   canSubmitVisit requires assessmentText which no UI field sets
   (CaseDetailViewModel.kt:51-57; screen never wires onAssessmentTextChanged).

6. AI advisory shown to farmer; chat.
   aiAdvisory returned on POST /reports and shown post-submit; chat GET/POST
   /reports/:id/chat persist full history, 20-message context cap. WORKING
   with same Groq dependency caveats.

7. Alerts.
   Farmer sees assignment/read-state alerts locally after sync; vet sees
   role-wide alerts. WORKING via pull sync. Outbreak cluster alert only
   when 3+ within 5km/7d.

8. Government oversight.
   Magistrate seeds (admin) -> email OTP login -> overview/applications/
   reports/clusters/alerts/users tabs; approve/reject applications;
   acknowledge alerts. WORKING. No notifications/escalation to gov beyond
   the portal itself.

9. Offline mode.
   Reports queue locally, photos upload first, periodic 15-min +
   reconnect-triggered sync. WORKING for queue/retry/pull; no server-side
   photo upload by backend (client talks to B2 directly with keys in
   local.properties/APK BuildConfig).

=====================================================================
12. GAPS AND PROBLEMS
=====================================================================

MISSING FEATURES (NOT IMPLEMENTED)
- No profile edit UI in vet-registration (updateMe unused, api.js:101-103).
- No vet app FCM (no google-services.json in app-vet).
- No server-side enforcement that a scanned QR matched (visits.js records
  matched=0 but still marks 'examined').
- Vet auto-assignment: no distance/proximity, no caseload balancing, no
  availability flag, no deterministic ordering.
- No global API rate limiting; no login attempt lockout.
- No real AI involvement in risk scoring — client score is a placeholder
  ("refined server-side later") and server only thresholds 60/30.
- No government notifications (email/SMS/push) on risky gov alerts.
- No resource localization (language toggle does not translate UI).
- No Tests for backend; no CI; no lint/typecheck scripts anywhere.
- No metrics/structured logging/request logging middleware.
- Session tokens irrevocable; no refresh tokens.
- B2 file uploads happen only from Android (backend can't ingest photos);
  server holds B2 keys but never uses them.
- vet-registration README endpoint table is stale/wrong.
- shared-backend README says AI model llama-3.3-70b-versatile; code uses
  openai/gpt-oss-120b.

STUBS / MOCKED / HARDCODED
- local.properties checked out on disk with live API_BASE_URL, APP_API_KEY,
  and real B2 key id/key/bucket — treat as compromised/secret-leak risk.
- Hardcoded fallback app keys in core/build.gradle.kts:25,
  vet-registration/src/api.js:2, gov-portal/src/api.js:2 (different values).
- core/build.gradle.kts:23 hardcodes production API base URL fallback.
- shared-backend/local.db checked in (dev SQLite with data).
- Debug /otp page exposes OTP codes behind ADMIN_SECRET (or open if
  OTP_PAGE_OPEN=true) — dev-only backdoor.
- JVM tests cover only B2UploadService/retry.
- CaseDetail "Why this score?" and client risk bands labeled placeholder
  (CaseDetailScreen.kt:616, RiskLevel.kt:17).

DUPLICATED / DEAD CODE
- api/v1 farmers also kept in legacy pashu_farmers table (unused).
- pashu_vets table exists but live data flows via users + vet_profiles.
- SessionManager plant detail duplicated in SharedPreferences vs DB profile.
- RemoteSync and ReportRepository both implement report pull/push paths.
- Multiple app keys across BuildConfig defaults (stale ones in web apps).
- ReportRepository.kt:66 stale comment "(sync logic comes later)".
- TursoClient.kt:17 doc says remote Turso sync "later phase" — stale.

ERROR-PRONE AREAS / LIKELY BUGS
- CaseDetailViewModel.canSubmitVisit requires assessmentText which the UI
  never sets -> "Submit Visit Verification" appears permanently disabled.
- Auto-assignment has no ORDER BY -> nondeterministic vet selection.
- Post-save AI pipeline failures swallowed (by design for resilience, but
  also silent inconsistency risk: reports may remain uncategorized).
- OTP attempt counter never resets across sends for a different code?
  (attempts increment on failed verify against latest code row; expiring
  row new send resets — acceptable but fragile.)
- SyncWorker always returns success -> WorkManager never backs off; a
  persistent failure loops every 15 min.
- fetchLocation has no timeout handling beyond Google services defaults.
- Vet login for app requires approved; a vet whose approval is revoked
  keeps a valid 30-day token (stateless tokens not role-gated after issue).
- gov district scoping compares LOWER(TRIM()) but no normalization of
  district aliases ("Central Delhi" vs "central delhi" ok, but state
  variants differ).

SCALABILITY LIMITS
- getActiveClusters and cluster detection load candidate rows into memory
  and do O(k^2) linkage per group — fine for small areas, CPU-bound at
  scale; runs synchronously in POST /reports request path.
- Chat sends last 20 messages regardless of size (token blowout possible
  with huge messages; 16k max_tokens caps output only).
- No DB indexes on symptom_reports(created_at), latitude/longitude,
  ai_risk_category, assigned_vet_id — scans grow with volume.
- Firebase polling for FCM token + device_tokens rows unbounded.
- Render single instance; SQLite/Turso single-writer.

=====================================================================
13. DEMO READINESS
=====================================================================

CAN BE DEMONSTRATED LIVE TODAY WITHOUT FAKING:
- Farmer: register (email OTP with MAIL_MODE=log or smtp), login, add
  animals (QR generated), report sick animal with photo + GPS, see
  immediate aiAdvisory dialog (if GROQ_API_KEY), offline queue + banner,
  My reports / Vaccinations / Alerts, profile language toggle (affects AI
  language only).
- Backend: health check, OTP send/verify, full /auth flows, app-key
  protection, cluster detection (seed 3 nearby reports via POST /reports),
  categorizeAndAssign behavior (60/30 bands), AI advisory + chat, gov API.
- Vet registration web: register -> OTP -> set PIN -> application by
  pincode coverage selection -> status page; sign in (client 'web');
  magistrate approval via gov-portal flips status visible on re-login.
- Gov portal: login via seeded magistrate OTP, overview, approve/reject
  applications scoped to district, reports table, clusters, gov alerts,
  users listing.
- Vet app: login (after approval), case queue, case detail, QR scan,
  assessment (NOTE likely submit-button blocker).

NEEDED FOR A REAL 20-30 FARMER PILOT:
- Add google-services.json for com.pashurakshak.vet so vet gets FCM.
- Fix CaseDetailViewModel.canSubmitVisit / wire assessment text (or drop
  the assessmentText requirement) so visits can be submitted from the UI.
- Enforce QR match server-side (reject status change when matched=0).
- Replace hardcoded fallback app keys/API URLs in web api.js and
  core/build.gradle.kts with required env config; rotate the keys + B2
  credentials currently in local.properties and the committed web
  fallbacks (treat as compromised).
- Turn OFF the /otp debug page in production (OTP_PAGE=false) and stop
  logging OTPs (MAIL_MODE=smtp or proper secrets).
- Add ORDER BY/scoring to vet assignment (nearest vet within coverage,
  least caseload) so demo assignment is deterministic.
- Deterministic risk score on server (or ship the client placeholder
  table to server) instead of only thresholding.
- Add indexes on symptom_reports(created_at, status) and assignment
  fields; add general rate limiting on /auth and POST /reports.
- Persist preferred_language effect beyond AI (true localization) if
  language will be a demo talking point.
- Backup/seed story for Turso; remove local.db from the repo or regenerate.
- Replace Placeholder client-side rule labels with production logic;
  hide B2UploadTestScreen route in release builds (currently always
  registered).
- Minimal backend smoke tests + Android instrumentation for the critical
  submitVisit path; proper error surfacing in the app (many paths log
  and swallow).

=====================================================================
14. SUMMARY TABLE
=====================================================================

| Feature | Status | Main file path |
|---|---|---|
| Farmer email OTP + PIN login/register/reset | Working | shared-backend/src/core/routes/auth.js |
| Vet OTP register + web login + app approval gate | Working | shared-backend/src/core/routes/auth.js |
| Gov magistrate OTP login (district-scoped) | Working | shared-backend/src/core/routes/auth.js |
| X-App-Key on all module routes | Working | shared-backend/src/core/middleware/verifyAppKey.js |
| Session tokens (HMAC, 30d) | Working | shared-backend/src/core/services/sessionToken.js |
| Offline report queue + 15-min/reconnect sync | Working | core/src/main/java/com/pashurakshak/app/data/sync/SyncWorker.kt |
| B2 photo capture/upload + retries | Working (farmer APK) | core/.../data/remote/B2UploadService.kt |
| Risk score (client placeholder bands) | Mocked/Placeholder | core/.../ui/farmer/ReportSickAnimalViewModel.kt:23 |
| Server risk categorization (60/30 + cluster->high) | Working (rule-based) | shared-backend/.../clusterDetection.js:138 |
| Cluster detection (7d/5km/3+, single-linkage) | Working | shared-backend/.../clusterDetection.js:45 |
| Vet auto-assignment (village LIKE match) | Partial | shared-backend/.../clusterDetection.js:158 |
| QR visit verification | Partial (server doesn't enforce match; app submit likely blocked) | shared-backend/src/modules/pashu-health/routes/visits.js |
| AI advisory (Groq gpt-oss-120b) | Working w/ GROQ_API_KEY | shared-backend/.../services/aiAdvisory.js |
| AI chat w/ 20-msg context cap, full history | Working w/ GROQ_API_KEY | shared-backend/.../routes/aiChat.js |
| Push notifications to vet (cluster) | Partial (vet APK lacks FCM config) | core/.../data/remote/PushMessagingService.kt |
| Gov alerts creation on risky visit | Working | shared-backend/.../routes/visits.js |
| Gov alerts acknowledgement in gov-portal | Working | shared-backend/src/core/routes/gov.js:266 |
| Vet application review + district scoping | Working | shared-backend/src/core/routes/gov.js:127,217,220 |
| Gov portal tabs (overview/reports/clusters/users) | Working | gov-portal/src/components/*Tab.jsx |
| vet-registration web (register/application/status) | Working (no profile edit) | vet-registration/src/App.jsx |
| Language toggle (hi/en) | Partial — stored + AI language only | core/.../ui/components/AppComponents.kt:588 |
| OTP debug page /otp | Working (dev backdoor) | shared-backend/src/app.js |
| CaseDetail "assessment text" submit path | Likely broken (no UI wiring) | core/.../ui/vet/CaseDetailViewModel.kt:51 |
| Server-side QR match enforcement | Not implemented | shared-backend/.../routes/visits.js |
| Logout / token revocation | Not implemented | — |
| Rate limiting beyond OTP | Not implemented | — |
| Backend tests / CI | Not implemented | — |
| Real localized UI strings | Not implemented | — |
