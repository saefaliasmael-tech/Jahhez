package com.example.data.sync

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductSyncDao {

    @Query("SELECT * FROM product_sync_queue WHERE storeId = :storeId AND status IN ('PENDING', 'FAILED') ORDER BY updatedAt ASC")
    suspend fun getPendingItems(storeId: String): List<ProductSyncEntity>

    @Query("SELECT COUNT(*) FROM product_sync_queue WHERE storeId = :storeId AND status IN ('PENDING', 'FAILED')")
    fun getPendingCountFlow(storeId: String): Flow<Int>

    @Query("SELECT * FROM product_sync_queue WHERE productId = :productId")
    suspend fun getByProductId(productId: Long): ProductSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: ProductSyncEntity)

    @Query("UPDATE product_sync_queue SET status = :status, lastAttemptAt = :timestamp, errorMessage = :error, retryCount = retryCount + :incrementRetry WHERE productId = :productId")
    suspend fun updateStatus(
        productId: Long,
        status: String,
        timestamp: Long,
        error: String?,
        incrementRetry: Int
    )

    @Query("DELETE FROM product_sync_queue WHERE productId = :productId")
    suspend fun delete(productId: Long)

    @Query("DELETE FROM product_sync_queue WHERE storeId = :storeId AND status = 'SYNCED'")
    suspend fun clearSynced(storeId: String)

    @Query("SELECT productId FROM product_sync_queue WHERE status = 'SYNCED'")
    suspend fun getSyncedProductIds(): List<Long>

    @Query("UPDATE product_sync_queue SET storeId = :storeId, status = 'PENDING', retryCount = 0, errorMessage = null WHERE status IN ('FAILED', 'SYNCING')")
    suspend fun resetFailedAndSyncingItems(storeId: String)
}
