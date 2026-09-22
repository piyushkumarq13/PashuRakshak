package com.pashurakshak.app.di

import android.content.Context
import com.pashurakshak.app.data.AlertRepository
import com.pashurakshak.app.data.AnimalRepository
import com.pashurakshak.app.data.AuthRepository
import com.pashurakshak.app.data.ReportRepository
import com.pashurakshak.app.data.VaccinationRepository
import com.pashurakshak.app.data.VetRepository
import com.pashurakshak.app.data.local.TursoClient
import com.pashurakshak.app.data.remote.B2UploadService
import com.pashurakshak.app.data.remote.ReportPushApi

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
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }
    val vaccinationRepository: VaccinationRepository by lazy { VaccinationRepository(database) }
    val vetRepository: VetRepository by lazy { VetRepository(database) }
    val alertRepository: AlertRepository by lazy { AlertRepository(database) }
    val b2UploadService: B2UploadService by lazy { B2UploadService(reportRepository) }
    val reportPushApi: ReportPushApi by lazy { ReportPushApi() }
    val authRepository: AuthRepository by lazy { AuthRepository(database) }
}
