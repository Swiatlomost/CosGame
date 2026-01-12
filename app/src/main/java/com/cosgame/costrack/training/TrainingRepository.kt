package com.cosgame.costrack.training

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Repository for training data operations.
 */
class TrainingRepository private constructor(context: Context) {

    private val database = TrainingDatabase.getInstance(context)
    private val dao = database.trainingDao()

    // ===== Samples =====

    suspend fun insertSample(sample: TrainingSample): Long = dao.insertSample(sample)

    suspend fun insertSamples(samples: List<TrainingSample>) = dao.insertSamples(samples)

    suspend fun getSamplesByActivity(activityType: ActivityType): List<TrainingSample> =
        dao.getSamplesByActivity(activityType)

    suspend fun getAllSamples(): List<TrainingSample> = dao.getAllSamples()

    suspend fun getAllSamplesWithCategory(): List<TrainingSample> = dao.getAllSamplesWithCategory()

    suspend fun getCountByActivity(activityType: ActivityType): Int =
        dao.getCountByActivity(activityType)

    fun getCountByActivityFlow(activityType: ActivityType): Flow<Int> =
        dao.getCountByActivityFlow(activityType)

    suspend fun getTotalSamplesCount(): Int = dao.getTotalSamplesCount()

    fun getTotalSamplesCountFlow(): Flow<Int> = dao.getTotalSamplesCountFlow()

    suspend fun deleteAllSamples() = dao.deleteAllSamples()

    suspend fun deleteSamplesByActivity(activityType: ActivityType) =
        dao.deleteSamplesByActivity(activityType)

    suspend fun deleteSamplesBySession(sessionId: Long) =
        dao.deleteSamplesBySession(sessionId)

    // ===== Sessions =====

    suspend fun insertSession(session: TrainingSession): Long = dao.insertSession(session)

    suspend fun updateSession(session: TrainingSession) = dao.updateSession(session)

    suspend fun getSessionsByActivity(activityType: ActivityType): List<TrainingSession> =
        dao.getSessionsByActivity(activityType)

    suspend fun getAllSessions(): List<TrainingSession> = dao.getAllSessions()

    suspend fun getCompletedSessions(): List<TrainingSession> = dao.getCompletedSessions()

    suspend fun getCompletedCountByActivity(activityType: ActivityType): Int =
        dao.getCompletedCountByActivity(activityType)

    fun getCompletedCountByActivityFlow(activityType: ActivityType): Flow<Int> =
        dao.getCompletedCountByActivityFlow(activityType)

    suspend fun deleteAllSessions() = dao.deleteAllSessions()

    suspend fun deleteSession(sessionId: Long) = dao.deleteSession(sessionId)

    suspend fun getSamplesBySession(sessionId: Long): List<TrainingSample> =
        dao.getSamplesBySession(sessionId)

    // ===== Statistics =====

    suspend fun getSampleCountsPerActivity(): List<ActivitySampleCount> =
        dao.getSampleCountsPerActivity()

    fun getSampleCountsPerActivityFlow(): Flow<List<ActivitySampleCount>> =
        dao.getSampleCountsPerActivityFlow()

    /**
     * Get a map of activity type to sample count.
     */
    suspend fun getSampleCountsMap(): Map<ActivityType, Int> {
        return dao.getSampleCountsPerActivity().associate { it.activityType to it.count }
    }

    /**
     * Check if we have minimum samples for all activities.
     */
    suspend fun hasMinimumSamplesForTraining(minPerClass: Int = 100): Boolean {
        val counts = getSampleCountsMap()
        return ActivityType.values().all { (counts[it] ?: 0) >= minPerClass }
    }

    /**
     * Clear all training data (samples and sessions).
     */
    suspend fun clearAllData() {
        dao.deleteAllSamples()
        dao.deleteAllSessions()
    }

    // ===== New samples tracking =====

    suspend fun getNewSamplesCount(sinceTimestamp: Long): Int =
        dao.getNewSamplesCount(sinceTimestamp)

    fun getNewSamplesCountFlow(sinceTimestamp: Long): Flow<Int> =
        dao.getNewSamplesCountFlow(sinceTimestamp)

    suspend fun getNewSampleCountsPerActivity(sinceTimestamp: Long): List<ActivitySampleCount> =
        dao.getNewSampleCountsPerActivity(sinceTimestamp)

    fun getNewSampleCountsPerActivityFlow(sinceTimestamp: Long): Flow<List<ActivitySampleCount>> =
        dao.getNewSampleCountsPerActivityFlow(sinceTimestamp)

    companion object {
        @Volatile
        private var INSTANCE: TrainingRepository? = null

        fun getInstance(context: Context): TrainingRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TrainingRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
