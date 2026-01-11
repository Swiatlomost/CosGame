package com.cosgame.costrack.training

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

/**
 * Type converters for Room database.
 */
class Converters {
    @TypeConverter
    fun fromActivityType(value: ActivityType): String = value.name

    @TypeConverter
    fun toActivityType(value: String): ActivityType = ActivityType.fromName(value)
}

/**
 * Room database for training data.
 */
@Database(
    entities = [TrainingSample::class, TrainingSession::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrainingDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    companion object {
        private const val DATABASE_NAME = "training_database"

        @Volatile
        private var INSTANCE: TrainingDatabase? = null

        fun getInstance(context: Context): TrainingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TrainingDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
