package com.pashurakshak.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteValuesTest {

    @Test
    fun nullAndBlankBecomeNull() {
        assertNull(cleanRemoteString(null))
        assertNull(cleanRemoteString(""))
        assertNull(cleanRemoteString("   "))
    }

    @Test
    fun androidLiteralNullsBecomeNull() {
        assertNull(cleanRemoteString("null"))
        assertNull(cleanRemoteString("NULL"))
        assertNull(cleanRemoteString("undefined"))
    }

    @Test
    fun realValuesAreTrimmedAndPreserved() {
        assertEquals("Khairatiya", cleanRemoteString(" Khairatiya "))
        assertEquals("QR-1234", cleanRemoteString("QR-1234"))
    }
}
