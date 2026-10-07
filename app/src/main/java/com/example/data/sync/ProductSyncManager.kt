package com.example.data.sync

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.ProductDao
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import com.example.data.auth.AuthRepository
import com.example.data.auth.FirebaseAuthRepository
import com.example.data.remote.ProductRemoteModel
import com.example.data.remote.ProductRemoteRepository
import com.example.data.remote.ProductUnitRemoteModel
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import com.example.util.DiagnosticsLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class ProductSyncManager(
    private val productDao: ProductDao,
    private val productSyncDao: ProductSyncDao,
    private val syncMetadataDao: SyncMetadataDao,
    private val storeConfigRepository: StoreConfigRepository,
    private val authRepository: AuthRepository,
    private val productRemoteRepository: ProductRemoteRepository
) {

    constructor(context: Context) : this(
        productDao = AppDatabase.getDatabase(context).productDao(),
        productSyncDao = AppDatabase.getDatabase(context).productSyncDao(),
        syncMetadataDao = AppDatabase.getDatabase(context).syncMetadataDao(),
        storeConfigRepository = StoreConfigRepository(context),
        authRepository = FirebaseAuthRepository(context),
        productRemoteRepository = ProductRemoteRepository(context)
    )

    private val _syncStatus = MutableStateFlow(SyncStatus.IDLE)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pendingCount: Flow<Int> = storeConfigRepository.storeConfig.flatMapLatest { config ->
        val effectiveStoreId = config.storeId.ifBlank { "store_default" }
        productSyncDao.getPendingCountFlow(effectiveStoreId)
    }

    suspend fun enqueueAllLocalProductsIfMissing(storeId: String) {
        if (storeId.isBlank()) return
        DiagnosticsLogger.i("JahezSync", "enqueueAllLocalProductsIfMissing called for storeId=$storeId")

        // Reset any previously failed or interrupted items so they get a fresh retry opportunity
        productSyncDao.resetFailedAndSyncingItems(storeId)

        // Discover local active products
        val activeIds = productDao.getActiveProductIds()
        val syncedIds = productSyncDao.getSyncedProductIds().toSet()
        val pendingItems = productSyncDao.getPendingItems(storeId).associateBy { it.productId }

        var enqueuedCount = 0
        for (id in activeIds) {
            if (!syncedIds.contains(id) && !pendingItems.containsKey(id)) {
                enqueueProductUpsert(id, storeId)
                enqueuedCount++
            }
        }
        DiagnosticsLogger.i("JahezSync", "enqueueAllLocalProductsIfMissing finished: active=${activeIds.size}, synced=${syncedIds.size}, pending=${pendingItems.size}, freshlyEnqueued=$enqueuedCount")
    }

    suspend fun enqueueProductUpsert(productId: Long, storeId: String) {
        if (storeId.isBlank()) return
        val entity = ProductSyncEntity(
            productId = productId,
            storeId = storeId,
            operation = "UPSERT",
            status = "PENDING",
            updatedAt = System.currentTimeMillis()
        )
        productSyncDao.enqueue(entity)
        DiagnosticsLogger.d("JahezSync", "Enqueued UPSERT for productId=$productId, storeId=$storeId")
    }

    suspend fun enqueueProductDelete(productId: Long, storeId: String) {
        if (storeId.isBlank()) return
        val entity = ProductSyncEntity(
            productId = productId,
            storeId = storeId,
            operation = "DELETE",
            status = "PENDING",
            updatedAt = System.currentTimeMillis()
        )
        productSyncDao.enqueue(entity)
        DiagnosticsLogger.d("JahezSync", "Enqueued DELETE for productId=$productId, storeId=$storeId")
    }

    suspend fun syncPendingToRemote(resetRetries: Boolean = true): SyncResult {
        if (!productRemoteRepository.isFirebaseConfigured()) {
            _syncStatus.value = SyncStatus.UNCONFIGURED
            DiagnosticsLogger.w("JahezSync", "syncPendingToRemote aborted: Firebase is not configured")
            return SyncResult(success = false, errorMessage = "Firebase is not configured")
        }

        val config = storeConfigRepository.storeConfig.first()
        val storeId = config.storeId.ifBlank { "store_default" }

        // Only Merchants have permission to push changes to Firestore
        if (config.role != UserRole.MERCHANT) {
            DiagnosticsLogger.d("JahezSync", "syncPendingToRemote skipped: user role is ${config.role}")
            return SyncResult(success = true, itemsPushed = 0)
        }

        if (resetRetries) {
            productSyncDao.resetFailedAndSyncingItems(storeId)
        }

        val pendingItems = productSyncDao.getPendingItems(storeId)
        DiagnosticsLogger.i("JahezSync", "syncPendingToRemote: found ${pendingItems.size} pending items for storeId=$storeId")
        if (pendingItems.isEmpty()) {
            return SyncResult(success = true, itemsPushed = 0)
        }

        var pushedCount = 0
        var hasFailures = false
        var lastError: String? = null

        for (item in pendingItems) {
            DiagnosticsLogger.d("JahezSync", "Processing sync item: productId=${item.productId}, op=${item.operation}, retries=${item.retryCount}")

            productSyncDao.updateStatus(
                productId = item.productId,
                status = "SYNCING",
                timestamp = System.currentTimeMillis(),
                error = null,
                incrementRetry = 0
            )

            if (item.operation == "UPSERT") {
                val productWithUnits = productDao.getProductWithUnitsById(item.productId)
                if (productWithUnits != null) {
                    val remoteUnits = productWithUnits.units.map { unit ->
                        ProductUnitRemoteModel(
                            id = unit.id,
                            productId = unit.productId,
                            unitName = unit.unitName,
                            price = unit.price,
                            isDefault = unit.isDefault,
                            minQuantity = unit.minQuantity
                        )
                    }

                    val remoteModel = ProductRemoteModel(
                        id = productWithUnits.product.id,
                        storeId = storeId,
                        name = productWithUnits.product.name,
                        categoryId = productWithUnits.product.categoryId,
                        isActive = productWithUnits.product.isActive,
                        units = remoteUnits,
                        createdAt = productWithUnits.product.createdAt,
                        updatedAt = productWithUnits.product.updatedAt
                    )

                    DiagnosticsLogger.i("JahezSync", "Calling upsertProduct for id=${productWithUnits.product.id}, name='${productWithUnits.product.name}' in merchants/$storeId/products/${productWithUnits.product.id}")
                    val result = productRemoteRepository.upsertProduct(storeId, remoteModel)
                    if (result.isSuccess) {
                        DiagnosticsLogger.i("JahezSync", "Successfully synced product id=${item.productId} to Firestore")
                        productSyncDao.delete(item.productId)
                        pushedCount++
                    } else {
                        hasFailures = true
                        lastError = result.exceptionOrNull()?.message
                        DiagnosticsLogger.e("JahezSync", "Failed to sync product id=${item.productId}: $lastError", result.exceptionOrNull())
                        productSyncDao.updateStatus(
                            productId = item.productId,
                            status = "FAILED",
                            timestamp = System.currentTimeMillis(),
                            error = lastError,
                            incrementRetry = 1
                        )
                    }
                } else {
                    // Product was completely deleted locally, push soft-delete to remote
                    DiagnosticsLogger.i("JahezSync", "Product id=${item.productId} deleted locally, pushing soft-delete to Firestore")
                    val result = productRemoteRepository.deleteProductSoft(storeId, item.productId)
                    if (result.isSuccess) {
                        productSyncDao.delete(item.productId)
                        pushedCount++
                    } else {
                        hasFailures = true
                        lastError = result.exceptionOrNull()?.message
                        DiagnosticsLogger.e("JahezSync", "Failed soft-delete for product id=${item.productId}: $lastError", result.exceptionOrNull())
                        productSyncDao.updateStatus(
                            productId = item.productId,
                            status = "FAILED",
                            timestamp = System.currentTimeMillis(),
                            error = lastError,
                            incrementRetry = 1
                        )
                    }
                }
            } else if (item.operation == "DELETE") {
                val result = productRemoteRepository.deleteProductSoft(
                    storeId = storeId,
                    productId = item.productId,
                    updatedAt = item.updatedAt
                )
                if (result.isSuccess) {
                    productSyncDao.delete(item.productId)
                    pushedCount++
                } else {
                    hasFailures = true
                    lastError = result.exceptionOrNull()?.message
                    DiagnosticsLogger.e("JahezSync", "Failed DELETE op for product id=${item.productId}: $lastError", result.exceptionOrNull())
                    productSyncDao.updateStatus(
                        productId = item.productId,
                        status = "FAILED",
                        timestamp = System.currentTimeMillis(),
                        error = lastError,
                        incrementRetry = 1
                    )
                }
            }
        }

        return SyncResult(
            success = !hasFailures,
            itemsPushed = pushedCount,
            errorMessage = lastError
        )
    }

    suspend fun pullRemoteDelta(): SyncResult {
        if (!productRemoteRepository.isFirebaseConfigured()) {
            _syncStatus.value = SyncStatus.UNCONFIGURED
            return SyncResult(success = false, errorMessage = "Firebase is not configured")
        }

        val config = storeConfigRepository.storeConfig.first()
        val storeId = config.storeId.ifBlank { "store_default" }

        val metadataKey = "product_sync_$storeId"
        val lastSyncTimestamp = syncMetadataDao.getLastSyncTimestamp(metadataKey) ?: 0L

        DiagnosticsLogger.d("JahezSync", "pullRemoteDelta starting for storeId=$storeId since timestamp=$lastSyncTimestamp")
        val result = productRemoteRepository.getProductsDelta(storeId, lastSyncTimestamp)
        if (result.isFailure) {
            val errorMsg = result.exceptionOrNull()?.message
            DiagnosticsLogger.e("JahezSync", "pullRemoteDelta failed: $errorMsg", result.exceptionOrNull())
            return SyncResult(
                success = false,
                errorMessage = errorMsg
            )
        }

        val remoteProducts = result.getOrNull() ?: emptyList()
        DiagnosticsLogger.i("JahezSync", "pullRemoteDelta fetched ${remoteProducts.size} products from Firestore")
        var pulledCount = 0
        var maxUpdatedAt = lastSyncTimestamp

        for (remoteProduct in remoteProducts) {
            val localProduct = productDao.getProductById(remoteProduct.id)

            // Conflict handling: Last Write Wins based on updatedAt
            if (localProduct != null && localProduct.updatedAt > remoteProduct.updatedAt) {
                // Local version is newer, skip overwriting
                continue
            }

            // Map remote product to Room entity while preserving local imageUri
            val productEntity = ProductEntity(
                id = remoteProduct.id,
                name = remoteProduct.name,
                imageUri = localProduct?.imageUri, // Preserve local file storage uri
                categoryId = remoteProduct.categoryId,
                isActive = remoteProduct.isActive,
                createdAt = remoteProduct.createdAt,
                updatedAt = remoteProduct.updatedAt
            )

            val unitEntities = remoteProduct.units.map { unit ->
                ProductUnitEntity(
                    id = unit.id,
                    productId = remoteProduct.id,
                    unitName = unit.unitName,
                    price = unit.price,
                    isDefault = unit.isDefault,
                    minQuantity = unit.minQuantity
                )
            }

            if (localProduct == null) {
                productDao.insertProduct(productEntity)
                if (unitEntities.isNotEmpty()) {
                    productDao.insertProductUnits(unitEntities)
                }
            } else {
                productDao.updateProductWithUnits(productEntity, unitEntities)
            }

            pulledCount++
            if (remoteProduct.updatedAt > maxUpdatedAt) {
                maxUpdatedAt = remoteProduct.updatedAt
            }
        }

        if (remoteProducts.isNotEmpty()) {
            syncMetadataDao.upsertMetadata(
                SyncMetadataEntity(
                    key = metadataKey,
                    lastSyncTimestamp = maxUpdatedAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        return SyncResult(
            success = true,
            itemsPulled = pulledCount
        )
    }

    suspend fun syncAll(resetRetries: Boolean = true): SyncResult {
        if (!productRemoteRepository.isFirebaseConfigured()) {
            _syncStatus.value = SyncStatus.UNCONFIGURED
            return SyncResult(success = false, errorMessage = "Firebase is not configured")
        }

        val config = storeConfigRepository.storeConfig.first()
        val storeId = config.storeId.ifBlank { "store_default" }

        DiagnosticsLogger.i("JahezSync", "syncAll triggered for storeId=$storeId, role=${config.role}")
        _syncStatus.value = SyncStatus.SYNCING

        val pushResult = if (config.role == UserRole.MERCHANT) {
            syncPendingToRemote(resetRetries = resetRetries)
        } else {
            SyncResult(success = true)
        }

        val pullResult = pullRemoteDelta()

        val overallSuccess = pushResult.success && pullResult.success
        val pendingCountNow = productSyncDao.getPendingItems(storeId).size
        _syncStatus.value = if (overallSuccess) {
            if (pendingCountNow > 0) SyncStatus.PENDING else SyncStatus.SYNCED
        } else {
            SyncStatus.FAILED
        }

        DiagnosticsLogger.i("JahezSync", "syncAll completed: pushSuccess=${pushResult.success}, pushed=${pushResult.itemsPushed}, pullSuccess=${pullResult.success}, pulled=${pullResult.itemsPulled}, pendingLeft=$pendingCountNow, status=${_syncStatus.value}")

        return SyncResult(
            success = overallSuccess,
            itemsPushed = pushResult.itemsPushed,
            itemsPulled = pullResult.itemsPulled,
            errorMessage = pushResult.errorMessage ?: pullResult.errorMessage
        )
    }
}

