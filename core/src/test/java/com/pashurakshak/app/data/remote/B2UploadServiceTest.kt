package com.pashurakshak.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

class B2UploadServiceTest {

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

    @Test
    fun urlEncodePathKeepsSafeChars() {
        // letters, digits, underscore, hyphen, dot, tilde are preserved as-is
        val input = "reports/abc-123_xyz.file/report_2024.jpg"
        val result = B2UploadService.urlEncodePath(input)
        assertEquals(input, result)
    }

    @Test
    fun urlEncodePathEscapesSpecialChars() {
        // space (32) must become "%20"
        assertEquals("%20", B2UploadService.urlEncodePath(" "))
        // at-sign (64) must become "%40"
        assertEquals("%40", B2UploadService.urlEncodePath("@"))
    }

    @Test
    fun buildFileUrlConstructsFullDownloadPath() {
        val url = "https://f005.backblazeb2.com"
        val bucket = "pashurakshak-reports"
        val file = "reports/abc/report_2024.jpg"
        val expected = "https://f005.backblazeb2.com/file/pashurakshak-reports/reports/abc/report_2024.jpg"
        assertEquals(expected, B2UploadService.buildFileUrl(url, bucket, file))
    }
}
