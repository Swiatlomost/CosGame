package com.cosgame.costrack.touch

import kotlinx.coroutines.flow.Flow

/**
 * Repository for touch session data.
 */
class TouchRepository(private val dao: TouchSessionDao) {

    val allSessions: Flow<List<TouchSession>> = dao.getAllSessions()

    val labeledSessions: Flow<List<TouchSession>> = dao.getLabeledSessions()

    suspend fun insert(session: TouchSession): Long = dao.insert(session)

    suspend fun update(session: TouchSession) = dao.update(session)

    suspend fun delete(session: TouchSession) = dao.delete(session)

    suspend fun getSessionById(id: Long): TouchSession? = dao.getSessionById(id)

    fun getSessionsByLabel(label: String): Flow<List<TouchSession>> =
        dao.getSessionsByLabel(label)

    fun getSessionsByMission(missionType: String): Flow<List<TouchSession>> =
        dao.getSessionsByMission(missionType)

    suspend fun getSessionCount(): Int = dao.getSessionCount()

    suspend fun getLabeledSessionCount(): Int = dao.getLabeledSessionCount()

    suspend fun getAllLabels(): List<String> = dao.getAllLabels()

    suspend fun getCountByLabel(label: String): Int = dao.getCountByLabel(label)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun deleteUnlabeled() = dao.deleteUnlabeled()

    suspend fun getTotalDataSize(): Long = dao.getTotalDataSize() ?: 0L

    /**
     * Get data size formatted.
     */
    suspend fun getTotalDataSizeFormatted(): String {
        val bytes = getTotalDataSize()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Get labeled sessions as list for training.
     */
    suspend fun getLabeledSessionsForTraining(): List<TouchSession> {
        var sessions = emptyList<TouchSession>()
        labeledSessions.collect { sessions = it }
        return sessions
    }
}
