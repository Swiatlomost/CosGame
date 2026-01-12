package com.cosgame.costrack.training

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.cosgame.costrack.activitylog.ActivityLog
import com.cosgame.costrack.activitylog.ActivityLogDao
import com.cosgame.costrack.activitylog.ActivitySession
import com.cosgame.costrack.touch.TouchSession
import com.cosgame.costrack.touch.TouchSessionDao
import com.cosgame.costrack.learn.Category
import com.cosgame.costrack.learn.CategoryDao

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
 * Room database for training data and activity logs.
 */
@Database(
    entities = [
        TrainingSample::class,
        TrainingSession::class,
        ActivityLog::class,
        ActivitySession::class,
        TouchSession::class,
        Category::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class TrainingDatabase : RoomDatabase() {

    abstract fun trainingDao(): TrainingDao

    abstract fun activityLogDao(): ActivityLogDao

    abstract fun touchSessionDao(): TouchSessionDao

    abstract fun categoryDao(): CategoryDao

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
