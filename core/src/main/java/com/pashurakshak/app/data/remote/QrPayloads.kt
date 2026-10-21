package com.pashurakshak.app.data.remote

import com.pashurakshak.app.BuildConfig
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-signed QR payloads so a code's contents can't be guessed or forged:
 * the payload is "<id>|<timestampMillis>|<hmacHex>" and the signature is
 * HMAC-SHA256 of "<id>|<timestampMillis>" keyed by the app API key. The
 * backend verifies the same signature before trusting the embedded id.
 */
object QrPayloads {

    fun sign(id: String): String {
        val ts = System.currentTimeMillis()
        return "$id|$ts|${hmacHex("$id|$ts")}"
    }

    /** Returns the embedded id when the signature is valid, else null. */
    fun verifyAndExtractId(raw: String): String? {
        val parts = raw.split('|')
        if (parts.size != 3) return null
        val (id, ts, sig) = parts
        if (hmacHex("$id|$ts") != sig) return null
        return id
    }

    private fun hmacHex(data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(BuildConfig.APP_API_KEY.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
