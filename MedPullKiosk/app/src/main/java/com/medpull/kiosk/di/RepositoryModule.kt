package com.medpull.kiosk.di

import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.google.gson.Gson
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.dao.FormDao
import com.medpull.kiosk.data.local.dao.FormFieldDao
import com.medpull.kiosk.data.local.dao.UserDao
import com.medpull.kiosk.data.remote.ai.ClaudeApiService
import com.medpull.kiosk.data.remote.ai.ClaudeVisionService
import com.medpull.kiosk.data.remote.ai.PdfPageRenderer
import com.medpull.kiosk.data.remote.aws.CognitoAuthServiceV2
import com.medpull.kiosk.data.remote.aws.S3Service
import com.medpull.kiosk.data.remote.aws.TextractService
import com.medpull.kiosk.data.remote.aws.TranslationService
import com.medpull.kiosk.data.repository.*
import com.medpull.kiosk.security.SecureStorageManager
import com.medpull.kiosk.sync.SyncManager
import com.medpull.kiosk.utils.NetworkMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        userDao: UserDao,
        secureStorageManager: SecureStorageManager,
        cognitoAuthService: CognitoAuthServiceV2,
        credentialsProvider: CognitoCachingCredentialsProvider
    ): AuthRepository {
        return AuthRepository(userDao, secureStorageManager, cognitoAuthService, credentialsProvider)
    }

    @Provides
    @Singleton
    fun provideFormRepository(
        formDao: FormDao,
        formFieldDao: FormFieldDao,
        s3Service: S3Service,
        textractService: TextractService,
        networkMonitor: NetworkMonitor,
        syncManager: SyncManager,
        authRepository: AuthRepository,
        claudeVisionService: ClaudeVisionService,
        pdfPageRenderer: PdfPageRenderer,
        translationService: TranslationService
    ): FormRepository {
        return FormRepository(formDao, formFieldDao, s3Service, textractService, networkMonitor, syncManager, authRepository, claudeVisionService, pdfPageRenderer, translationService)
    }

    @Provides
    @Singleton
    fun provideAuditRepository(
        auditLogDao: AuditLogDao,
        s3Service: S3Service,
        gson: Gson
    ): AuditRepository {
        return AuditRepository(auditLogDao, s3Service, gson)
    }

    @Provides
    @Singleton
    fun provideStorageRepository(
        s3Service: S3Service,
        networkMonitor: NetworkMonitor,
        syncManager: SyncManager
    ): StorageRepository {
        return StorageRepository(s3Service, networkMonitor, syncManager)
    }

    @Provides
    @Singleton
    fun provideTranslationRepository(
        translationService: TranslationService,
        formFieldDao: FormFieldDao
    ): TranslationRepository {
        return TranslationRepository(translationService, formFieldDao)
    }

    @Provides
    @Singleton
    fun provideAiRepository(
        claudeApiService: ClaudeApiService,
        auditLogDao: AuditLogDao,
        authRepository: AuthRepository
    ): AiRepository {
        return AiRepository(claudeApiService, auditLogDao, authRepository)
    }
}
