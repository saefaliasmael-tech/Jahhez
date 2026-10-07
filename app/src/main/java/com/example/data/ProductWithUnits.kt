package com.example.data

import androidx.room.Embedded
import androidx.room.Relation

data class ProductWithUnits(
    @Embedded
    val product: ProductEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "productId"
    )
    val units: List<ProductUnitEntity> = emptyList()
) {
    val defaultUnit: ProductUnitEntity?
        get() = units.firstOrNull { it.isDefault } ?: units.firstOrNull()

    val displayPrice: Long
        get() = defaultUnit?.price ?: 0L

    val displayUnitName: String
        get() = defaultUnit?.unitName ?: ""
}
