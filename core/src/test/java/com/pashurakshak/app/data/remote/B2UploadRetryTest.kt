package com.pashurakshak.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

/**
 * Pure JVM unit tests for B2UploadService retry/backoff helpers.
 * Full end-to-end upload is verified manually via the B2 Upload Test screen
 * (Farmer Home → debug button) once real credentials are in local.properties.
 */
class B2UploadRetryTest {

    @Test
    fun maxAttemptsIsThree() {
        assertEquals(3, B2UploadService.MAX_ATTEMPTS)
    }

    @Test
    fun backoffDoublesEachAttempt() {
        assertEquals(1_000L, B2UploadService.backoffDelayMs(1))
        assertEquals(2_000L, B2UploadService.backoffDelayMs(2))
        assertEquals(4_000L, B2UploadService.backoffDelayMs(3))
    }

    @Test
    fun offlineExceptionsAreDetected() {
        assertTrue(B2UploadService.isNoNetwork(UnknownHostException("api.backblazeb2.com")))
        assertTrue(B2UploadService.isNoNetwork(ConnectException("Connection refused")))
        assertTrue(B2UploadService.isNoNetwork(IOException("Failed to connect to host")))
        assertTrue(B2UploadService.isNoNetwork(IOException("Network is unreachable")))
    }

    @Test
    fun genericIoExceptionIsNotTreatedAsOffline() {
        assertFalse(B2UploadService.isNoNetwork(IOException("disk full")))
    }
}
