package com.pashurakshak.app.data

import android.app.Activity
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.local.TursoClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Firebase Phone Auth + local phone→role mapping.
 *
 * Firebase knows the phone number but not farmer-vs-vet, so after OTP success the
 * chosen role is stored in the local `farmers`/`vets` table keyed by Firebase UID.
 *
 * Requires app/google-services.json (see google-services.json.example / README);
 * every entry point degrades to a friendly error when Firebase isn't configured.
 */
class AuthRepository(private val db: TursoClient) {

    /**
     * Starts SMS/instant verification. [onCodeSent] fires with the verification id
     * (→ OTP screen); [onVerificationCompleted] fires on instant/auto verification.
     */
    fun sendVerificationCode(
        activity: Activity,
        e164PhoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onVerificationCompleted: (credential: PhoneAuthCredential) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        try {
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onVerificationCompleted(credential)
                }

                override fun onVerificationFailed(error: FirebaseException) {
                    onError(error.message ?: "Phone verification failed")
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    onCodeSent(verificationId)
                }
            }
            val options = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
                .setPhoneNumber(e164PhoneNumber)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build()
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (error: Throwable) {
            onError(configErrorMessage(error))
        }
    }

    /** Exchanges an OTP + verification id for a Firebase session; reports uid. */
    fun signInWithOtp(
        verificationId: String,
        code: String,
        onSuccess: (uid: String) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        try {
            val credential = PhoneAuthProvider.getCredential(verificationId, code)
            signInWithCredential(credential, onSuccess, onError)
        } catch (error: Throwable) {
            onError(configErrorMessage(error))
        }
    }

    /** Sign in with an auto-retrieved/instant credential (no manual OTP entry). */
    fun signInWithCredential(
        credential: PhoneAuthCredential,
        onSuccess: (uid: String) -> Unit,
        onError: (message: String) -> Unit,
    ) {
        try {
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener { result ->
                    val user: FirebaseUser? = result.user
                    if (user != null) {
                        onSuccess(user.uid)
                    } else {
                        onError("Sign-in succeeded but no user was returned")
                    }
                }
                .addOnFailureListener { error ->
                    onError(configErrorMessage(error))
                }
        } catch (error: Throwable) {
            onError(configErrorMessage(error))
        }
    }

    /**
     * Persist the phone→role mapping locally. Firebase has no notion of roles, so
     * the local tables are the source of truth for farmer-vs-vet.
     */
    suspend fun saveRoleMapping(uid: String, phone: String, role: SessionManager.Role) {
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
                    phone,
                    phone,
                )
                db.execute("UPDATE vets SET phone = ? WHERE id = ?", phone, uid)
            }
        }
        // Best-effort: login is already done; a failed registration retries next login.
        registerDeviceToken(role)
    }

    /**
     * POSTs the FCM token to shared-backend /api/v1/core/devices (app key + Firebase
     * ID token) so the server can push cluster alerts to this device. Never throws —
     * device registration must not break the login flow.
     */
    private suspend fun registerDeviceToken(role: SessionManager.Role) {
        try {
            withContext(Dispatchers.IO) {
                val user = FirebaseAuth.getInstance().currentUser
                    ?: return@withContext
                val idToken = Tasks.await(user.getIdToken(false)).token
                    ?: return@withContext
                val fcmToken = Tasks.await(FirebaseMessaging.getInstance().token)
                val payload = JSONObject()
                    .put("role", when (role) {
                        SessionManager.Role.FARMER -> "farmer"
                        SessionManager.Role.VET -> "vet"
                    })
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
                    connection.setRequestProperty("Authorization", "Bearer $idToken")
                    connection.outputStream.use {
                        it.write(payload.toString().toByteArray(Charsets.UTF_8))
                    }
                    val code = connection.responseCode
                    if (code !in 200..299) {
                        val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
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

    private fun configErrorMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return if (error is IllegalStateException || message.contains("Default FirebaseApp", ignoreCase = true)) {
            "Firebase isn't configured yet — add app/google-services.json (see README)"
        } else {
            error.message ?: "Phone auth failed"
        }
    }

    private companion object {
        const val TAG = "AuthRepository"
    }
}
