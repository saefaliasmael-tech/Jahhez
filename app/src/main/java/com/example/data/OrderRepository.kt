package com.example.data

import kotlinx.coroutines.flow.Flow

class OrderRepository(private val orderDao: OrderDao) {
    val ordersWithItems: Flow<List<OrderWithItems>> = orderDao.getOrdersWithItems()

    fun getOrderById(orderId: Long): Flow<OrderWithItems?> {
        return orderDao.getOrderWithItemsById(orderId)
    }

    suspend fun getOrderDirect(orderId: Long): OrderWithItems? {
        return orderDao.getOrderWithItemsDirect(orderId)
    }

    suspend fun createOrder(status: String, items: List<OrderItemDraft>): Long {
        val totalAmount = items.sumOf { it.lineTotal }
        val now = System.currentTimeMillis()
        val order = OrderEntity(
            createdAt = now,
            updatedAt = now,
            status = status,
            totalAmount = totalAmount
        )
        val entities = items.map { it.toEntity(0L) }
        return orderDao.saveOrderWithItems(order, entities)
    }

    suspend fun updateOrder(
        orderId: Long,
        createdAt: Long,
        status: String,
        items: List<OrderItemDraft>
    ) {
        val totalAmount = items.sumOf { it.lineTotal }
        val order = OrderEntity(
            id = orderId,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
            status = status,
            totalAmount = totalAmount
        )
        val entities = items.map { it.toEntity(orderId) }
        orderDao.updateOrderWithItems(order, entities)
    }

    suspend fun deleteOrder(order: OrderEntity) {
        orderDao.deleteOrder(order)
    }
}
