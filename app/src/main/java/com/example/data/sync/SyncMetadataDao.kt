package com.example.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE `key` = :key")
    suspend fun getMetadata(key: String): SyncMetadataEntity?

    @Query("SELECT lastSyncTimestamp FROM sync_metadata WHERE `key` = :key")
    suspend fun getLastSyncTimestamp(key: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMetadata(metadata: SyncMetadataEntity)
}
