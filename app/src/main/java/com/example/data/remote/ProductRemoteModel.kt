package com.example.data.remote

data class ProductUnitRemoteModel(
    val id: Long = 0L,
    val productId: Long = 0L,
    val unitName: String = "",
    val price: Long = 0L,
    val isDefault: Boolean = true,
    val minQuantity: Int = 1
)

data class ProductRemoteModel(
    val id: Long = 0L,
    val storeId: String = "",
    val name: String = "",
    val categoryId: Long? = null,
    val isActive: Boolean = true,
    val units: List<ProductUnitRemoteModel> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
