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
 * b2_authorize_account → b2_list_buckets(accountId) → b2_get_upload_url → b2_upload_file.
 *
 * Credentials come from BuildConfig (local.properties) — never hardcoded.
 * Retry-on-failure uses exponential backoff (max 3 attempts); offline skips silently
 * so the sync queue can pick the photo up later.
 *
 * The upload response contains `fileName` (not `fileUrl`), so the download URL is
 * constructed as downloadUrl + "/file/" + bucketName + "/" + fileName.
 * Bucket is private, so a cached download-authorization token is supplied when
 * displaying images (see getDownloadAuthToken / withAuth).
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

    /** Authorize result including downloadUrl and accountId needed for later steps. */
    private data class Auth(
        val apiUrl: String,
        val downloadUrl: String,
        val accountId: String,
        val token: String,
    )

    private data class UploadTarget(val url: String, val token: String)

    /** Cached download-authorization token for private-bucket image display. */
    private var cachedDownloadToken: String? = null
    private var cachedDownloadTokenExpiryMs: Long = 0

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
     * Walks the photo-upload queue (reports with a local photo and no remote URL,
     * regardless of synced flag so retried failures eventually succeed).
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
                val upload = getUploadUrl(auth)
                val fileUrl = postFile(upload, remoteName, contentType, bytes, auth)
                UploadResult.Success(fileUrl)
            }
        }

    /**
     * Returns a full public-style download URL for the file.
     * Because the bucket is private, callers should prefer [withAuth] when rendering images.
     */
    fun buildDownloadUrl(fileUrl: String): String = fileUrl

    /**
     * Returns a URL that can be used in an <img>/AsyncImage tag by appending a
     * download-authorization token as a query parameter for private buckets.
     * Caches the token for [TOKEN_CACHE_DURATION_MS].
     */
    suspend fun withAuth(fileUrl: String): String {
        if (fileUrl.isBlank()) return fileUrl
        val token = getDownloadAuthToken() ?: return fileUrl
        return if (fileUrl.contains("Authorization=")) fileUrl else "$fileUrl?Authorization=$token"
    }

    /** Cached download-authorization token (scoped to the `reports/` prefix). */
    private suspend fun getDownloadAuthToken(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        cachedDownloadToken?.let { if (now < cachedDownloadTokenExpiryMs) return@withContext it }
        try {
            val auth = authorize()
            val token = requestDownloadAuth(auth)
            cachedDownloadToken = token
            cachedDownloadTokenExpiryMs = now + TOKEN_CACHE_DURATION_MS
            token
        } catch (_: Exception) { null }
    }

    private suspend fun requestDownloadAuth(auth: Auth): String = withContext(Dispatchers.IO) {
        val connection = open("${auth.apiUrl}/b2api/v2/b2_get_download_authorization", "POST")
        connection.setRequestProperty("Authorization", auth.token)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        val body = JSONObject()
            .put("bucketId", resolveBucketId(auth))
            .put("fileNamePrefix", "reports/")
            .put("validDurationInSeconds", TOKEN_CACHE_DURATION_MS / 1000)
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val json = readJson(connection)
        json.getString("authorizationToken")
    }

    /** Performs b2_authorize_account and returns apiUrl, downloadUrl, accountId, token. */
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
        return Auth(
            apiUrl = json.getString("apiUrl"),
            downloadUrl = json.getString("downloadUrl"),
            accountId = json.getString("accountId"),
            token = json.getString("authorizationToken"),
        )
    }

    private fun getUploadUrl(auth: Auth): UploadTarget {
        val bucketId = resolveBucketId(auth)
        val connection = open("${auth.apiUrl}/b2api/v2/b2_get_upload_url", "POST")
        connection.setRequestProperty("Authorization", auth.token)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.outputStream.use { it.write(JSONObject().put("bucketId", bucketId).toString().toByteArray()) }
        val json = readJson(connection)
        return UploadTarget(json.getString("uploadUrl"), json.getString("authorizationToken"))
    }

    /** B2 list_buckets requires accountId as a query parameter. */
    private fun resolveBucketId(auth: Auth): String {
        val connection = open("${auth.apiUrl}/b2api/v2/b2_list_buckets?accountId=${auth.accountId}", "GET")
        connection.setRequestProperty("Authorization", auth.token)
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

    /** B2 b2_upload_file returns `fileName` (not `fileUrl`), so build the download URL manually. */
    private fun postFile(
        uploadTarget: UploadTarget,
        remoteName: String,
        contentType: String,
        bytes: ByteArray,
        auth: Auth,
    ): String {
        val connection = open(uploadTarget.url, "POST")
        connection.setRequestProperty("Authorization", uploadTarget.token)
        connection.setRequestProperty("Content-Type", contentType)
        connection.setRequestProperty("X-Bz-File-Name", urlEncodePath(remoteName))
        connection.setRequestProperty("X-Bz-Content-Sha1", sha1Hex(bytes))
        connection.doOutput = true
        connection.outputStream.use { it.write(bytes) }
        val json = readJson(connection)
        val fileName = json.getString("fileName")
        val pathSegments = fileName.split("/")
        val encodedPath = pathSegments.joinToString("/") { urlEncodePath(it) }
        return "${auth.downloadUrl}/file/$bucketName/$encodedPath"
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

    private class B2HttpException(val code: Int, message: String) : IOException(message)

        companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_BASE_DELAY_MS = 1_000L
        private const val TOKEN_CACHE_DURATION_MS = 12 * 60 * 60 * 1000L // 12 hours

        /** Percent-encode each path segment; letters/digits and -._~ are kept as-is. */
        @JvmStatic
        fun urlEncodePath(path: String): String =
            path.split("/")
                .joinToString("/") { segment ->
                    segment.map { char ->
                        if (char.isLetterOrDigit() || char in "-._~") char.toString()
                        else "%${"%02x".format(char.code)}"
                    }.joinToString("")
                }

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

        /** Build a display URL (with authorization token) for a private-bucket image. */
        @JvmStatic
        fun buildFileUrl(downloadUrl: String, bucketName: String, fileName: String): String =
            "$downloadUrl/file/$bucketName/$fileName"
    }
}
