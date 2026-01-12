package com.cosgame.costrack.di

import android.app.Application
import android.content.Context
import com.cosgame.costrack.activitylog.ActivityLogRepository
import com.cosgame.costrack.state.AppStateManager
import com.cosgame.costrack.training.TrainingDatabase
import com.cosgame.costrack.training.TrainingDao
import com.cosgame.costrack.training.TrainingRepository
import com.cosgame.costrack.training.PersonalHarTrainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplication(@ApplicationContext context: Context): Application {
        return context.applicationContext as Application
    }

    @Provides
    @Singleton
    fun provideAppStateManager(
        @ApplicationContext context: Context
    ): AppStateManager {
        return AppStateManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideActivityLogRepository(
        @ApplicationContext context: Context
    ): ActivityLogRepository {
        return ActivityLogRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideTrainingDatabase(
        @ApplicationContext context: Context
    ): TrainingDatabase {
        return TrainingDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTrainingDao(database: TrainingDatabase): TrainingDao {
        return database.trainingDao()
    }

    @Provides
    @Singleton
    fun provideTrainingRepository(
        @ApplicationContext context: Context
    ): TrainingRepository {
        return TrainingRepository.getInstance(context)
    }

    @Provides
    @Singleton
    fun providePersonalHarTrainer(
        @ApplicationContext context: Context
    ): PersonalHarTrainer {
        return PersonalHarTrainer(context)
    }
}
