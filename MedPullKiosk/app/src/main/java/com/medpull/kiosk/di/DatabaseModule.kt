package com.medpull.kiosk.di

import android.content.Context
import androidx.room.Room
import com.medpull.kiosk.data.local.AppDatabase
import com.medpull.kiosk.data.local.dao.AuditLogDao
import com.medpull.kiosk.data.local.dao.BranchingRuleDao
import com.medpull.kiosk.data.local.dao.FormDao
import com.medpull.kiosk.data.local.dao.PatientCacheDao
import com.medpull.kiosk.data.local.dao.FormFieldDao
import com.medpull.kiosk.data.local.dao.FormIntakeFlowDao
import com.medpull.kiosk.data.local.dao.SyncQueueDao
import com.medpull.kiosk.data.local.dao.UserDao
import com.medpull.kiosk.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for Room database
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            Constants.Database.NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideFormDao(database: AppDatabase): FormDao {
        return database.formDao()
    }

    @Provides
    @Singleton
    fun provideFormFieldDao(database: AppDatabase): FormFieldDao {
        return database.formFieldDao()
    }

    @Provides
    @Singleton
    fun provideAuditLogDao(database: AppDatabase): AuditLogDao {
        return database.auditLogDao()
    }

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }

    @Provides
    @Singleton
    fun provideFormIntakeFlowDao(database: AppDatabase): FormIntakeFlowDao {
        return database.formIntakeFlowDao()
    }

    @Provides
    @Singleton
    fun provideBranchingRuleDao(database: AppDatabase): BranchingRuleDao {
        return database.branchingRuleDao()
    }

    @Provides
    @Singleton
    fun providePatientCacheDao(database: AppDatabase): PatientCacheDao {
        return database.patientCacheDao()
    }
}
