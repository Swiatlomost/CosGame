package com.cosgame.costrack.training

/**
 * Activity types for personal HAR training.
 */
enum class ActivityType(
    val displayName: String,
    val icon: String,
    val description: String
) {
    WALKING("Walking", "🚶", "Normal walking pace"),
    RUNNING("Running", "🏃", "Jogging or running"),
    SITTING("Sitting", "🪑", "Sitting on a chair"),
    STANDING("Standing", "🧍", "Standing still"),
    LAYING("Laying", "🛏️", "Laying down");

    companion object {
        fun fromOrdinal(ordinal: Int): ActivityType {
            return values().getOrElse(ordinal) { STANDING }
        }

        fun fromName(name: String): ActivityType {
            return values().find { it.name.equals(name, ignoreCase = true) } ?: STANDING
        }
    }
}
