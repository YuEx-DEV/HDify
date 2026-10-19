package com.example.hdify.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntity): Long

    @Update
    suspend fun updateJob(job: JobEntity)

    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun getAllJobsFlow(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('PENDING', 'PROCESSING') ORDER BY createdAt ASC")
    fun getActiveQueueFlow(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED') ORDER BY completedAt DESC")
    fun getHistoryFlow(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :jobId")
    suspend fun getJobById(jobId: Long): JobEntity?

    @Query("SELECT * FROM jobs WHERE status = 'PROCESSING' ORDER BY createdAt DESC")
    suspend fun getInterruptedJobs(): List<JobEntity>

    @Query("DELETE FROM jobs WHERE id = :jobId")
    suspend fun deleteJobById(jobId: Long)

    @Query("DELETE FROM jobs WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearHistoryOnly()

    @Query("DELETE FROM jobs")
    suspend fun clearAll()
}
