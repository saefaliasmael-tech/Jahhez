package com.example.data.sync

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "product_sync_queue")
data class ProductSyncEntity(
    @PrimaryKey
    val productId: Long,
    val storeId: String,
    val operation: String, // "UPSERT", "DELETE"
    val status: String,    // "PENDING", "SYNCING", "SYNCED", "FAILED"
    val retryCount: Int = 0,
    val lastAttemptAt: Long = 0L,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
