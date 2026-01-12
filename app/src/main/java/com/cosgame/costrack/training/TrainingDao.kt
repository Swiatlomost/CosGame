package com.cosgame.costrack.training

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for training data.
 */
@Dao
interface TrainingDao {

    // ===== Samples =====

    @Insert
    suspend fun insertSample(sample: TrainingSample): Long

    @Insert
    suspend fun insertSamples(samples: List<TrainingSample>)

    @Query("SELECT * FROM training_samples WHERE activityType = :activityType ORDER BY timestamp")
    suspend fun getSamplesByActivity(activityType: ActivityType): List<TrainingSample>

    @Query("SELECT * FROM training_samples ORDER BY timestamp")
    suspend fun getAllSamples(): List<TrainingSample>

    @Query("SELECT * FROM training_samples WHERE category != '' ORDER BY timestamp")
    suspend fun getAllSamplesWithCategory(): List<TrainingSample>

    @Query("SELECT COUNT(*) FROM training_samples WHERE activityType = :activityType")
    suspend fun getCountByActivity(activityType: ActivityType): Int

    @Query("SELECT COUNT(*) FROM training_samples WHERE activityType = :activityType")
    fun getCountByActivityFlow(activityType: ActivityType): Flow<Int>

    @Query("SELECT COUNT(*) FROM training_samples")
    suspend fun getTotalSamplesCount(): Int

    @Query("SELECT COUNT(*) FROM training_samples")
    fun getTotalSamplesCountFlow(): Flow<Int>

    @Query("DELETE FROM training_samples")
    suspend fun deleteAllSamples()

    @Query("DELETE FROM training_samples WHERE activityType = :activityType")
    suspend fun deleteSamplesByActivity(activityType: ActivityType)

    @Query("DELETE FROM training_samples WHERE sessionId = :sessionId")
    suspend fun deleteSamplesBySession(sessionId: Long)

    // ===== Sessions =====

    @Insert
    suspend fun insertSession(session: TrainingSession): Long

    @Update
    suspend fun updateSession(session: TrainingSession)

    @Query("SELECT * FROM training_sessions WHERE activityType = :activityType ORDER BY startTime DESC")
    suspend fun getSessionsByActivity(activityType: ActivityType): List<TrainingSession>

    @Query("SELECT * FROM training_sessions ORDER BY startTime DESC")
    suspend fun getAllSessions(): List<TrainingSession>

    @Query("SELECT * FROM training_sessions WHERE completed = 1 ORDER BY startTime DESC")
    suspend fun getCompletedSessions(): List<TrainingSession>

    @Query("SELECT COUNT(*) FROM training_sessions WHERE activityType = :activityType AND completed = 1")
    suspend fun getCompletedCountByActivity(activityType: ActivityType): Int

    @Query("SELECT COUNT(*) FROM training_sessions WHERE activityType = :activityType AND completed = 1")
    fun getCompletedCountByActivityFlow(activityType: ActivityType): Flow<Int>

    @Query("DELETE FROM training_sessions")
    suspend fun deleteAllSessions()

    @Query("DELETE FROM training_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM training_samples WHERE sessionId = :sessionId ORDER BY timestamp")
    suspend fun getSamplesBySession(sessionId: Long): List<TrainingSample>

    // ===== Statistics =====

    @Query("""
        SELECT activityType, COUNT(*) as count
        FROM training_samples
        GROUP BY activityType
    """)
    suspend fun getSampleCountsPerActivity(): List<ActivitySampleCount>

    @Query("""
        SELECT activityType, COUNT(*) as count
        FROM training_samples
        GROUP BY activityType
    """)
    fun getSampleCountsPerActivityFlow(): Flow<List<ActivitySampleCount>>

    // ===== New samples tracking =====

    @Query("SELECT COUNT(*) FROM training_samples WHERE timestamp > :sinceTimestamp")
    suspend fun getNewSamplesCount(sinceTimestamp: Long): Int

    @Query("SELECT COUNT(*) FROM training_samples WHERE timestamp > :sinceTimestamp")
    fun getNewSamplesCountFlow(sinceTimestamp: Long): Flow<Int>

    @Query("""
        SELECT activityType, COUNT(*) as count
        FROM training_samples
        WHERE timestamp > :sinceTimestamp
        GROUP BY activityType
    """)
    suspend fun getNewSampleCountsPerActivity(sinceTimestamp: Long): List<ActivitySampleCount>

    @Query("""
        SELECT activityType, COUNT(*) as count
        FROM training_samples
        WHERE timestamp > :sinceTimestamp
        GROUP BY activityType
    """)
    fun getNewSampleCountsPerActivityFlow(sinceTimestamp: Long): Flow<List<ActivitySampleCount>>
}

/**
 * Helper class for sample count queries.
 */
data class ActivitySampleCount(
    val activityType: ActivityType,
    val count: Int
)
