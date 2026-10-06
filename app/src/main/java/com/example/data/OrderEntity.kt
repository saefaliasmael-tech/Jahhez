package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val status: String = STATUS_READY,
    val totalAmount: Long = 0L
) {
    companion object {
        const val STATUS_DRAFT = "DRAFT"
        const val STATUS_READY = "READY"
    }
}
