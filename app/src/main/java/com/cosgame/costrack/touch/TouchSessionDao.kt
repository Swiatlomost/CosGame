package com.cosgame.costrack.touch

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TouchSessionDao {

    @Insert
    suspend fun insert(session: TouchSession): Long

    @Update
    suspend fun update(session: TouchSession)

    @Delete
    suspend fun delete(session: TouchSession)

    @Query("SELECT * FROM touch_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<TouchSession>>

    @Query("SELECT * FROM touch_sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): TouchSession?

    @Query("SELECT * FROM touch_sessions WHERE label IS NOT NULL ORDER BY startTime DESC")
    fun getLabeledSessions(): Flow<List<TouchSession>>

    @Query("SELECT * FROM touch_sessions WHERE label = :label ORDER BY startTime DESC")
    fun getSessionsByLabel(label: String): Flow<List<TouchSession>>

    @Query("SELECT * FROM touch_sessions WHERE missionType = :missionType ORDER BY startTime DESC")
    fun getSessionsByMission(missionType: String): Flow<List<TouchSession>>

    @Query("SELECT COUNT(*) FROM touch_sessions")
    suspend fun getSessionCount(): Int

    @Query("SELECT COUNT(*) FROM touch_sessions WHERE label IS NOT NULL")
    suspend fun getLabeledSessionCount(): Int

    @Query("SELECT DISTINCT label FROM touch_sessions WHERE label IS NOT NULL")
    suspend fun getAllLabels(): List<String>

    @Query("SELECT COUNT(*) FROM touch_sessions WHERE label = :label")
    suspend fun getCountByLabel(label: String): Int

    @Query("DELETE FROM touch_sessions")
    suspend fun deleteAll()

    @Query("DELETE FROM touch_sessions WHERE label IS NULL")
    suspend fun deleteUnlabeled()

    @Query("SELECT SUM(LENGTH(touchEventsJson) * 2) FROM touch_sessions")
    suspend fun getTotalDataSize(): Long?
}
