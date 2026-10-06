package com.example.data

data class OrderItemDraft(
    val productId: Long,
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
            productNameSnapshot = productNameSnapshot,
            productImageUriSnapshot = productImageUriSnapshot,
            unitSnapshot = unitSnapshot,
            unitPriceSnapshot = unitPriceSnapshot,
            quantity = quantity,
            lineTotal = lineTotal
        )
    }

    companion object {
        fun fromProduct(product: ProductEntity, quantity: Int = 1): OrderItemDraft {
            return OrderItemDraft(
                productId = product.id,
                productNameSnapshot = product.name,
                productImageUriSnapshot = product.imageUri,
                unitSnapshot = product.unit,
                unitPriceSnapshot = product.price,
                quantity = quantity
            )
        }

        fun fromEntity(entity: OrderItemEntity): OrderItemDraft {
            return OrderItemDraft(
                productId = entity.productId,
                productNameSnapshot = entity.productNameSnapshot,
                productImageUriSnapshot = entity.productImageUriSnapshot,
                unitSnapshot = entity.unitSnapshot,
                unitPriceSnapshot = entity.unitPriceSnapshot,
                quantity = entity.quantity
            )
        }
    }
}
