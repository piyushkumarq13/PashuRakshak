package com.pashurakshak.app.data

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.local.TursoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Custom backend auth — no Firebase Auth.
 *
 * Email OTP verifies ownership; a 6-digit PIN authenticates farmers (and vets
 * via PIN or password). Sessions are backend-signed tokens stored in
 * [SessionManager] and sent as `Authorization: Bearer <token>`.
 */
class AuthRepository(private val db: TursoClient) {

    sealed class AuthResult {
        data class Success(
            val token: String,
            val user: JSONObject,
            val profile: JSONObject?,
            val applicationStatus: String? = null,
        ) : AuthResult()

        data class Failure(
            val message: String,
            val code: String? = null,
            val applicationStatus: String? = null,
        ) : AuthResult()
    }

    data class OtpSendInfo(val maskedEmail: String, val cooldownSeconds: Int)

    data class FarmerCheck(
        val exists: Boolean,
        val hasCredential: Boolean,
        val requiresPinSetup: Boolean,
        val hasEmail: Boolean,
    )

    /* ───────────────────────── Farmer ───────────────────────── */

    suspend fun checkFarmer(phone: String): Result<FarmerCheck> = io {
        val (code, json) = post("/api/v1/auth/farmer/check", JSONObject().put("phone", phone), auth = false)
        if (code !in 200..299) throw IOException(json.optString("message", "Account check failed"))
        Result.success(
            FarmerCheck(
                exists = json.optBoolean("exists"),
                hasCredential = json.optBoolean("hasCredential"),
                requiresPinSetup = json.optBoolean("requiresPinSetup"),
                hasEmail = json.optBoolean("hasEmail"),
            ),
        )
    }

    suspend fun loginFarmer(phone: String, pin: String): AuthResult = io {
        val (code, json) = post(
            "/api/v1/auth/farmer/login",
            JSONObject().put("phone", phone).put("pin", pin),
            auth = false,
        )
        authResultFrom(code, json)
    }

    suspend fun registerFarmer(
        verifyToken: String,
        phone: String,
        pin: String,
        name: String,
        preferredLanguage: String,
        animalCount: Int?,
        village: String,
        pincode: String,
    ): AuthResult = io {
        val body = JSONObject()
            .put("verifyToken", verifyToken)
            .put("phone", phone)
            .put("pin", pin)
            .put("name", name)
            .put("preferredLanguage", preferredLanguage)
            .put("village", village)
            .put("pincode", pincode)
        if (animalCount != null) body.put("animalCount", animalCount)
        val (code, json) = post("/api/v1/auth/farmer/register", body, auth = false)
        authResultFrom(code, json)
    }

    suspend fun resetFarmerPin(verifyToken: String, pin: String): AuthResult = io {
        val (code, json) = post(
            "/api/v1/auth/farmer/reset-pin",
            JSONObject().put("verifyToken", verifyToken).put("pin", pin),
            auth = false,
        )
        authResultFrom(code, json)
    }

    /* ───────────────────────── Vet (app login only — registration is on the web) ───────────────────────── */

    suspend fun loginVet(phone: String, credential: String): AuthResult = io {
        val body = JSONObject()
            .put("phone", phone)
            .put("credential", credential)
            .put("client", "app")
        val (code, json) = post("/api/v1/auth/vet/login", body, auth = false)
        authResultFrom(code, json)
    }

    suspend fun resetVetPin(verifyToken: String, pin: String): AuthResult = io {
        val (code, json) = post(
            "/api/v1/auth/vet/reset-pin",
            JSONObject().put("verifyToken", verifyToken).put("pin", pin).put("pinType", "pin"),
            auth = false,
        )
        authResultFrom(code, json)
    }

    /* ───────────────────────── OTP ───────────────────────── */

    suspend fun sendOtp(email: String, purpose: String, phone: String? = null): Result<OtpSendInfo> = io {
        val body = JSONObject().put("email", email).put("purpose", purpose)
        if (!phone.isNullOrBlank()) body.put("phone", phone)
        val (code, json) = post("/api/v1/auth/otp/send", body, auth = false)
        if (code !in 200..299) throw OtpException.from(code, json)
        Result.success(
            OtpSendInfo(
                maskedEmail = json.optString("email"),
                cooldownSeconds = json.optInt("retryAfterSeconds", 60),
            ),
        )
    }

    suspend fun verifyOtp(email: String, purpose: String, code: String): Result<String> = io {
        val (status, json) = post(
            "/api/v1/auth/otp/verify",
            JSONObject().put("email", email).put("purpose", purpose).put("code", code),
            auth = false,
        )
        if (status !in 200..299) throw OtpException.from(status, json)
        val token = json.optString("verifyToken")
        if (token.isBlank()) throw IOException("Verification succeeded but no token was returned")
        Result.success(token)
    }

    /* ───────────────────────── Session helpers ───────────────────────── */

