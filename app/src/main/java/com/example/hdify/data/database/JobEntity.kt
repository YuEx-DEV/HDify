package com.example.hdify.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val sourceUri: String,
    val outputUri: String?,
    val scale: Int,
    val engineName: String,
    val status: String, // "PENDING", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"
    val progress: Int = 0,
    val checkpointFrame: Int = 0,
    val totalFrames: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)
