package com.example.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    val key: String,
    val lastSyncTimestamp: Long = 0L,
    val updatedAt: Long = System.currentTimeMillis()
)
