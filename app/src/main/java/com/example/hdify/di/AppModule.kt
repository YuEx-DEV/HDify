package com.example.hdify.di

import android.content.Context
import androidx.room.Room
import com.example.hdify.data.database.AppDatabase
import com.example.hdify.data.database.JobDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "hdify.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideJobDao(database: AppDatabase): JobDao {
        return database.jobDao()
    }
}
