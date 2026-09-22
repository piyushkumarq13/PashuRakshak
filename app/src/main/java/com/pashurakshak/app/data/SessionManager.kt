package com.pashurakshak.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Real login session, backed by Firebase Phone Auth + local persistence.
 *
 * Flow: phone entry → OTP verified ([onOtpVerified] stores uid/phone) →
 * role selection ([completeLogin] stores farmer/vet). The phone→role mapping is
 * persisted in the local `farmers`/`vets` tables by [AuthRepository] (Firebase
 * itself doesn't know roles).
 */
object SessionManager {

    enum class Role(val label: String) {
        FARMER("Farmer"),
        VET("Vet"),
    }

    private const val PREFS_NAME = "pashurakshak_session"
    private const val KEY_UID = "uid"
    private const val KEY_PHONE = "phone"
    private const val KEY_ROLE = "role"

    private var prefs: SharedPreferences? = null

    /** Firebase UID of the signed-in user (null until OTP verification succeeds). */
    var uid: String? = null
        private set

    /** E.164 phone number the user verified. */
    var phone: String? = null
        private set

    var role: Role? = null
        private set

    val isLoggedIn: Boolean
        get() = role != null && uid != null

    /** Farmer-side entity id = Firebase UID (safe fallback when not signed in as farmer). */
    val farmerId: String
        get() = uid ?: "anonymous-farmer"

    /**
     * Vet-side recipient id. Farmer reports address vet alerts to a shared id; the vet
     * alerts screen queries by role, so this only matters as a stable string.
     */
    val vetId: String
        get() = "all-vets"

    fun initialize(context: Context) {
        val stored = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = stored
        uid = stored.getString(KEY_UID, null)
        phone = stored.getString(KEY_PHONE, null)
        role = stored.getString(KEY_ROLE, null)?.let { name ->
            runCatching { Role.valueOf(name) }.getOrNull()
        }
    }

    /** Called once Firebase Phone Auth OTP verification succeeds (before role selection). */
    fun onOtpVerified(uid: String, phone: String) {
        this.uid = uid
        this.phone = phone
        prefs?.edit()
            ?.putString(KEY_UID, uid)
            ?.putString(KEY_PHONE, phone)
            ?.apply()
    }

    /** Called when the user picks Farmer/Vet — completes login and persists the role. */
    fun completeLogin(role: Role) {
        this.role = role
        prefs?.edit()
            ?.putString(KEY_ROLE, role.name)
            ?.apply()
    }

    fun logout() {
        role = null
        uid = null
        phone = null
        prefs?.edit()
            ?.remove(KEY_ROLE)
            ?.remove(KEY_UID)
            ?.remove(KEY_PHONE)
            ?.apply()
    }
}
