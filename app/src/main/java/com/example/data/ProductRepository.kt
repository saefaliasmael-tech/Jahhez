package com.example.data

import kotlinx.coroutines.flow.Flow

class ProductRepository(private val productDao: ProductDao) {
    val allProducts: Flow<List<ProductEntity>> = productDao.getAllProducts()

    fun searchProducts(query: String): Flow<List<ProductEntity>> {
        return if (query.isBlank()) {
            productDao.getAllProducts()
        } else {
            productDao.searchProducts(query)
        }
    }

    suspend fun insertProduct(name: String, imageUri: String?, price: Long, unit: String): Long {
        val currentTime = System.currentTimeMillis()
        val product = ProductEntity(
            name = name,
            imageUri = imageUri,
            price = price,
            unit = unit,
            createdAt = currentTime,
            updatedAt = currentTime
        )
        return productDao.insertProduct(product)
    }

    suspend fun updateProduct(id: Long, name: String, imageUri: String?, price: Long, unit: String, createdAt: Long) {
        val product = ProductEntity(
            id = id,
            name = name,
            imageUri = imageUri,
            price = price,
            unit = unit,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
        productDao.updateProduct(product)
    }

    suspend fun deleteProduct(product: ProductEntity) {
        productDao.deleteProduct(product)
    }
}
