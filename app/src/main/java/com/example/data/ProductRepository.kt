package com.example.data

import android.content.Context
import com.example.data.store.StoreConfigRepository
import com.example.data.sync.ProductSyncManager
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ProductRepository(
    private val productDao: ProductDao,
    private val context: Context? = null
) {
    val allProductsWithUnits: Flow<List<ProductWithUnits>> = productDao.getAllProductsWithUnits()

    private val syncManager: ProductSyncManager? = context?.let { ProductSyncManager(it) }
    private val storeConfigRepo: StoreConfigRepository? = context?.let { StoreConfigRepository(it) }

    fun searchProducts(query: String): Flow<List<ProductWithUnits>> {
        return if (query.isBlank()) {
            productDao.getAllProductsWithUnits()
        } else {
            productDao.searchProductsWithUnits(query.trim())
        }
    }

    suspend fun getProductWithUnitsById(id: Long): ProductWithUnits? {
        return productDao.getProductWithUnitsById(id)
    }

    suspend fun insertProduct(
        name: String,
        imageUri: String?,
        units: List<ProductUnitEntity>
    ): Long {
        require(name.isNotBlank()) { "اسم المنتج مطلوب" }
        require(units.isNotEmpty()) { "يجب إضافة وحدة بيع واحدة على الأقل" }

        val normalizedUnits = normalizeUnits(units)
        val now = System.currentTimeMillis()
        val product = ProductEntity(
            name = name.trim(),
            imageUri = imageUri,
            createdAt = now,
            updatedAt = now
        )
        val productId = productDao.insertProductWithUnits(product, normalizedUnits)

        if (storeConfigRepo != null && syncManager != null) {
            try {
                val storeId = storeConfigRepo.storeConfig.first().storeId.ifBlank { "store_default" }
                syncManager.enqueueProductUpsert(productId, storeId)
            } catch (e: Exception) {
                // Keep local save resilient
            }
        }

        return productId
    }

    suspend fun updateProduct(
        id: Long,
        name: String,
        imageUri: String?,
        units: List<ProductUnitEntity>,
        createdAt: Long,
        oldImageUri: String? = null
    ) {
        require(name.isNotBlank()) { "اسم المنتج مطلوب" }
        require(units.isNotEmpty()) { "يجب إضافة وحدة بيع واحدة على الأقل" }

        if (oldImageUri != null && oldImageUri != imageUri && context != null) {
            ImageStorageHelper.deleteInternalImage(context, oldImageUri)
        }

        val normalizedUnits = normalizeUnits(units)
        val product = ProductEntity(
            id = id,
            name = name.trim(),
            imageUri = imageUri,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        productDao.updateProductWithUnits(product, normalizedUnits)

        if (storeConfigRepo != null && syncManager != null) {
            try {
                val storeId = storeConfigRepo.storeConfig.first().storeId.ifBlank { "store_default" }
                syncManager.enqueueProductUpsert(id, storeId)
            } catch (e: Exception) {
                // Keep local save resilient
            }
        }
    }

    suspend fun deleteProduct(product: ProductEntity) {
        if (context != null && !product.imageUri.isNullOrBlank()) {
            ImageStorageHelper.deleteInternalImage(context, product.imageUri)
        }

        val now = System.currentTimeMillis()
        val deactivated = product.copy(isActive = false, updatedAt = now)
        productDao.updateProduct(deactivated)

        if (storeConfigRepo != null && syncManager != null) {
            try {
                val storeId = storeConfigRepo.storeConfig.first().storeId.ifBlank { "store_default" }
                syncManager.enqueueProductDelete(product.id, storeId)
            } catch (e: Exception) {
                // Keep local delete resilient
            }
        }
    }

    suspend fun hardDeleteProduct(product: ProductEntity) {
        if (context != null && !product.imageUri.isNullOrBlank()) {
            ImageStorageHelper.deleteInternalImage(context, product.imageUri)
        }
        productDao.deleteProduct(product)
    }

    private fun normalizeUnits(units: List<ProductUnitEntity>): List<ProductUnitEntity> {
        val hasDefault = units.any { it.isDefault }
        return if (!hasDefault) {
            units.mapIndexed { index, unit ->
                unit.copy(isDefault = index == 0)
            }
        } else {
            var foundFirst = false
            units.map { unit ->
                if (unit.isDefault) {
                    if (!foundFirst) {
                        foundFirst = true
                        unit
                    } else {
                        unit.copy(isDefault = false)
                    }
                } else {
                    unit
                }
            }
        }
    }
}