    /** Stores the session from a successful auth response + best-effort local sync. */
    suspend fun establishSession(
        token: String,
        user: JSONObject,
        profile: JSONObject?,
        role: SessionManager.Role,
    ) {
        val profileComplete = profile != null && !profile.isNull("village")
        SessionManager.establishSession(
            token = token,
            userId = user.optString("id"),
            userPhone = user.optString("phone").takeIf { it.isNotBlank() },
            role = role,
            name = user.optString("name").takeIf { it.isNotBlank() },
            email = user.optString("email").takeIf { it.isNotBlank() },
            language = user.optString("preferred_language").takeIf { it.isNotBlank() } ?: "hi",
            animalCount = profile?.optInt("animal_count") ?: 0,
            village = profile?.optString("village")?.takeIf { it.isNotBlank() },
            pincode = profile?.optString("pincode")?.takeIf { it.isNotBlank() },
            profileComplete = profileComplete,
        )
        // Application-scoped so navigation can't cancel local row + device registration.
        syncLocalAccount(role)
    }

    /** Public entry for FCM token rotation (PushMessagingService.onNewToken). */
    suspend fun registerDeviceTokenPublic(role: SessionManager.Role) = registerDeviceToken(role)

    /**
     * Local farmers/vets row (referenced by local profile FK) + FCM token POST
     * to /core/devices. Best-effort: login is already done.
     */
    private suspend fun syncLocalAccount(role: SessionManager.Role) {
        try {
            val uid = SessionManager.uid ?: return
            val phone = SessionManager.phone.orEmpty()
            when (role) {
                SessionManager.Role.FARMER -> db.execute(
                    "INSERT OR REPLACE INTO farmers (id, phone, created_at) VALUES (?, ?, ?)",
                    uid,
                    phone,
                    System.currentTimeMillis(),
                )

                SessionManager.Role.VET -> {
                    db.execute(
                        "INSERT OR IGNORE INTO vets (id, name, phone, assigned_village) VALUES (?, ?, ?, '')",
                        uid,
                        SessionManager.name ?: phone,
                        phone,
                    )
                    db.execute("UPDATE vets SET phone = ? WHERE id = ?", phone, uid)
                }
            }
            registerDeviceToken(role)
        } catch (error: Exception) {
            Log.w(TAG, "Local account sync skipped: ${error.message}")
        }
    }

    /**
     * POSTs the FCM token to /core/devices (app key + session token). Never throws.
     */
    private suspend fun registerDeviceToken(role: SessionManager.Role) {
        try {
            withContext(Dispatchers.IO) {
                val sessionToken = SessionManager.sessionToken ?: return@withContext
                val fcmToken = try {
                    Tasks.await(FirebaseMessaging.getInstance().token)
                } catch (_: Exception) {
                    // Firebase not configured (no google-services.json yet).
                    return@withContext
                }
                val payload = JSONObject()
                    .put(
                        "role",
                        when (role) {
                            SessionManager.Role.FARMER -> "farmer"
                            SessionManager.Role.VET -> "vet"
                        },
                    )
                    .put("fcmToken", fcmToken)

                val connection = URL("${BuildConfig.API_BASE_URL}/api/v1/core/devices")
                    .openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
                    connection.setRequestProperty("Authorization", "Bearer $sessionToken")
                    connection.outputStream.use {
                        it.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val body = connection.errorStream
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        Log.w(TAG, "Device registration failed (HTTP $code): $body")
                    } else {
                        Log.d(TAG, "Device token registered for role=${payload.getString("role")}")
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Device registration skipped: ${error.message}")
        }
    }

    /* ───────────────────────── HTTP plumbing ───────────────────────── */

    private fun authResultFrom(code: Int, json: JSONObject): AuthResult {
        if (code !in 200..299) {
            return AuthResult.Failure(
                message = json.optString("message", "Sign-in failed"),
                code = json.optString("error").takeIf { it.isNotBlank() },
                applicationStatus = json.optString("applicationStatus").takeIf { it.isNotBlank() },
            )
        }
        val token = json.optString("token")
        val user = json.optJSONObject("user")
        if (token.isBlank() || user == null) {
            return AuthResult.Failure("Sign-in succeeded but no session was returned")
        }
        return AuthResult.Success(
            token = token,
            user = user,
            profile = json.optJSONObject("profile"),
            applicationStatus = json.optString("applicationStatus").takeIf { it.isNotBlank() },
        )
    }

    /** Blocking POST — must only be called from Dispatchers.IO. */
    private fun post(path: String, body: JSONObject, auth: Boolean): Pair<Int, JSONObject> {
        val connection = URL("${BuildConfig.API_BASE_URL}$path")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-App-Key", BuildConfig.APP_API_KEY)
            if (auth) {
                val sessionToken = SessionManager.sessionToken
                    ?: throw IOException("Not signed in")
                connection.setRequestProperty("Authorization", "Bearer $sessionToken")
            }
            connection.outputStream.use {
                it.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            return code to json
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /** OTP endpoint failures → user-facing message with optional rate-limit wait. */
    class OtpException(
        message: String,
        val code: String?,
        val retryAfterSeconds: Int?,
    ) : Exception(message) {
        companion object {
            fun from(status: Int, json: JSONObject): OtpException = OtpException(
                message = json.optString("message", "Could not send/verify the code."),
                code = json.optString("error").takeIf { it.isNotBlank() },
                retryAfterSeconds = json.optInt("retryAfterSeconds").takeIf { it > 0 },
            )
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
