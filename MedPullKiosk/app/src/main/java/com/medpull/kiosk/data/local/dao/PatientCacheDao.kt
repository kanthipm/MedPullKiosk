package com.medpull.kiosk.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.medpull.kiosk.data.local.entities.PatientCacheEntity

@Dao
interface PatientCacheDao {
    @Upsert
    suspend fun upsert(cache: PatientCacheEntity)

    @Query("SELECT * FROM patient_cache WHERE userId = :userId LIMIT 1")
    suspend fun getByUserId(userId: String): PatientCacheEntity?
}
