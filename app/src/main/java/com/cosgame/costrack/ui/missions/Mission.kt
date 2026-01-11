package com.cosgame.costrack.ui.missions

import com.cosgame.costrack.training.ActivityType

/**
 * Definition of a training mission.
 */
data class Mission(
    val id: String,
    val activityType: ActivityType,
    val durationSeconds: Int,
    val minSamplesRequired: Int = 100
) {
    val icon: String get() = activityType.icon
    val name: String get() = activityType.displayName
    val description: String get() = activityType.description
    val displayDuration: String get() = "${durationSeconds}s"
}

/**
 * Predefined training missions.
 */
object Missions {
    val WALK = Mission(
        id = "walk_60",
        activityType = ActivityType.WALKING,
        durationSeconds = 60
    )

    val RUN = Mission(
        id = "run_30",
        activityType = ActivityType.RUNNING,
        durationSeconds = 30
    )

    val SIT = Mission(
        id = "sit_60",
        activityType = ActivityType.SITTING,
        durationSeconds = 60
    )

    val STAND = Mission(
        id = "stand_60",
        activityType = ActivityType.STANDING,
        durationSeconds = 60
    )

    val LAY = Mission(
        id = "lay_30",
        activityType = ActivityType.LAYING,
        durationSeconds = 30
    )

    val ALL = listOf(WALK, RUN, SIT, STAND, LAY)

    fun getById(id: String): Mission? = ALL.find { it.id == id }
}
