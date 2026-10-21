# 🐄 PasuRakshak - Livestock Disease Early-Warning System

> **A complete digital ecosystem for livestock health monitoring, outbreak detection, and veterinary management across rural India.**

![Status](https://img.shields.io/badge/Status-Development-orange) ![Version](https://img.shields.io/badge/version-1.0.0-blue.svg) ![Platform](https://img.shields.io/badge/platform-Android%20+%20Web-green.svg)

---

## 📋 Project Overview

**PashuRakshak** (literally "Animal Protector" in Hindi) is an end-to-end digital platform designed to revolutionize livestock disease management in rural India. The system provides real-time disease surveillance, AI-powered risk assessment, automated outbreak clustering, and streamlined veterinarian registration and approval workflows.

### Core Mission
Enable proactive disease prevention through early reporting, data-driven insights, and coordinated veterinary response across districts.

### Key Highlights
- ✅ **Offline-first Android app** for farmers and veterinarians
- ✅ **Government oversight portal** for District Magistrates
- ✅ **Vet registration web app** with automated district routing
- ✅ **AI-powered symptom analysis** and risk scoring
- ✅ **Automatic outbreak detection** using geographic clustering algorithms
- ✅ **Bidirectional cloud sync** for disconnected environments
- ✅ **Push notification system** for alerts and approvals
- ✅ **QR-based animal passports** for vaccination tracking

---

## 🏗️ Architecture & Technology Stack

### System Components

```
┌─────────────────────────────────────────────────────────────────┐
│                    PASURAKSHAK ECOSYSTEM                         │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ Farmer App   │  │ Vet App      │  │ Gov Portal           │  │
│  │ (Android)    │  │ (Android)    │  │ (React Web)          │  │
│  │              │  │              │  │                      │  │
│  │ • Animal     │  │ • Case       │  │ • Approve Vets       │  │
│  │   Management │  │   Queue      │  │ • Monitor Outbreaks  │  │
│  │ • Report Sick│  │ • Field      │  │ • Government Alerts  │  │
│  │ • Vaccination│  │   Inspections│  │ • Platform Analytics │  │
│  │ • QR Passport│  │ • Vet Alerts │  │                      │  │
│  └──────────────┘  └──────────────┘  └──────────────────────┘  │
│         │                   │                        │          │
│         │                   │                        │          │
│         ▼                   ▼                        ▼          │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │              VET REGISTRATION WEB APP                    │   │
│  │  • Email/OTP Verification  • PIN Setup                  │   │
│  │  • Application Submission  • Status Tracking            │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │          SHARED BACKEND (Node.js + Express)              │   │
│  │  • RESTful API  • Firebase Auth  • Turso Database        │   │
│  │  • Push Notifications  • Cluster Detection               │   │
│  └─────────────────────────────────────────────────────────┘   │
│                           │                                     │
│                           ▼                                     │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                THIRD-PARTY SERVICES                      │   │
│  │  • Firebase Cloud Messaging  • Backblaze B2 Storage     │   │
│  │  • Postal API (Pincode Lookup)  • AI Risk Engine         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### Detailed Tech Stack

#### 📱 Android Applications (Farmer & Vet)
| Component | Technology | Version | Purpose |
|-----------|------------|---------|---------|
| **Language** | Kotlin | 2.2.10 (AGP bundled) | Native development |
| **UI Framework** | Jetpack Compose | 2026.09.00 | Modern declarative UI |
| **Design System** | Material 3 | Via BOM | Consistent UI components |
| **Navigation** | Navigation Compose | 2.10.1 | Screen routing |
| **Local Database** | Turso libSQL | 0.1.2 | Offline-first SQLite |
| **Background Work** | WorkManager | 2.10.1 | Scheduled sync tasks |
| **Authentication** | Firebase Phone Auth | firebase-bom 33.16.0 | OTP-based login |
| **Push Notifications** | Firebase FCM | firebase-bom 33.16.0 | Real-time alerts |
| **Cloud Storage** | Backblaze B2** | Native API | Photo uploads |
| **Location Services** | Google Play Services** | Latest | GPS & location tracking |
| **QR Generation** | ZXing Library** | Latest | Animal passport QR codes |
| **Image Loading** | Coil** | Latest | Efficient image loading |
| **Async Processing** | Coroutines** | 1.11.0 | Non-blocking operations |
| **Dependency Injection** | Manual ServiceLocator** | Custom | Lightweight DI |

#### 💻 Web Applications

##### Vet Registration Portal
| Component | Technology | Version |
|-----------|------------|---------|
| **Framework** | React | 19.1.0 |
| **Build Tool** | Vite | 6.3.5 |
| **Styling** | Custom CSS | — |

##### Government Portal
| Component | Technology | Version |
|-----------|------------|---------|
| **Framework** | React | 19.1.0 |
| **Build Tool** | Vite | 6.3.5 |
| **Styling** | Custom CSS | — |

#### 🔧 Shared Backend (Node.js)
| Component | Technology | Version | Notes |
|-----------|------------|---------|-------|
| **Runtime** | Node.js | ≥18 | JavaScript runtime |
| **Framework** | Express | 4.21.0 | Web framework |
| **Database** | Turso (@libsql/client)** | 0.15.0 | SQLite-based, edge-ready |
| **Authentication** | Firebase Admin SDK** | 14.4.0 | ID token verification |
| **Push Notifications** | Firebase Admin SDK** | 14.4.0 | FCM messaging |
| **Environment Config** | dotenv** | 16.4.5 | Environment variables |
| **CORS Handling** | cors** | 2.8.5 | Cross-origin requests |

---

## 📦 Application Modules

### 1. 🟢 Farmer Android App (`app-farmer`)

#### Purpose
Empower livestock owners to report sick animals, track vaccinations, and receive health alerts.

#### Key Features

##### 📝 Report Sick Animals
- **Photo capture**: Take photos of symptoms directly from the app
- **GPS location**: Automatically capture precise location data
- **Symptom selection**: Choose from predefined symptoms or add custom notes
- **Risk scoring**: AI-powered urgency classification (Low/Medium/High)
- **Offline support**: Reports saved locally, synced when connectivity returns

##### 🐃 Animal Management
- **Profile creation**: Add species, name, age, breed, color, weight
- **QR Passport**: Generate unique QR codes for each animal
- **Vaccination history**: Track past and upcoming vaccinations
- **Family details**: Record father/mother lineage for breeding records

##### 💉 Vaccination Tracker
- **Schedule reminders**: Automatic alerts for due vaccinations
- **History log**: Complete vaccination records per animal
- **Next due dates**: Clear visibility of upcoming shots
- **Alert notifications**: Reminders sent via FCM

##### 🚨 Alerts & Notifications
- **Veterinary alerts**: When vets assign cases to their area
- **Cluster warnings**: Outbreak alerts in nearby regions
- **Vaccination reminders**: Timely notifications for due dates
- **Government announcements**: Official health advisories

##### 🔄 Bidirectional Sync
- **Upload queue**: Pending reports shown in sync banner
- **Photo upload**: Photos stored in Backblaze B2 cloud storage
- **Remote pull**: Fetch new data from backend automatically
- **Conflict resolution**: Smart merge of local/remote changes

#### Technical Implementation

**Data Layer** (`data/local/`):
```kotlin
// Core entities with SQLite mapping
- SymptomReport: id, animalId, farmerId, symptoms[], photoPath, 
                 latitude, longitude, riskScore, status
- Animal: id, ownerFarmerId, species, name, qrCodeId
- Vaccination: id, animalId, vaccineName, dateGiven, nextDue
- Alert: id, recipientRole, recipientId, message, read, createdAt
```

**Repository Pattern** (`data/*.kt`):
```kotlin
interface AnimalRepository {
    fun insert(animal: Animal): Long
    fun getAll(): List<Animal>
    fun getById(id: String): Animal?
    fun getByQrCode(qrCode: String): Animal?
    fun update(animal: Animal)
    fun delete(id: String)
}

interface ReportRepository {
    fun insert(report: SymptomReport): Long
    fun getUnsyncedReports(): List<SymptomReport>
    fun getReportsPendingPhotoUpload(): List<SymptomReport>
    fun upsertFromRemote(remoteReport: SymptomReport)
    // ... more methods
}
```

**Sync Engine** (`data/sync/`):
```kotlin
SyncScheduler:
- Periodic sync every 15 minutes
- Immediate sync on network reconnection
- NetworkType.CONNECTED constraint (battery efficient)

SyncWorker:
1. Upload pending photos to Backblaze B2
2. Re-push reports with photo URLs
3. POST unsynced reports to backend
4. Sync animals/vaccinations/alerts bidirectionally
5. Update 'synced' flags after successful operations

RemoteSync:
- pushLocals(): Animals → Vaccinations → Reports → Alerts
- pullAll(): Remote data inserted into local DB (idempotent)
```

**Authentication** (`data/AuthRepository.kt`):
```kotlin
// Firebase Phone Auth flow
sendVerificationCode(phoneNumber) → VerificationId
signInWithOtp(verificationId, code) → FirebaseUser
registerDeviceToken(fcmToken) → Backend device registration
saveRoleMapping(phone, Role.FARMER) → Local farmer table
```

#### Screens & Navigation

```
MainActivity → AppNavHost
├── Auth Flow
│   ├── PhoneEntryScreen: Enter phone number → Send OTP
│   ├── OtpScreen: Verify 6-digit code
│   └── RoleSelectScreen: Choose role (Farmers only here)
│
├── Farmer Home
│   ├── MyAnimalsScreen: List animals + QR codes
│   ├── ReportSickAnimalScreen: Form with photo/location
│   ├── VaccinationStatusScreen: Per-animal vaccination history
│   ├── MyReportsScreen: View submitted reports
│   └── AlertsScreen: Farmer-specific alerts
│
└── QR Passport
    └── QrPassportScreen({animalId}): Scannable QR + profile
```

**Bottom Navigation** (FarmerBottomBar.kt):
- 🏠 Home
- 🐃 My Animals
- 📝 Report Sick
- 💉 Vaccination
- 🔔 Alerts

---

### 2. 🩺 Veterinarian Android App (`app-vet`)

#### Purpose
Provide veterinarians with case queues, field inspection tools, and alert systems.

#### Key Features

##### 📋 Case Queue Management
- **Risk-sorted queue**: Cases prioritized by AI risk score
- **Filter options**: By status, distance, urgency level
- **Geographic sorting**: Nearest cases first
- **Case details**: Full history, symptoms, photos, animal info

##### 🔍 Case Detail Screen
- **Patient overview**: Animal details, owner contact, location
- **Symptoms display**: Rich list with severity indicators
- **Photo gallery**: View uploaded symptom images
- **Risk breakdown**: Explain why this case scored as HIGH/MEDIUM/LOW
- **Field check dialog**: Mark as "visited", add observations
- **Status updates**: Progress through workflow stages

##### 📊 Workflow States
```
reported → vet_assigned → examined → sample_sent → confirmed → resolved
```

Each stage represents:
- **reported**: Farmer submitted initial report
- **vet_assigned**: System assigned to veterinarian (auto or manual)
- **examined**: Vet completed physical inspection
- **sample_sent**: Lab samples dispatched (if needed)
- **confirmed**: Diagnosis finalized
- **resolved**: Treatment completed, animal recovered/deceased

##### 🚨 Vet-Specific Alerts
- **New high-risk cases**: Real-time notifications for urgent submissions
- **Cluster formations**: Automated alerts when 3+ cases detected within 5km
- **Government advisories**: Official health bulletins
- **Approval notifications**: Vaccination program invites

##### 🔎 Why This Score? Feature
Explainable AI card showing:
```json
{
  "fever": 15,
  "loss_of_appetite": 10,
  "reduced_milk_yield": 20,
  "lethargy": 25,
  "swelling": 30,
  "total": 100 (HIGH RISK)
}
```

Each symptom has weighted coefficients based on historical disease patterns.

#### Technical Implementation

**Risk Scoring Algorithm** (`ui/vet/RiskLevel.kt`):
```kotlin
enum class RiskLevel(val scoreRange: IntRange, val label: String, val color: Int) {
    LOW(0-40, "LOW RISK", Color.Green),
    MEDIUM(41-70, "MEDIUM RISK", Color(0xFFEF6C00)),
    HIGH(71-100, "HIGH RISK", Color.Red)
}

fun calculateRiskScore(symptoms: List<String>): Map<String, Int> {
    val weights = mapOf(
        "fever" to 15,
        "loss_of_appetite" to 10,
        "reduced_milk_yield" to 20,
        "lethargy" to 25,
        "swelling" to 30,
        "difficulty_breathing" to 35,
        "diarrhea" to 15,
        "lameness" to 20
    )
    
    return symptoms.mapNotNull { symptom ->
        weights[symptom.lowercase()]?.let { it to it }
    }.toMap()
}
```

**Case Detail ViewModel** (`CaseDetailViewModel.kt`):
```kotlin
class CaseDetailViewModel(
    private val reportRepository: ReportRepository,
    private val animalRepository: AnimalRepository
) : ViewModel() {
    
    val caseDetail = MutableStateFlow<CaseDetailUiState?>(null)
    
    fun loadCase(reportId: String) {
        viewModelScope.launch {
            val report = reportRepository.getById(reportId)
            val animal = report?.let { animalRepository.getById(it.animalId) }
            val farmer = SessionManager.getFarmerId()
            
            caseDetail.value = CaseDetailUiState(
                report = report,
                animal = animal,
                farmerId = farmer,
                myInspection = false // Will be updated if vet visited
            )
        }
    }
    
    fun markAsExamined(observationNotes: String) {
        viewModelScope.launch {
            val report = caseDetail.value?.report ?: return@launch
            val updatedReport = report.copy(
                status = ReportStatus.EXAMINED,
                vetObservations = observationNotes
            )
            reportRepository.update(updatedReport)
        }
    }
}
```

#### Screens & Navigation

```
AppNavHost
└── Vet Home
    ├── VetCaseQueueScreen: Priority-sorted list of pending cases
    ├── CaseDetailScreen({reportId}): Deep dive with field check dialog
    └── VetAlertsScreen: Vet-wide broadcast alerts
```

**Bottom Navigation** (VetBottomBar.kt):
- 🏠 Home
- 📋 Case Queue
- 🔔 Alerts

---

### 3. 🛡️ Government Portal (Gov Portal Web App)

#### Purpose
District Magistrate dashboard for oversight, vet approval, and regional health monitoring.

#### Access Model
- **Email-based authentication**: No password, pure OTP flow
- **District scoping**: Each magistrate sees only their district's data
- **Role-based access**: `role='gov'` session tokens

#### Key Features

##### 🔐 Login Flow
```
Step 1: Email Entry
   └── Input: dm.rampur@example.gov.in
   └── Action: Request OTP
   
Step 2: OTP Verification
   └── Input: 6-digit code sent via email
   └── Action: Verify → Create session token
   
Step 3: Dashboard Access
   └── JWT-like payload: { email, role, district, exp }
   └── Stored in localStorage
   └── Backend validates on every API call
```

##### 📊 Overview Tab
Real-time platform metrics:
- Total Farmers Registered
- Active Veterinarians
- Pending Vet Applications
- Recent Symptom Reports (last 7 days)
- Active Outbreak Clusters
- Unread Alerts

##### 👨‍⚕️ Veterinarian Applications Tab
```
Application Card Structure:
┌──────────────────────────────────────────┐
│ Name: Dr. Rajesh Kumar                   │
│ Experience: 12 years                     │
│ Qualification: BVSc & AH                 │
| Clinic Address: Main St, Rampur          │
│ Pincode: 244701 → District: Rampur       │
│ Coverage Areas: [Rampur ☑, Shahjahanpur] │
│ Status: ⏳ Pending                       │
└──────────────────────────────────────────┘

Actions:
├── ✅ Approve (with optional notes shown to vet)
└── ❌ Reject (reviewer notes mandatory)
```

**Backend Logic**:
- Server resolves pincode to district via postal API
- Applications routed to correct District Magistrate
- Approval creates `pashu_vets` record with `status='active'`
- Rejection creates audit trail with reviewer note

##### 📝 Symptom Reports Tab
- **Full filter bar**: By status, risk level, date range
- **Search**: Animal name, farmer phone, symptoms
- **Table columns**: ID, Animal, Farmer, Symptoms, Risk Score, Status, Date
- **Drill-down**: Click row → view full case details

##### 🌡️ Clusters Tab
**Outbreak Detection Algorithm**:
```javascript
const ACTIVE_WINDOW_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
const CLUSTER_RADIUS_KM = 5;
const MIN_REPORTS = 3;

function detectClusters(reports) {
    // 1. Filter recent unresolved reports
    const recent = reports.filter(r => 
        r.createdAt >= Date.now() - ACTIVE_WINDOW_MS &&
        r.status !== 'resolved'
    );
    
    // 2. Geographic clustering (single-linkage)
    const clusters = [];
    for (const report of recent) {
        const neighbors = findNeighbors(report, recent);
        if (neighbors.length >= MIN_REPORTS) {
            clusters.push({
                centerLat: calcAverage(neighbors.lat),
                centerLng: calcAverage(neighbors.lng),
                radiusKm: calcMaxDistance(neighbors),
                reportIds: neighbors.ids
            });
        }
    }
    
    return clusters;
}
```

Visual cluster cards:
```
🚨 Cluster Alert #142
├── Location: Rampur South (28.12°N, 77.45°E)
├── Radius: 4.2 km
├── Reports: 5 animals affected
├── Timeframe: Last 3 days
├── Dominant symptoms: Fever, Loss of appetite
└── Status: Vets notified ✓
```

##### 📢 Government Alerts Tab
- **Source**: Auto-generated from risky field visits or manual entry
- **Acknowledgment workflow**:
  ```
  New Alert → Read → Acknowledge (confirmation required)
                     ↓
              Dismiss / Escalate to State Dept
  ```
- **Acknowledged count**: Tracked per magistrate

##### 👥 Users Tab
Split view tabs:
- **Farmers**: Phone, name (if available), district, joined date
- **Veterinarians**: Name, clinic address, active district, approval date, coverage areas

---

### 4. 📝 Vet Registration Web App

#### Purpose
Streamlined online application process for veterinarians seeking licensure/approval.

#### User Journey

##### Step 1: Registration Start
```
Inputs:
├── Phone Number (+91 XXXXX XXXXX)
└── Email Address (professional email)

Action:
→ POST /core/auth/vet/register/send-otp
→ Server generates 6-digit code
→ Sends email via SMTP or Brevo/Resend HTTPS
→ Returns temporary verifyToken
```

##### Step 2: OTP Verification
```
Input: 6-digit code (resend supported after 30s)
Action:
→ POST /core/auth/vet/register/verify-otp
→ Server validates
→ Returns sessionToken (JWT-like)
→ Valid for credential setup only
```

##### Step 3: Set Credential
**Choose authentication method**:
- **Option A**: 6-digit numeric PIN (easy recall)
- **Option B**: Alphanumeric password (stronger security)

API: `POST /core/auth/vet/setup-pin`
```json
{
  "type": "pin",  // or "password"
  "credential": "123456"  // hashed before sending
}
```

##### Step 4: Application Form (Two Steps)

**Step 1 – Professional Details**:
```
├── Full Name (required)
├── BVSc & AH Degree Number (required)
├── Registration Council (dropdown: UP Veterinary Council, etc.)
├── Years of Experience (number input)
├── Clinic/Hospital Name
├── Clinic Address (textarea)
└── Primary Contact Phone
```

**Step 2 – Coverage Area Selection**:
```
Current Location: Auto-detected OR manual pincode lookup

Coverage Areas (Multi-select checkboxes):
☑️ Rampur (current district)
☐ Shahjahanpur
☐ Barabanki
☐ Kushinagar
☐ Amethi

Server auto-resolves pincode to district → pre-check that district
```

Submit → `POST /pashu-health/vet-applications`

Response:
```json
{
  "applicationId": "uuid-here",
  "status": "pending",
  "assignedMagistrate": {
    "district": "Rampur",
    "email": "dm.rampur@example.gov.in"
  },
  "submissionDate": "2026-10-17T10:30:00Z"
}
```

##### Step 5: Status Page
Three possible states:

**State 1: Pending** (Default after submission)
```
⏳ Your application is under review
Assigned to: District Magistrate, Rampur
Expected processing time: 5-7 working days
You can resubmit edits anytime while pending.
[Edit Application] button enabled
```

**State 2: Approved**
```
✅ Congratulations! Your application has been approved.
Approval Date: Oct 15, 2026
Reviewer Notes: "Excellent credentials, verified diploma."
You can now log in to the Vet Android App.
[Go to Vet App] button enabled
```

**State 3: Rejected**
```
❌ Application Rejected
Reason: "Incomplete qualification documents."
Review Note: "Please upload scanned degree certificate."
Appeal deadline: 30 days from rejection date

[Edit & Resubmit] button enabled (changes tracked)
```

**Returning Vet Login**:
```
Phone + PIN/Password → GET /core/pincode/:code
If status=pending/rejected: Redirect to Status Page
If status=approved: Redirect to Vet Dashboard (not yet implemented)
```

#### API Endpoints Used

| Method | Endpoint | Purpose | Authentication |
|--------|----------|---------|----------------|
| POST | `/core/auth/vet/register/send-otp` | Initiate registration | App key only |
| POST | `/core/auth/vet/register/verify-otp` | Validate OTP | App key only |
| POST | `/core/auth/vet/setup-pin` | Create PIN/password | Verify token |
| POST | `/core/auth/vet/login` | Login with PIN/password | App key only |
| GET | `/core/auth/me` | Get current user profile | Session token |
| POST | `/pashu-health/vet-applications` | Submit application | Session token |
| GET | `/pashu-health/vet-applications/me` | Get own applications | Session token |
| GET | `/core/pincode/:pincode` | Resolve pincode → district | Public (no auth) |

---

## 🔄 Shared Backend API Reference

Base URL: `http://localhost:3000/api/v1` (or deployed URL)

All endpoints require `X-App-Key` header (registered per application).

### Core Routes (`/core`)

#### App Management
```http
POST /api/v1/core/apps
Headers:
  X-App-Key: <admin_secret_required>  // Use ADMIN_SECRET from .env
  Content-Type: application/json
Body:
  { "name": "Government Portal" }
  
Response:
  { "id": "uuid", "name": "...", "api_key": "hex-string-ONCE_ONLY" }
```

```http
GET /api/v1/core/apps
Headers:
  X-Admin-Secret: <ADMIN_SECRET>
  
Response:
  [ { "id": "...", "name": "...", "created_at": 1234567890 } ]
```

#### Device Token Registration
```http
POST /api/v1/core/devices
Headers:
  X-App-Key: <app_api_key>
  Authorization: Bearer <firebase_id_token>
Body:
  { "uid": "firebase-user-uid", "role": "farmer", "fcmToken": "token" }
  
Response:
  { "success": true, "deviceTokenId": "uuid" }
```

#### Vet Application CRUD
```http
POST /api/v1/pashu-health/vet-applications
Headers:
  X-App-Key: <app_api_key>
  Authorization: Bearer <session_token_from_otp_login>
Body:
  {
    "veterinarianName": "Dr. Kumar",
    "qualification": "BVSc & AH",
    "experienceYears": 12,
    "clinicAddress": "Main St, Rampur",
    "pincode": "244701",
    "coverageAreas": ["Rampur", "Shahjahanpur"]
  }
  
Response:
  { "id": "uuid", "status": "pending", "district": "Rampur" }
```

```http
GET /api/v1/pashu-health/vet-applications
Query Params: ?status=pending|approved|rejected

Response:
  [ { "id": "...", "veterinarianName": "...", "status": "...", "submittedAt": ... } ]
```

```http
POST /api/v1/pashu-health/vet-applications/:id/approve
Headers:
  Authorization: Bearer <gov_session_token>
Body:
  { "reviewerNotes": "Approved after verification" }
  
Response:
  { "updated": true, "status": "approved", "createdAt": timestamp }
```

```http
POST /api/v1/pashu-health/vet-applications/:id/reject
Headers:
  Authorization: Bearer <gov_session_token>
Body:
  { "reason": "Incomplete documents", "reviewerNotes": "..." }
  
Response:
  { "updated": true, "status": "rejected" }
```

### Health Module Routes (`/pashu-health`)

#### Symptom Reports
```http
POST /api/v1/pashu-health/reports
Headers:
  X-App-Key: <farmer_app_key>
  Authorization: Bearer <firebase_id_token>
Body:
  {
    "id": "custom-uuid",  // client-generated, idempotent
    "animalId": "animal-uuid",
    "farmerId": "farmer-uuid",
    "symptoms": ["fever", "loss_of_appetite"],
    "photoLocalPath": "/local/path/photo.jpg",
    "photoRemoteUrl": "https://b2-cdn.com/bucket/photo.jpg",  // optional initially
    "latitude": 28.123456,
    "longitude": 77.456789,
    "riskScore": 75,
    "riskBreakdown": {"fever": 15, "loss_of_appetite": 10},
    "status": "reported",
    "synced": false,
    "createdAt": 1234567890
  }
  
Response:
  { "saved": true, "clusterDetected": false }
  
// Cluster detection runs asynchronously after save
```

```http
GET /api/v1/pashu-health/reports
Headers:
  X-App-Key: <any_registered_app_key>
  
Response:
  [ { "id": "...", "animalId": "...", "symptoms": [...], "riskScore": 75, ... } ]
```

#### Animals (Full CRUD)
```http
POST /api/v1/pashu-health/animals
PUT /api/v1/pashu-health/animals/:id
DELETE /api/v1/pashu-health/animals/:id
GET /api/v1/pashu-health/animals
GET /api/v1/pashu-health/animals/:id
```

#### Vaccinations
```http
POST /api/v1/pashu-health/vaccinations
GET /api/v1/pashu-health/vaccinations
GET /api/v1/pashu-health/vaccinations/:id
DELETE /api/v1/pashu-health/vaccinations/:id
```

#### Alerts
```http
POST /api/v1/pashu-health/alerts
GET /api/v1/pashu-health/alerts
GET /api/v1/pashu-health/alerts/:id
PUT /api/v1/pashu-health/alerts/:id  // partial update
DELETE /api/v1/pashu-health/alerts/:id
```

#### Clusters
```http
GET /api/v1/pashu-health/clusters
Headers:
  X-App-Key: <app_key>
  
Response:
  [
    {
      "reportCount": 5,
      "reportIds": ["id1", "id2", ...],
      "centerLat": 28.12,
      "centerLng": 77.45,
      "radiusKm": 4.2,
      "windowDays": 7,
      "firstReportAt": 1234567890,
      "lastReportAt": 1234589012,
      "statuses": ["reported", "vet_assigned"]
    }
  ]
```

---

## 🗄️ Database Schema

### Backend Tables (Turso/libSQL)

#### Core Tables (Shared Across Apps)

**`apps`** — Application registrations
```sql
CREATE TABLE apps (
  id TEXT PRIMARY KEY,              -- UUID
  name TEXT NOT NULL,               -- Human-readable name
  api_key TEXT NOT NULL UNIQUE,     -- 32-char hex string, shown once
  created_at INTEGER NOT NULL       -- Unix timestamp
);
```

**`device_tokens`** — FCM token registry
```sql
CREATE TABLE device_tokens (
  id TEXT PRIMARY KEY,              -- UUID
  app_id TEXT NOT NULL REFERENCES apps(id),
  uid TEXT,                         -- Firebase UID (nullable for web)
  role TEXT,                        -- 'farmer', 'vet', 'gov'
  fcm_token TEXT,                   -- Firebase Cloud Messaging token
  updated_at INTEGER NOT NULL       -- Last token refresh
);
```

#### Pashu Health Module Tables (Prefixed to prevent collisions)

**`pashu_animals`** — Livestock profiles
```sql
CREATE TABLE pashu_animals (
  id TEXT PRIMARY KEY,              -- UUID
  owner_farmer_id TEXT NOT NULL,    -- References farmers.id
  species TEXT NOT NULL,            -- 'Cow', 'Buffalo', 'Goat', 'Sheep'
  name TEXT NOT NULL,               -- Owner-given name
  qr_code_id TEXT NOT NULL UNIQUE,  -- Unique QR identifier
  created_at INTEGER NOT NULL       -- Timestamp
);
```

**`pashu_vaccinations`** — Vaccination records
```sql
CREATE TABLE pashu_vaccinations (
  id TEXT PRIMARY KEY,              -- UUID
  animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
  vaccine_name TEXT NOT NULL,       -- e.g., "HFMD Vaccine 2025"
  date_given INTEGER NOT NULL,      -- Vaccination date
  next_due INTEGER NOT NULL         -- Next scheduled date
);
```

**`pashu_symptom_reports`** — Disease reports
```sql
CREATE TABLE pashu_symptom_reports (
  id TEXT PRIMARY KEY,              -- UUID
  animal_id TEXT NOT NULL REFERENCES pashu_animals(id),
  farmer_id TEXT NOT NULL,          -- Submitter (may differ from owner)
  symptoms TEXT NOT NULL,           -- JSON array: ["fever", "lameness"]
  photo_local_path TEXT NOT NULL,   -- Relative path in app storage
  photo_remote_url TEXT,            -- Backblaze B2 public URL (nullable)
  latitude REAL NOT NULL,           -- GPS coordinates
  longitude REAL NOT NULL,
  risk_score INTEGER NOT NULL,      -- 0-100 scale
  risk_breakdown TEXT NOT NULL,     -- JSON: {"fever": 15, ...}
  status TEXT NOT NULL CHECK (
    status IN ('reported', 'vet_assigned', 'examined', 
               'sample_sent', 'confirmed', 'resolved')
  ),
  synced INTEGER NOT NULL DEFAULT 0,-- 0=pending sync, 1=synced
  created_at INTEGER NOT NULL       -- Timestamp
);
```

**`pashu_vets`** — Veterinarian profiles
```sql
CREATE TABLE pashu_vets (
  id TEXT PRIMARY KEY,              -- UUID
  name TEXT NOT NULL,               -- Full name
  phone TEXT NOT NULL,              -- Verified mobile
  assigned_district TEXT NOT NULL,  -- Primary work district
  coverage_areas TEXT NOT NULL,     -- JSON array of districts
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
  created_at INTEGER NOT NULL       -- Application timestamp
);
```

**`pashu_alerts`** — Notification messages
```sql
CREATE TABLE pashu_alerts (
  id TEXT PRIMARY KEY,              -- UUID
  recipient_role TEXT NOT NULL,     -- 'farmer', 'vet', 'all-vets', 'gov'
  recipient_id TEXT NOT NULL,       -- Specific user or 'all-vets'
  message TEXT NOT NULL,            -- Alert body
  read INTEGER NOT NULL DEFAULT 0,  -- 0=unread, 1=read
  created_at INTEGER NOT NULL       -- Timestamp
);
```

**`pashu_farmers`** — Farmer identities
```sql
CREATE TABLE pashu_farmers (
  id TEXT PRIMARY KEY,              -- UUID
  phone TEXT NOT NULL,              -- Verified mobile
  created_at INTEGER NOT NULL       -- Registration timestamp
);
```

**`pashu_vet_applications`** — Vet application submissions
```sql
CREATE TABLE pashu_vet_applications (
  id TEXT PRIMARY KEY,              -- UUID
  veterinarian_name TEXT NOT NULL,
  qualification TEXT NOT NULL,
  experience_years INTEGER,
  clinic_address TEXT NOT NULL,
  pincode TEXT NOT NULL,
  coverage_areas TEXT NOT NULL,     -- JSON array
  district TEXT NOT NULL,           -- Resolved from pincode
  status TEXT NOT NULL DEFAULT 'pending',
  reviewer_notes TEXT,              -- Admin feedback
  assigned_magistrate TEXT,         -- DM email
  submitted_at INTEGER NOT NULL,
  updated_at INTEGER                -- Last edit timestamp
);
```

#### Migration Tracking

**`migrations_applied`** — Version control for schema changes
```sql
CREATE TABLE migrations_applied (
  version INTEGER PRIMARY KEY,
  name TEXT NOT NULL,              -- Filename like "001_pashu_health.js"
  applied_at INTEGER NOT NULL      -- Timestamp
);
```

**Migration Files**:
- `000_core.js` → Creates `apps`, `device_tokens`
- `001_pashu_health.js` → Creates all `pashu_*` tables

---

### Android Local Tables (Embedded SQLite)

Uses same structure but **unprefixed** table names:
- `animals` instead of `pashu_animals`
- `vaccinations` instead of `pashu_vaccinations`
- etc.

This separation allows independent evolution of local vs remote schemas.

**Migration Strategy**: Android uses `PRAGMA user_version` tracked in database metadata (distinct from backend's `migrations_applied` table).

---

## 🔐 Security & Authentication

### Authentication Flows

#### 1. Firebase Phone Auth (Farmer/Vet Android Apps)

```
Step 1: PhoneEntryScreen
   ↓
   POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithPhoneNumber
   Body: { phoneNumber: "+91XXXXXXXXXX", recaptchaVerifier: ... }
   ↓
Step 2: Receive VerificationId
   ↓
   OtpScreen shows 6-digit input field
   ↓
Step 3: Verify Code
   ↓
   POST https://identitytoolkit.googleapis.com/v1/accounts:signInWithConfirm
   Body: { 
     phoneNumber: "...", 
     verificationId: "AFGh...xyz", 
     code: "123456" 
   }
   ↓
Step 4: Get Firebase ID Token
   ↓
   { 
     kind: "loginSuccess",
     localId: "firebase-user-uid",
     idToken: "eyJhbGc..." // valid for 1 hour
   }
   ↓
Step 5: Register Device with Backend
   ↓
   POST /api/v1/core/devices
   Headers:
     X-App-Key: <farmer_app_key>
     Authorization: Bearer <firebase_id_token>
   Body: { uid: "...", role: "farmer", fcmToken: "..." }
   ↓
Login Complete → RoleSelection → Navigate to FarmerHome
```

#### 2. Email OTP Auth (Government Portal)

```
Step 1: LoginScreen (Gov)
   ↓
   Email input: dm.rampur@example.gov.in
   ↓
   POST /api/v1/auth/gov/request-otp
   Headers:
     X-App-Key: <gov_portal_app_key>
   Body: { email: "dm.rampur@example.gov.in" }
   ↓
Step 2: Backend Generates OTP
   ↓
   Generates 6-digit random code
   Stores in Redis/temp store with TTL=5min
   Sends via SMTP or Brevo/Resend HTTPS
   ↓
Step 3: Verify OTP
   ↓
   POST /api/v1/auth/gov/verify
   Headers:
     X-App-Key: <gov_portal_app_key>
   Body: { email: "...", otp: "123456" }
   ↓
Step 4: Return Session Token
   ↓
   JWT-like payload:
   {
     "email": "dm.rampur@example.gov.in",
     "role": "gov",
     "district": "Rampur",
     "exp": 1234567890,
     "iat": 1234567800
   }
   → Signed with server secret (HMAC or EdDSA)
   → Base64url encoded: payload.signature.timestamp
   ↓
Step 5: Store in localStorage
   ↓
   Dashboard loads with district-scoped queries
```

#### 3. Vet Registration Flow (Web App)

```
RegisterStart → Send OTP → Verify OTP → Setup PIN/Password → Application → Status Page

Detailed Flow:

1. RegisterStart.jsx
   ↓
   Inputs: phone + email
   ↓
   POST /core/auth/vet/register/send-otp
   Body: { phone: "+91...", email: "vet@example.com" }
   ↓
   Returns: verifyToken (temporary, short-lived)

2. OtpVerify.jsx
   ↓
   Inputs: 6-digit code
   ↓
   POST /core/auth/vet/register/verify-otp
   Body: { verifyToken: "...", code: "123456" }
   ↓
   Returns: sessionToken (valid for credential setup phase only)

3. SetCredential.jsx
   ↓
   Options: 
     - PIN (numeric, 6 digits)
     - Password (alphanumeric, min 8 chars)
   ↓
   POST /core/auth/vet/setup-pin
   Headers:
     Authorization: Bearer <sessionToken>
   Body:
     { type: "pin", credential: "hashed_123456" }
   ↓
   Server hashes with bcrypt → stores in database
   ↓
   Returns: success=true, can now login

4. ApplicationForm.jsx (after first login)
   ↓
   Step 1: Professional details
   ↓
   Step 2: Coverage area multi-select
   ↓
   POST /pashu-health/vet-applications
   Body: { ..., pincode: "244701" }
   ↓
   Server resolves pincode → district (via postal API)
   ↓
   Creates application record with status='pending'
   ↓
   Redirects to StatusPage.jsx
```

#### 4. Returning Vet Login (PIN-based)

```
SignInForm.jsx
   ↓
   Inputs: phone + PIN/password
   ↓
   POST /core/auth/vet/login
   Body: { phone: "+91...", credential: "123456" }
   ↓
   Server verifies credential (bcrypt compare)
   ↓
   Returns: sessionToken (full-session token, longer TTL)
   ↓
   Check status: GET /pashu-health/vet-applications/me
   ↓
   If status='approved': Redirect to Vet App (future)
   If status='pending/rejected': Redirect to StatusPage
```

---

## 📡 Background Sync Engine

### Problem Statement
Rural areas have intermittent connectivity. Users must be able to:
- **Work offline**: Submit reports without internet
- **Sync when online**: Batch uploads when connection returns
- **Handle conflicts**: Merge remote changes without data loss

### Solution Architecture

```
┌─────────────────────────────────────────────────────────┐
│              SYNC SCHEDULER (SyncScheduler.kt)           │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  triggerNow()                                            │
│  ├── Immediate sync attempt (network-connected?)        │
│  └── Schedule periodic job (every 15min, CONNECTED)     │
│                                                          │
│  schedulePeriodic()                                      │
│  └── WorkManager.periodicWorkRequest(15min)             │
│                                                             │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│                 SYNC WORKER (SyncWorker.kt)              │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  doWork():                                               │
│  1. Upload pending photos → Backblaze B2                │
│  2. Re-push reports with photo URLs                     │
│  3. POST unsynced reports → /reports                    │
│  4. Sync animals/vaccinations/alerts (bidirectional)    │
│  5. Pull remote data → local insertIfAbsent             │
│  6. Mark synced=true                                    │
│                                                          │
│  Result: SUCCESS if any operation succeeds              │
│         FAILURE if all fail                             │
└─────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│              REMOTE SYNC (RemoteSync.kt)                 │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  pushLocals():                                           │
│  └── For each entity:                                    │
│      └── POST /api/v1/entity/ with local data           │
│                                                          │
│  pullAll():                                              │
│  └── GET /api/v1/entity/ → InsertIfAbsent locally       │
│      (Prevents overwriting local changes)               │
│                                                          │
│  Order matters:                                          │
│  1. Animals (parent)                                    │
│  2. Vaccinations (child of animal)                      │
│  3. Reports (references animal + farmer)                │
│  4. Alerts (standalone)                                 │
└─────────────────────────────────────────────────────────┘
```

### Photo Upload Pipeline

```
Farmer submits report → photo saved locally at:
  /data/data/com.pashurakshak.app/files/photos/report-uuid.jpg

Sync Worker execution:
  1. Query getReportsPendingPhotoUpload()
  2. For each report:
     a. Call B2UploadService.uploadFile(localPath)
        → b2_authorize_account (auth)
        → b2_get_upload_url (get upload endpoint)
        → b2_upload_file (multipart POST with file)
        → Returns: { bucketName, fileName, url }
     b. Update report:
        photo_remote_url = "https://cdn.com/bucket/filename.jpg"
        synced = 0 (still needs POST to backend)
  3. Re-trigger push for reports with photo URLs
```

**Backblaze B2 Credentials**:
```properties
# local.properties (gitignored, BuildConfig generated)
B2_APPLICATION_KEY_ID=<your_backblaze_key_id>
B2_APPLICATION_KEY=<your_backblaze_application_key>
B2_BUCKET_NAME=<your_private_bucket_name>
```

Never commit real credentials. `.gitignore` excludes `local.properties`, but any
key that was previously committed or shared must be revoked and rotated in B2.

---

## 🧪 Development & Build

### Prerequisites

#### Android Studio Setup
1. **Install Android Studio** (Arctic Fox or later)
2. **SDK Manager**:
   - Install Android SDK Platform 37 (targetSdk)
   - Install Android SDK Platform 24 (minSdk)
   - Install Android SDK Build Tools 34.0.0
   - Install Android Emulator
3. **Gradle**: Wrapper configured (version 9.6.0)

#### Backend Setup (shared-backend)
1. **Node.js**: Version 18 or higher
2. **Turso CLI** (optional, for local testing):
   ```bash
   npm install -g @libsql/cli
   ```

### Initial Configuration

#### 1. Clone Repository
```bash
git clone https://github.com/yourusername/PasuRakshak.git
cd PasuRakshak
```

#### 2. Android Secrets (`local.properties`)
Copy from example:
```bash
cp app-local.properties.example app-local.properties
```

Fill in:
```properties
B2_APPLICATION_KEY_ID=your_backblaze_key_id
B2_APPLICATION_KEY=your_backblaze_app_key
B2_BUCKET_NAME=your_bucket_name
API_BASE_URL=http://localhost:3000
APP_API_KEY=your_farmer_app_api_key  # Register app first!
```

#### 3. Firebase Configuration
```bash
# Create Firebase project at console.firebase.google.com
# Add Android app with package name: com.pashurakshak.app
# Download google-services.json
# Move to: app/google-services.json (NOT committed to git)
```

**Optional**: Phone Auth requires SHA-1 fingerprint:
```bash
./gradlew signingReport
```

Add SHA-1 to Firebase Console → Project Settings → Your Apps → Add fingerprint.

#### 4. Backend Environment (shared-backend/.env)
```bash
cd shared-backend
cp .env.example .env
```

Fill in required variables:
```env
TURSO_DATABASE_URL=libsql://your-db.turso.io
TURSO_AUTH_TOKEN=your_jwt_token
PORT=3000
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:5174
ADMIN_SECRET=change-me-to-long-random-string
FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}
```

**Generate Firebase service account JSON**:
1. Firebase Console → Project Settings → Service Accounts
2. Click "Generate new private key"
3. Copy JSON to `.env` (single-line, escaped quotes)

#### 5. Register Applications with Backend
```bash
# Farmer App (Android)
curl -X POST http://localhost:3000/api/v1/core/apps \
  -H "Content-Type: application/json" \
  -H "X-Admin-Secret: your_admin_secret" \
  -d '{"name":"Farmer Android App"}'

# Save the returned api_key to local.properties
```

Same process for Vet App, Gov Portal, and Vet Registration.

---

### Build Commands

#### Android Debug Build
```bash
# From project root
./gradlew assembleDebug
```

Output: `app-farmer/build/outputs/apk/debug/app-farmer-debug.apk`

#### Android Release Build
```bash
./gradlew assembleRelease
```

Requires keystore configuration (not included in repo).

#### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

#### Backend Start
```bash
cd shared-backend
npm install
npm run migrate  # Apply database migrations
npm start        # Start server on PORT (default 3000)
```

Test health endpoint:
```bash
curl http://localhost:3000/health
# Response: {"status":"ok"}
```

#### Gov Portal Dev Server
```bash
cd gov-portal
npm install
cp .env.example .env
# Fill VITE_API_BASE_URL and VITE_APP_API_KEY
npm run dev
```

Opens at: `http://localhost:5174`

#### Vet Registration Dev Server
```bash
cd vet-registration
npm install
cp .env.example .env
# Fill environment vars
npm run dev
```

Opens at: `http://localhost:5173`

---

## 🚀 Deployment

### Android App Deployment

#### Generate Signed APK/AAB
1. **Create Keystore**:
   ```bash
   keytool -genkey -v -keystore pasurakshak-release.keystore \
     -alias pasurakshak -keyalg RSA -keysize 2048 -validity 10000
   ```

2. **Update `gradle.properties`**:
   ```properties
   android.useAndroidX=true
   android.enableJetifier=true
   ```

3. **Configure `signingConfigs` in `build.gradle.kts`**:
   ```kotlin
   signingConfigs {
       create("release") {
           storeFile = file("../pasurakshak-release.keystore")
           storePassword = System.getenv("KEYSTORE_PASSWORD")
           keyAlias = "pasurakshak"
           keyPassword = System.getenv("KEY_PASSWORD")
       }
   }
   ```

4. **Build AAB for Play Store**:
   ```bash
   ./gradlew bundleRelease
   ```

Upload `.aab` to Google Play Console.

#### Google Play Console Setup
1. **Create new app**: "PashuRakshak - Livestock Health"
2. **Category**: Health & Fitness → Agriculture
3. **Target audience**: Farmers (ages 25-60+)
4. **Content rating**: Complete questionnaire → ESRB/PEGI rating
5. **Store listing**: Screenshots, description, graphics
6. **Pricing**: Free (India focus)
7. **Upload APK/AAB**: Production rollout

---

### Backend Deployment (Render.com)

#### Pre-deployment Checklist
- [ ] `.env` file with production values
- [ ] Firebase service account JSON updated
- [ ] Turso database provisioned (production tier)
- [ ] ADMIN_SECRET set to cryptographically strong value
- [ ] CORS_ALLOWED_ORIGINS includes deployed frontend URLs
- [ ] Deployed URL allow-listed in Firebase Console

#### Deploy to Render
1. **Create new Web Service**:
   - Connect GitHub repo: `PasuRakshak/shared-backend`
   - Root directory: `shared-backend`
   - Build command: `npm install`
   - Start command: `npm start`
   - Environment: Node

2. **Set Environment Variables** (from `.env`):
   - TURSO_DATABASE_URL
   - TURSO_AUTH_TOKEN
   - CORS_ALLOWED_ORIGINS (e.g., `https://gov-portal.vercel.app,https://vet-registration.vercel.app`, or `*` while previews change)
   - ADMIN_SECRET
   - SESSION_SECRET (**required** — Render sets `NODE_ENV=production`)
   - FIREBASE_SERVICE_ACCOUNT_JSON
   - MAIL_MODE + email provider keys (see note below)
   - GROQ_API_KEY, B2_* (optional)

   > **Gmail SMTP on Render Free will not work.** Render blocks outbound
   > SMTP ports `25`, `465`, and `587`. Use `MAIL_MODE=brevo` (or `resend`)
   > with `BREVO_API_KEY` / `RESEND_API_KEY`, or upgrade the Render instance
   > to a paid plan and keep `MAIL_MODE=smtp`.

3. **Deploy**:
   ```bash
   # Option 1: Use Render CLI
   render deploy
   
   # Option 2: GitHub Auto-deploy
   # Commit changes → GitHub pushes to Render → Auto-builds
   ```

4. **Monitor Logs**:
   - Render Dashboard → Logs tab
   - Watch for migration errors, connection issues

#### Alternative: Railway.app
Similar process:
1. Create new project
2. Connect GitHub repository
3. Configure build/start commands
4. Set env vars via dashboard
5. Deploy

---

### Frontend Deployment (two separate Vercel projects)

Both web apps are standalone Vite projects with their own `vercel.json`.
Create **two separate Vercel projects** from this repo and set the
**Root Directory** for each — never deploy from the repository root.

#### Gov Portal → Vercel
1. Import the repo → Root Directory: `gov-portal`
2. Framework: Vite · Build: `npm run build` · Output: `dist`
3. Env vars (Production + Preview):
   - `VITE_API_BASE_URL`: backend URL, e.g. `https://pashurakshak-ilol.onrender.com`
   - `VITE_APP_API_KEY`: Gov Portal app key
4. Deploy, then add the URL to the backend's `CORS_ALLOWED_ORIGINS`.
5. Redeploy after changing any `VITE_*` value (they are baked at build time).

#### Vet Registration → Vercel
1. Import the same repo → Root Directory: `vet-registration`
2. Framework: Vite · Build: `npm run build` · Output: `dist`
3. Env vars (Production + Preview):
   - `VITE_API_BASE_URL`: same backend URL
   - `VITE_APP_API_KEY`: Vet Registration app key
4. Deploy, then add the URL to the backend's `CORS_ALLOWED_ORIGINS`.

> OTP emails are sent by the backend, not by these static sites. To make them
> work on Render Free, configure Brevo/Resend in `shared-backend` (see above).

---

## 🧩 API Error Handling

### Standard Error Responses

All endpoints follow consistent error format:

```json
{
  "error": "error_code",
  "message": "Human-readable explanation",
  "details": { ... }  // Optional, may contain validation errors
}
```

#### Common Error Codes

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `missing_app_key` | 401 | No `X-App-Key` header provided |
| `invalid_app_key` | 401 | App key not found in database |
| `missing_bearer_token` | 401 | No `Authorization: Bearer <token>` header |
| `invalid_firebase_token` | 401 | Firebase ID token expired or malformed |
| `firebase_not_configured` | 503 | Backend lacks Firebase credentials |
| `missing_admin_secret` | 401 | Admin-only route called without secret |
| `invalid_admin_secret` | 401 | Admin secret doesn't match `.env` |
| `validation_error` | 400 | Request body missing required fields |
| `duplicate_record` | 409 | Record with same unique key exists |
| `resource_not_found` | 404 | Entity ID doesn't exist |
| `internal_server_error` | 500 | Unexpected server-side exception |

### Example Error Responses

**Invalid App Key**:
```json
{
  "error": "invalid_app_key",
  "message": "Invalid X-App-Key — no registered app matches this key."
}
```

**Missing Required Field**:
```json
{
  "error": "validation_error",
  "message": "Required field 'symptoms' is missing",
  "details": {
    "field": "symptoms",
    "received": null
  }
}
```

**Firebase Not Configured**:
```json
{
  "error": "firebase_not_configured",
  "message": "FIREBASE_SERVICE_ACCOUNT_JSON has not been checked yet. Add the service account JSON (Firebase Console → Project Settings → Service Accounts) to your .env."
}
```

---

## 🎨 UI/UX Guidelines

### Design System

**Material 3 Theme** (`ui/theme/Theme.kt`):
```kotlin
val PashuRakshakColorScheme = colorScheme(
  primary = Color(0xFF6750A4),    // Purple accent
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEADDFF),
  secondary = Color(0xFF625B71),
  tertiary = Color(0xFF7D5260),
  background = Color(0xFFFFFBFE),
  surface = Color(0xFFFFFBFE),
  error = Color(0xFFB3261E),      // Red for alerts/errors
)
```

**Typography**:
```kotlin
typography = Typography(
  headlineLarge = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = 28.sp,
    fontWeight = FontWeight.Bold
  ),
  bodyMedium = TextStyle(
    fontSize = 16.sp,
    lineHeight = 24.sp
  ),
  // ... other styles
)
```

### Risk Level Colors
Consistent throughout the app:
- **Low Risk**: Green `#2E7D32` (safe, non-urgent)
- **Medium Risk**: Orange `#EF6C00` (caution, attention needed)
- **High Risk**: Red `#D32F2F` (critical, immediate action)

These colors used in:
- Risk badges on case list items
- Status chips in report tables
- Risk score meters
- Alert icons

### Iconography
Using Material Icons (Google's official icon set):
- 🏠 `home` - Home navigation
- 🐃 `animal` - Animal management
- 📝 `note_add` - Report new case
- 💉 `medical_services` - Vaccination tracking
- 🔔 `notifications` - Alerts feed
- 🚨 `bug_report` - Outbreak clusters
- ✅ `check_circle` - Approved status
- ❌ `cancel` - Rejected status
- ⏳ `hourglass_empty` - Pending status

---

## 📊 Data Flow Diagrams

### Report Creation to Field Inspection

```
Farmer Opens App
    ↓
Tap "Report Sick Animal"
    ↓
Camera Permission Request
    ↓
Take Photo of Symptoms
    ↓
Enter Symptoms (multiselect): fever, loss_of_appetite
    ↓
Auto-detect GPS Location (lat/long)
    ↓
Calculate Risk Score Locally:
   fever (15) + loss_of_appetite (10) = 25 points (LOW RISK)
    ↓
Save to Local DB:
   INSERT INTO symptom_reports VALUES (...)
   synced = 0
    ↓
App closes → Worker scheduled for next sync window
    ↓
SYNC EVENT TRIGGERED (connectivity restored)
    ↓
Upload Photo to Backblaze B2:
   b2_upload_file → photo_remote_url received
    ↓
UPDATE symptom_reports SET photo_remote_url = '...'
    ↓
Push Report to Backend:
   POST /api/v1/pashu-health/reports
   Body: { id, animal_id, farmer_id, symptoms, 
           photo_remote_url, latitude, longitude, ... }
    ↓
Cluster Detection Runs:
   Query: SELECT * FROM symptom_reports 
          WHERE lat/lon within 5km AND created_at > NOW()-7days
   If ≥3 reports → CREATE pashu_alerts
                  send_push_to_uid(vets_in_district)
    ↓
Local DB Update:
   UPDATE symptom_reports SET synced = 1
    ↓
Sync Banner Shows: "✓ Synced"
```

### Vet Application Approval Workflow

```
Vet Submits Application (Web App)
    ↓
POST /pashu-health/vet-applications
Body: { veterinarianName: "Dr. Kumar", pincode: "244701", ... }
    ↓
Server Resolves Pincode → District:
   GET /core/pincode/244701
   Response: { city: "Rampur", district: "Rampur", state: "UP" }
    ↓
INSERT INTO pashu_vet_applications:
   district = 'Rampur', status = 'pending'
    ↓
Vet sees Status Page: "Your application is under review"
    ↓
DM Logs Into Gov Portal
    ↓
Dashboard Filters Applications:
   SELECT * FROM pashu_vet_applications 
   WHERE district = 'Rampur' AND status = 'pending'
    ↓
DM Reviews Application Card
   Checks: qualifications, experience, coverage areas
    ↓
DM Clicks "Approve"
    ↓
POST /pashu-health/vet-applications/:id/approve
Body: { reviewerNotes: "Verified BVSc & AH degree" }
    ↓
BEGIN TRANSACTION
   UPDATE pashu_vet_applications SET status = 'approved', reviewer_notes = '...'
   INSERT INTO pashu_vets (name, phone, assigned_district, status='active')
COMMIT
    ↓
Vet gets pushed notification: "Application Approved!"
    ↓
Vet logs into Status Page: Sees "✅ Approved" message
```

---

## 🔧 Troubleshooting & Known Issues

### Common Issues

#### Issue 1: Firebase Phone Auth Fails on Emulator
**Symptom**: OTP verification fails with "INVALID_VERIFICATION_CODE"

**Cause**: Emulator doesn't receive SMS; Firebase requires real device for phone auth

**Solution**:
1. Use Physical Device connected via USB debugging
2. Or use Firebase Test Lab (cloud emulators with SMS forwarding)
3. Alternatively, enable "Emulator bypass" in Firebase Console → Authentication → Sign-in method → Phone → Enable

#### Issue 2: Photos Don't Upload to Backblaze B2
**Symptom**: Sync banner shows "2 reports pending photo upload" indefinitely

**Causes**:
1. Missing B2 credentials in `local.properties`
2. Bucket permissions incorrect (needs `b2_upload_file` permission)
3. Network timeout during large photo upload

**Solutions**:
```properties
# Verify local.properties has ALL three keys filled:
B2_APPLICATION_KEY_ID=xxx
B2_APPLICATION_KEY=xxx
B2_BUCKET_NAME=xxx
```

Check bucket policy in Backblaze dashboard:
- Allow: `listBuckets`, `writeFiles`
- Restrict: `deleteFiles` (security best practice)

Implement retry logic in `B2UploadService.kt`:
```kotlin
var retryCount = 0
while (retryCount < 3 && uploadFailed) {
    delay(exponentialBackoff(retryCount))
    retryCount++
}
```

#### Issue 3: Sync Loop After Migration
**Symptom**: App enters infinite sync loop, consuming battery

**Cause**: Migration changed table schema, but `PRAGMA user_version` still old

**Diagnosis**:
```kotlin
Log.d("MIGRATION", "Current version: $currentUserVersion")
Log.d("MIGRATION", "Latest migration: ${Migrations.all.maxByOrNull { it.version }?.version}")
```

**Solution**: Manually reset version in database (DEBUG builds only):
```bash
adb shell sqlite3 /data/data/com.pashurakshak.app/files/pashurakshak.db
PRAGMA user_version = 2;  -- Match latest migration
.exit
```

Or better: improve migration rollback handling in `Migrations.kt`.

#### Issue 4: Admin Secret Too Weak
**Symptom**: Security scanner warns about weak admin secret

**Current Value**: `<redacted>` (replace it — never commit real secrets)

**Fix**: Generate cryptographically strong secret:
```bash
node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
```

Replace in `.env`:
```env
ADMIN_SECRET=<generated-long-random-string>
```

Re-run: `curl -X POST http://localhost:3000/api/v1/core/apps ...` with new secret.

#### Issue 5: Cluster Detection Not Triggering
**Symptom**: 3+ nearby reports submitted, but no alert created

**Checklist**:
1. All reports have valid lat/long coordinates?
2. Created within last 7 days?
3. Status not already 'resolved'?
4. Backend cluster detection function running? (Check logs)

**Debug**:
```javascript
// In shared-backend/logs.txt
[migrate] all migrations up to date
[pashu-health/reports] cluster detection failed: undefined
```

Possible causes:
- Bounding box calculation bug (±0.05 degrees ≈ 5km at equator, less at poles)
- Haversine formula floating-point precision issue
- Database query timeout on large datasets

**Fix**: Increase database index on `symptom_reports(created_at, latitude, longitude)` composite index.

---

## 🚧 Future Roadmap

### Phase 9: AI-Powered Diagnostics (Planned)
- Integrate TensorFlow Lite model trained on livestock disease images
- Predict disease type from photo + symptom text
- Confidence scores for each prediction
- Historical comparison: "Similar to 12 past cases in your district"

### Phase 10: Lab Integration
- Sample submission tracking with courier APIs
- PCR test result integration (for Foot-and-Mouth Disease screening)
- Lab portal for uploading pathology reports

### Phase 11: Multi-Language Support
- Hinglish (Hindi + English) interface option
- Regional language support: Bengali, Tamil, Telugu
- Voice input for symptom reporting (text-to-speech)

### Phase 12: Blockchain Provenance
- Immutable vaccination records on Hyperledger Fabric
- Anti-counterfeit medicine verification via QR codes
- Supply chain tracking for veterinary products

### Phase 13: IoT Sensor Integration
- Temperature sensors attached to cattle collars
- Activity monitors (accelerometers for lameness detection)
- Real-time vitals streaming to app dashboard

### Phase 14: Insurance Claim Automation
- Digital claim forms linked to disease diagnosis
- Automated payout based on verified cases
- Partnership with National Agricultural Insurance Scheme (NAIS)

---

## 👥 Team & Contributions

### Development Team
- **Project Lead**: Piyush Chaurasiya
- **Android Development**: Kotlin experts with Material Compose experience
- **Backend Development**: Node.js/Express engineers
- **UI/UX Design**: Material 3 theming specialists
- **QA Testing**: Rural field testing partnerships

### How to Contribute
1. Fork repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

**Coding Standards**:
- Android: Kotlin coding conventions (https://kotlinlang.org/docs/coding-conventions.html)
- Backend: ESLint + Prettier (configure in future)
- Commits: Conventional commits format (`feat:`, `fix:`, `docs:`, `style:`, `refactor:`)

---

## 📚 Additional Resources

### External Documentation Links
- [Firebase Phone Auth Guide](https://firebase.google.com/docs/auth/android/phone-auth)
- [Material Design 3](https://m3.material.io/)
- [Turso/libSQL Docs](https://turso.tech/docs)
- [Backblaze B2 API Reference](https://www.backblaze.com/b2/docs/)
- [Android WorkManager](https://developer.android.com/topic/libraries/work/runtime)

### Indian Govt. Initiatives Alignment
- **Digital India**: Paperless livestock registration
- **National Animal Disease Reporting System (NADRS)**: Real-time surveillance
- **National Livestock Mission (NLM)**: Breed improvement tracking
- **e-Gram Swaraj Portal**: Village-level integration

---

## 📄 License

This project is developed for social impact and made available under the MIT License. See `LICENSE` file for details.

**Free for non-commercial use**. Commercial licensing available upon request.

---

## 📞 Contact & Support

### Support Channels
- **Issue Tracker**: GitHub Issues (bug reports, feature requests)
- **Email**: support@pashurakshak.org (fictional placeholder)
- **Emergency Hotline**: +91-XXXXXXXXXX (for disease outbreak coordination)

### Office Hours
Monday-Friday: 9:00 AM - 6:00 PM IST
Saturday: 9:00 AM - 1:00 PM IST

---

<div align="center">

**Built with ❤️ for Indian farmers and their livestock**

© 2026 PashuRakshak Team. All rights reserved.

[View on GitHub](https://github.com/yourusername/PasuRakshak)

</div>
