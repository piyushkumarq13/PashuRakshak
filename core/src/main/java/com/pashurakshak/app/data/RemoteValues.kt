package com.pashurakshak.app.data

/**
 * Android's JSONObject.optString() turns a JSON null into the literal string
 * "null" instead of an empty value. Persisting that string poisons ids such as
 * assigned_vet_id, qr_code_id and photo_remote_url, so every remote/db optional
 * string goes through this cleanup before it reaches the local database or a
 * push payload.
 */
internal fun cleanRemoteString(value: String?): String? {
    val text = value?.trim() ?: return null
    return text.takeIf {
        it.isNotEmpty() &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("undefined", ignoreCase = true)
    }
}
