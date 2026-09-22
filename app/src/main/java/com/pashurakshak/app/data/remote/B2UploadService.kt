package com.pashurakshak.app.data.remote

import com.pashurakshak.app.BuildConfig
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.local.SymptomReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Uploads symptom-report photos to Backblaze B2 via the native API:
 * b2_authorize_account → b2_get_upload_url → b2_upload_file.
 *
 * Credentials come from BuildConfig (local.properties) — never hardcoded.
 * Retry-on-failure uses exponential backoff (max 3 attempts); offline skips silently
 * so the Phase 7 sync queue can pick the photo up later.
 */
class B2UploadService(
    private val reportRepository: ReportRepository,
    private val applicationKeyId: String = BuildConfig.B2_APPLICATION_KEY_ID,
    private val applicationKey: String = BuildConfig.B2_APPLICATION_KEY,
    private val bucketName: String = BuildConfig.B2_BUCKET_NAME,
) {

    sealed class UploadResult {
        data class Success(val fileUrl: String) : UploadResult()
        data class Failure(val message: String) : UploadResult()
        /** No network (or missing local file) — left for the sync queue to retry later. */
        data object Skipped : UploadResult()
    }

    /** Uploads one report's photo and writes the returned URL to photo_remote_url. */
    suspend fun uploadReportPhoto(report: SymptomReport): UploadResult {
        val localPath = report.photoLocalPath
        if (localPath.isBlank()) return UploadResult.Skipped
        val file = File(localPath)
        if (!file.exists()) return UploadResult.Skipped
        if (applicationKeyId.isBlank() || applicationKey.isBlank() || bucketName.isBlank()) {
            return UploadResult.Failure(
                "B2 credentials missing — fill B2_* fields in local.properties",
            )
        }

        val remoteName = "reports/${report.id}/${file.name}"
        return when (val result = uploadFile(file, remoteName)) {
            is UploadResult.Success -> {
                reportRepository.update(report.copy(photoRemoteUrl = result.fileUrl))
                result
            }

            else -> result
        }
    }

    /**
     * Walks the photo-upload queue (unsynced reports with a local photo and no remote URL).
     * Returns per-report outcomes; failures are not fatal so one bad photo doesn't block others.
     */
    suspend fun uploadPendingReportPhotos(): List<Pair<String, UploadResult>> {
        val pending = reportRepository.getReportsPendingPhotoUpload()
        return pending.map { report ->
            report.id to uploadReportPhoto(report)
        }
    }

    /** Single-file upload with retry/backoff — public so the manual test screen can call it. */
    suspend fun uploadFile(file: File, remoteName: String): UploadResult =
        withContext(Dispatchers.IO) {
            executeWithRetry {
                val bytes = file.readBytes()
                val contentType = when {
                    remoteName.endsWith(".png", ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }
                val auth = authorize()
                val upload = getUploadUrl(auth.apiUrl, auth.token)
                val fileUrl = postFile(upload.url, upload.token, remoteName, contentType, bytes)
                UploadResult.Success(fileUrl)
            }
        }

    private data class Auth(val apiUrl: String, val token: String)
    private data class UploadTarget(val url: String, val token: String)

    private fun authorize(): Auth {
        val connection = open(
            "https://api.backblazeb2.com/b2api/v2/b2_authorize_account",
            "GET",
        )
        val credentials = android.util.Base64.encodeToString(
            "$applicationKeyId:$applicationKey".toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP,
        )
        connection.setRequestProperty("Authorization", "Basic $credentials")
        val json = readJson(connection)
        val allowed = json.optJSONObject("allowed")
        val buckets = allowed?.optJSONArray("buckets")
        if (buckets != null && bucketName.isNotBlank()) {
            for (i in 0 until buckets.length()) {
                val bucket = buckets.getJSONObject(i)
                if (bucket.optString("bucketName") == bucketName) {
                    // Bucket id is looked up fresh on each upload via b2_get_upload_url.
                    break
                }
            }
        }
        return Auth(json.getString("apiUrl"), json.getString("authorizationToken"))
    }

    private fun getUploadUrl(apiUrl: String, token: String): UploadTarget {
        val connection = open("$apiUrl/b2api/v2/b2_get_upload_url", "POST")
        connection.setRequestProperty("Authorization", token)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val bucketId = resolveBucketId(apiUrl, token)
        connection.outputStream.use { it.write(JSONObject().put("bucketId", bucketId).toString().toByteArray()) }
        val json = readJson(connection)
        return UploadTarget(json.getString("uploadUrl"), json.getString("authorizationToken"))
    }

    private fun resolveBucketId(apiUrl: String, token: String): String {
        val connection = open("$apiUrl/b2api/v2/b2_list_buckets", "GET")
        connection.setRequestProperty("Authorization", token)
        val json = readJson(connection)
        val buckets = json.getJSONArray("buckets")
        for (i in 0 until buckets.length()) {
            val bucket = buckets.getJSONObject(i)
            if (bucket.getString("bucketName") == bucketName) {
                return bucket.getString("bucketId")
            }
        }
        error("Bucket '$bucketName' not found for this application key")
    }

    private fun postFile(uploadUrl: String, token: String, remoteName: String, contentType: String, bytes: ByteArray): String {
        val connection = open(uploadUrl, "POST")
        connection.setRequestProperty("Authorization", token)
        connection.setRequestProperty("Content-Type", contentType)
        connection.setRequestProperty("X-Bz-File-Name", urlEncodePath(remoteName))
        connection.setRequestProperty("X-Bz-Content-Sha1", sha1Hex(bytes))
        connection.doOutput = true
        connection.outputStream.use { it.write(bytes) }
        val json = readJson(connection)
        return json.getString("fileUrl")
    }

    private fun open(rawUrl: String, method: String): HttpURLConnection {
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = false
        return connection
    }

    private fun readJson(connection: HttpURLConnection): JSONObject {
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw B2HttpException(code, body)
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Max 3 attempts with exponential backoff (1s, 2s). No-network failures skip silently
     * (Skipped) so the offline-first sync queue can retry later without surfacing errors.
     */
    private suspend fun executeWithRetry(block: suspend () -> UploadResult): UploadResult {
        var lastError: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            try {
                return block()
            } catch (error: B2HttpException) {
                return UploadResult.Failure("B2 HTTP ${error.code}: ${error.message}")
            } catch (error: IOException) {
                if (isNoNetwork(error)) {
                    return UploadResult.Skipped
                }
                lastError = error
            } catch (error: Exception) {
                lastError = error
            }
            if (attempt < MAX_ATTEMPTS) {
                delay(backoffDelayMs(attempt))
            }
        }
        return UploadResult.Failure(lastError?.message ?: "Upload failed")
    }

    private fun isNoNetwork(error: IOException): Boolean = isNoNetworkStatic(error)

    private fun sha1Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-1")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun urlEncodePath(path: String): String =
        path.split("/")
            .joinToString("/") { segment ->
                segment.map { char ->
                    if (char.isLetterOrDigit() || char in "-._~") char.toString()
                    else "%02x".format(char.code)
                }.joinToString("")
            }

    private class B2HttpException(val code: Int, message: String) : IOException(message)

    companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 1_000L

        /** Delay before retrying after the given 1-based attempt (1s, 2s, 4s...). */
        fun backoffDelayMs(attempt: Int): Long = RETRY_BASE_DELAY_MS shl (attempt - 1)

        /** True when the IOException means the device is offline (skip silently, no retry). */
        fun isNoNetwork(error: IOException): Boolean = isNoNetworkStatic(error)

        private fun isNoNetworkStatic(error: IOException): Boolean {
            val message = error.message.orEmpty()
            return error is java.net.UnknownHostException ||
                error is java.net.ConnectException ||
                error is java.net.SocketTimeoutException ||
                message.contains("Unable to resolve host", ignoreCase = true) ||
                message.contains("Network is unreachable", ignoreCase = true) ||
                message.contains("Failed to connect", ignoreCase = true) ||
                message.contains("Connection refused", ignoreCase = true)
        }
    }
}
