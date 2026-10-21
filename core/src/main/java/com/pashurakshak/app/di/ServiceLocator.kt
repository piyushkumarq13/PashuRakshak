package com.pashurakshak.app.di

import android.content.Context
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.AuthRepository
import com.pashurakshak.app.data.FarmerRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.VetRepository
import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.remote.AiRiskApi
import com.pashurakshak.app.data.remote.B2UploadService
import com.pashurakshak.app.data.remote.ReportPushApi
import com.pashurakshak.app.data.sync.SyncScheduler
import com.pashurakshak.app.data.sync.SyncStatusHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ServiceLocator {

    private lateinit var database: TursoClient
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        appContext = context.applicationContext
        if (::database.isInitialized) return
        database = TursoClient(context.applicationContext)
    }

    /** Application context for WorkManager triggers / background sync from ViewModels. */
    val context: Context
        get() = appContext

    val animalRepository: AnimalRepository by lazy { AnimalRepository(database) }
    val farmerRepository: FarmerRepository by lazy { FarmerRepository() }
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }
    val vaccinationRepository: VaccinationRepository by lazy { VaccinationRepository(database) }
    val vetRepository: VetRepository by lazy { VetRepository(database) }
    val alertRepository: AlertRepository by lazy { AlertRepository(database) }
    val b2UploadService: B2UploadService by lazy { B2UploadService(reportRepository) }
    val reportPushApi: ReportPushApi by lazy { ReportPushApi() }
    val aiRiskApi: AiRiskApi by lazy { AiRiskApi() }
    val authRepository: AuthRepository by lazy { AuthRepository(database) }

    /**
     * Removes all per-user local state after sign-out so a different account can
     * never read the previous user's animals, reports, photos or cached tokens.
     */
    suspend fun clearLocalData() = withContext(Dispatchers.IO) {
        runCatching { SyncScheduler.cancel(appContext) }
        runCatching { database.closeAndDelete() }
        runCatching { b2UploadService.clearCachedAuth() }
        runCatching { File(appContext.filesDir, "photos").deleteRecursively() }
        runCatching { File(appContext.cacheDir, "image_cache").deleteRecursively() }
        SyncStatusHolder.reset()
    }
}
