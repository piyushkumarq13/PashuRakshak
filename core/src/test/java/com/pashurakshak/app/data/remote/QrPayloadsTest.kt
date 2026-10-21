package com.pashurakshak.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrPayloadsTest {

    @Test
    fun signedPayloadVerifiesAndExtractsId() {
        val id = "animal-123"
        val payload = QrPayloads.sign(id)

        assertEquals(id, QrPayloads.verifyAndExtractId(payload))
    }

    @Test
    fun tamperedPayloadIsRejected() {
        val payload = QrPayloads.sign("animal-123")
        val parts = payload.split('|')
        val tampered = "${parts[0]}|${parts[1]}|${"0".repeat(64)}"

        assertNull(QrPayloads.verifyAndExtractId(tampered))
    }

    @Test
    fun malformedPayloadIsRejected() {
        assertNull(QrPayloads.verifyAndExtractId("animal-123"))
        assertNull(QrPayloads.verifyAndExtractId("animal-123|123|"))
        assertNull(QrPayloads.verifyAndExtractId("a|b|c|d"))
    }
}
