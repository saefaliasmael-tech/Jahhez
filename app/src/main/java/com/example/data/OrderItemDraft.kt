package com.example.data

data class OrderItemDraft(
    val productId: Long,
    val productUnitId: Long? = null,
    val productNameSnapshot: String,
    val productImageUriSnapshot: String? = null,
    val unitSnapshot: String,
    val unitPriceSnapshot: Long,
    val quantity: Int
) {
    val lineTotal: Long
        get() = unitPriceSnapshot * quantity

    fun toEntity(orderId: Long): OrderItemEntity {
        return OrderItemEntity(
            orderId = orderId,
            productId = productId,
            productUnitId = productUnitId,
            productNameSnapshot = productNameSnapshot,
            productImageUriSnapshot = productImageUriSnapshot,
            unitSnapshot = unitSnapshot,
            unitPriceSnapshot = unitPriceSnapshot,
            quantity = quantity,
            lineTotal = lineTotal
        )
    }

    companion object {
        fun fromProductWithUnits(
            productWithUnits: ProductWithUnits,
            selectedUnit: ProductUnitEntity? = null,
            quantity: Int = 1
        ): OrderItemDraft {
            val unit = selectedUnit ?: productWithUnits.defaultUnit
            return OrderItemDraft(
                productId = productWithUnits.product.id,
                productUnitId = unit?.id,
                productNameSnapshot = productWithUnits.product.name,
                productImageUriSnapshot = productWithUnits.product.imageUri,
                unitSnapshot = unit?.unitName ?: "",
                unitPriceSnapshot = unit?.price ?: 0L,
                quantity = quantity
            )
        }

        fun fromEntity(entity: OrderItemEntity): OrderItemDraft {
            return OrderItemDraft(
                productId = entity.productId,
                productUnitId = entity.productUnitId,
                productNameSnapshot = entity.productNameSnapshot,
                productImageUriSnapshot = entity.productImageUriSnapshot,
                unitSnapshot = entity.unitSnapshot,
                unitPriceSnapshot = entity.unitPriceSnapshot,
                quantity = entity.quantity
            )
        }
    }
}
