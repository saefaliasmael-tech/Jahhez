package com.example.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "order_items",
    foreignKeys = [
        ForeignKey(
            entity = OrderEntity::class,
            parentColumns = ["id"],
            childColumns = ["orderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["orderId"])
    ]
)
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val orderId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val productImageUriSnapshot: String? = null,
    val unitSnapshot: String,
    val unitPriceSnapshot: Long,
    val quantity: Int,
    val lineTotal: Long
)
