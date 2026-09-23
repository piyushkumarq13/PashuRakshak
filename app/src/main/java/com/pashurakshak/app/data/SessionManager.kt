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
    private const val KEY_NAME = "name"
    private const val KEY_EMAIL = "email"
    private const val KEY_PREFERRED_LANGUAGE = "preferredLanguage"
    private const val KEY_ANIMAL_COUNT = "animalCount"
    private const val KEY_VILLAGE = "village"
    private const val KEY_PINCODE = "pincode"

    private var prefs: SharedPreferences? = null

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

    val isLoggedIn: Boolean
        get() = role != null && uid != null

    val farmerId: String
        get() = uid ?: "anonymous-farmer"

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
        name = stored.getString(KEY_NAME, null)
        email = stored.getString(KEY_EMAIL, null)
        preferredLanguage = stored.getString(KEY_PREFERRED_LANGUAGE, null)
        animalCount = stored.getInt(KEY_ANIMAL_COUNT, 0)
        village = stored.getString(KEY_VILLAGE, null)
        pincode = stored.getString(KEY_PINCODE, null)
    }

    fun onOtpVerified(uid: String, phone: String) {
        this.uid = uid
        this.phone = phone
        prefs?.edit()
            ?.putString(KEY_UID, uid)
            ?.putString(KEY_PHONE, phone)
            ?.apply()
    }

    fun completeLogin(role: Role) {
        this.role = role
        prefs?.edit()
            ?.putString(KEY_ROLE, role.name)
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
        prefs?.edit()
            ?.putString(KEY_NAME, name)
            ?.putString(KEY_EMAIL, email)
            ?.putString(KEY_PREFERRED_LANGUAGE, preferredLanguage)
            ?.putInt(KEY_ANIMAL_COUNT, animalCount)
            ?.putString(KEY_VILLAGE, village)
            ?.putString(KEY_PINCODE, pincode)
            ?.apply()
    }

    fun logout() {
        role = null
        uid = null
        phone = null
        name = null
        email = null
        preferredLanguage = null
        animalCount = 0
        village = null
        pincode = null
        prefs?.edit()
            ?.remove(KEY_ROLE)
            ?.remove(KEY_UID)
            ?.remove(KEY_PHONE)
            ?.remove(KEY_NAME)
            ?.remove(KEY_EMAIL)
            ?.remove(KEY_PREFERRED_LANGUAGE)
            ?.remove(KEY_ANIMAL_COUNT)
            ?.remove(KEY_VILLAGE)
            ?.remove(KEY_PINCODE)
            ?.apply()
    }
}
