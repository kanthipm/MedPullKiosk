package com.medpull.kiosk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.medpull.kiosk.data.local.entities.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getBySessionId(sessionId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE sessionId = :sessionId AND documentType = :documentType LIMIT 1")
    suspend fun getBySessionAndType(sessionId: String, documentType: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM documents WHERE sessionId = :sessionId AND documentType = :documentType")
    suspend fun deleteBySessionAndType(sessionId: String, documentType: String)

    @Query("SELECT * FROM documents WHERE sessionId = :sessionId")
    suspend fun getBySessionIdOnce(sessionId: String): List<DocumentEntity>
}
