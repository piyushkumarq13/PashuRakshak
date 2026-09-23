package com.pashurakshak.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Login session backed by the shared-backend (email OTP + PIN/password).
 *
 * The role is fixed per install: the farmer app (applicationId
 * com.pashurakshak.app) is always FARMER, the vet app (com.pashurakshak.vet)
 * is always VET. Sessions are backend-signed tokens sent as
 * `Authorization: Bearer <token>` on every API call.
 */
object SessionManager {

    enum class Role(val label: String) {
        FARMER("Farmer"),
        VET("Vet"),
    }

    private const val PREFS_NAME = "pashurakshak_session"
    private const val KEY_SESSION_TOKEN = "sessionToken"
    private const val KEY_UID = "uid"
    private const val KEY_PHONE = "phone"
    private const val KEY_ROLE = "role"
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PREFERRED_LANGUAGE = "preferredLanguage"
    private const val KEY_ANIMAL_COUNT = "animalCount"
    private const val KEY_VILLAGE = "village"
    private const val KEY_PINCODE = "pincode"
    private const val KEY_PROFILE_COMPLETE = "profileComplete"

    private const val VET_PACKAGE = "com.pashurakshak.vet"

    private var prefs: SharedPreferences? = null

    /** Role of this install — set once from the applicationId at startup. */
    var appRole: Role = Role.FARMER
        private set

    var sessionToken: String? = null
        private set

    var uid: String? = null
        private set

    var phone: String? = null
        private set

    var role: Role? = null
        private set

    var name: String? = null
        private set

    var email: String? = null
        private set

    var preferredLanguage: String? = null
        private set

    var animalCount: Int = 0
        private set

    var village: String? = null
        private set

    var pincode: String? = null
        private set

    /** Farmer profile saved (register/login returned one). Drives onboarding skip. */
    var profileComplete: Boolean = false
        private set

    val isLoggedIn: Boolean
        get() = role != null && uid != null && sessionToken != null

    val farmerId: String
        get() = uid ?: "anonymous-farmer"

    val vetId: String
        get() = uid ?: "all-vets"

    fun initialize(context: Context) {
        val stored = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = stored
        appRole = if (context.packageName == VET_PACKAGE) Role.VET else Role.FARMER
        sessionToken = stored.getString(KEY_SESSION_TOKEN, null)
        uid = stored.getString(KEY_UID, null)
        phone = stored.getString(KEY_PHONE, null)
        name = stored.getString(KEY_NAME, null)
        email = stored.getString(KEY_EMAIL, null)
        preferredLanguage = stored.getString(KEY_PREFERRED_LANGUAGE, null)
        animalCount = stored.getInt(KEY_ANIMAL_COUNT, 0)
        village = stored.getString(KEY_VILLAGE, null)
        pincode = stored.getString(KEY_PINCODE, null)
        profileComplete = stored.getBoolean(KEY_PROFILE_COMPLETE, false)
        val storedRole = stored.getString(KEY_ROLE, null)?.let { runCatching { Role.valueOf(it) }.getOrNull() }
        // Sessions without a backend token (pre-rebuild installs) must re-login.
        if (sessionToken == null) {
            clearInMemory()
        } else {
            role = storedRole
        }
    }

    /** Stores a fresh backend session (token + user + optional farmer profile). */
    fun establishSession(
        token: String,
        userId: String,
        userPhone: String?,
        role: Role,
        name: String?,
        email: String?,
        language: String?,
        animalCount: Int = 0,
        village: String?,
        pincode: String?,
        profileComplete: Boolean,
    ) {
        this.sessionToken = token
        this.uid = userId
        this.phone = userPhone
        this.role = role
        this.name = name
        this.email = email
        this.preferredLanguage = language
        this.animalCount = animalCount
        this.village = village
        this.pincode = pincode
        this.profileComplete = profileComplete
        prefs?.edit()
            ?.putString(KEY_SESSION_TOKEN, token)
            ?.putString(KEY_UID, userId)
            ?.putString(KEY_PHONE, userPhone)
            ?.putString(KEY_ROLE, role.name)
            ?.putString(KEY_NAME, name)
            ?.putString(KEY_EMAIL, email)
            ?.putString(KEY_PREFERRED_LANGUAGE, language)
            ?.putInt(KEY_ANIMAL_COUNT, animalCount)
            ?.putString(KEY_VILLAGE, village)
            ?.putString(KEY_PINCODE, pincode)
            ?.putBoolean(KEY_PROFILE_COMPLETE, profileComplete)
            ?.apply()
    }

    fun setFarmerProfile(
        name: String,
        email: String,
        preferredLanguage: String,
        animalCount: Int,
        village: String,
        pincode: String,
    ) {
        this.name = name
        this.email = email
        this.preferredLanguage = preferredLanguage
        this.animalCount = animalCount
        this.village = village
        this.pincode = pincode
        this.profileComplete = true
        prefs?.edit()
            ?.putString(KEY_NAME, name)
            ?.putString(KEY_EMAIL, email)
            ?.putString(KEY_PREFERRED_LANGUAGE, preferredLanguage)
            ?.putInt(KEY_ANIMAL_COUNT, animalCount)
            ?.putString(KEY_VILLAGE, village)
            ?.putString(KEY_PINCODE, pincode)
            ?.putBoolean(KEY_PROFILE_COMPLETE, true)
            ?.apply()
    }

    fun logout() {
        clearInMemory()
        prefs?.edit()?.clear()?.apply()
    }

    private fun clearInMemory() {
        sessionToken = null
        role = null
        uid = null
        phone = null
        name = null
        email = null
        preferredLanguage = null
        animalCount = 0
        village = null
        pincode = null
        profileComplete = false
    }
}
