package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.OrderEntity
import com.example.data.OrderItemDraft
import com.example.data.OrderRepository
import com.example.data.OrderWithItems
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import com.example.data.ProductUnitEntity
import com.example.data.ProductWithUnits
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val orderRepository: OrderRepository
    private val productRepository: ProductRepository

    init {
        val db = AppDatabase.getDatabase(application)
        orderRepository = OrderRepository(db.orderDao())
        productRepository = ProductRepository(db.productDao(), application)
    }

    val ordersWithItems: StateFlow<List<OrderWithItems>> = orderRepository.ordersWithItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _productSearchQuery = MutableStateFlow("")
    val productSearchQuery: StateFlow<String> = _productSearchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val availableProducts: StateFlow<List<ProductWithUnits>> = _productSearchQuery
        .flatMapLatest { query ->
            productRepository.searchProducts(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _editingOrderId = MutableStateFlow<Long?>(null)
    val editingOrderId: StateFlow<Long?> = _editingOrderId.asStateFlow()

    private var editingOrderCreatedAt: Long = 0L

    private val _draftStatus = MutableStateFlow(OrderEntity.STATUS_READY)
    val draftStatus: StateFlow<String> = _draftStatus.asStateFlow()

    private val _draftItems = MutableStateFlow<List<OrderItemDraft>>(emptyList())
    val draftItems: StateFlow<List<OrderItemDraft>> = _draftItems.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    val draftTotalAmount: StateFlow<Long> = _draftItems.map { items ->
        items.sumOf { it.lineTotal }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = 0L
    )

    fun setProductSearchQuery(query: String) {
        _productSearchQuery.value = query
    }

    fun startNewOrder() {
        _editingOrderId.value = null
        editingOrderCreatedAt = System.currentTimeMillis()
        _draftStatus.value = OrderEntity.STATUS_READY
        _draftItems.value = emptyList()
        _hasUnsavedChanges.value = false
        _productSearchQuery.value = ""
    }

    fun loadOrderForEditing(orderId: Long) {
        viewModelScope.launch {
            val orderWithItems = orderRepository.getOrderDirect(orderId)
            if (orderWithItems != null) {
                _editingOrderId.value = orderWithItems.order.id
                editingOrderCreatedAt = orderWithItems.order.createdAt
                _draftStatus.value = orderWithItems.order.status
                _draftItems.value = orderWithItems.items.map { OrderItemDraft.fromEntity(it) }
                _hasUnsavedChanges.value = false
                _productSearchQuery.value = ""
            }
        }
    }

    fun addProductToDraft(productWithUnits: ProductWithUnits, unit: ProductUnitEntity? = null) {
        val selectedUnit = unit ?: productWithUnits.defaultUnit ?: return
        val currentItems = _draftItems.value.toMutableList()
        val index = currentItems.indexOfFirst {
            it.productId == productWithUnits.product.id && it.productUnitId == selectedUnit.id
        }
        if (index != -1) {
            val existing = currentItems[index]
            currentItems[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            currentItems.add(OrderItemDraft.fromProductWithUnits(productWithUnits, selectedUnit, quantity = 1))
        }
        _draftItems.value = currentItems
        _hasUnsavedChanges.value = true
    }

    fun addProductToDraft(
        product: ProductEntity,
        unitName: String = "كارتون",
        price: Long = 0L,
        unitId: Long? = null
    ) {
        val currentItems = _draftItems.value.toMutableList()
        val index = currentItems.indexOfFirst { it.productId == product.id }
        if (index != -1) {
            val existing = currentItems[index]
            currentItems[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            currentItems.add(
                OrderItemDraft(
                    productId = product.id,
                    productUnitId = unitId,
                    productNameSnapshot = product.name,
                    productImageUriSnapshot = product.imageUri,
                    unitSnapshot = unitName,
                    unitPriceSnapshot = price,
                    quantity = 1
                )
            )
        }
        _draftItems.value = currentItems
        _hasUnsavedChanges.value = true
    }

    fun incrementQuantity(productId: Long, productUnitId: Long? = null) {
        val currentItems = _draftItems.value.toMutableList()
        val index = currentItems.indexOfFirst {
            it.productId == productId && (productUnitId == null || it.productUnitId == productUnitId)
        }
        if (index != -1) {
            val existing = currentItems[index]
            currentItems[index] = existing.copy(quantity = existing.quantity + 1)
            _draftItems.value = currentItems
            _hasUnsavedChanges.value = true
        }
    }

    fun decrementQuantity(productId: Long, productUnitId: Long? = null) {
        val currentItems = _draftItems.value.toMutableList()
        val index = currentItems.indexOfFirst {
            it.productId == productId && (productUnitId == null || it.productUnitId == productUnitId)
        }
        if (index != -1) {
            val existing = currentItems[index]
            if (existing.quantity > 1) {
                currentItems[index] = existing.copy(quantity = existing.quantity - 1)
                _draftItems.value = currentItems
                _hasUnsavedChanges.value = true
            }
        }
    }

    fun removeItemFromDraft(productId: Long, productUnitId: Long? = null) {
        val currentItems = _draftItems.value.toMutableList()
        val index = currentItems.indexOfFirst {
            it.productId == productId && (productUnitId == null || it.productUnitId == productUnitId)
        }
        if (index != -1) {
            currentItems.removeAt(index)
            _draftItems.value = currentItems
            _hasUnsavedChanges.value = true
        }
    }

    fun saveOrder(status: String = OrderEntity.STATUS_READY, onSuccess: () -> Unit) {
        if (_draftItems.value.isEmpty()) return

        viewModelScope.launch {
            val orderId = _editingOrderId.value
            if (orderId == null) {
                orderRepository.createOrder(status, _draftItems.value)
            } else {
                orderRepository.updateOrder(
                    orderId = orderId,
                    createdAt = editingOrderCreatedAt,
                    status = status,
                    items = _draftItems.value
                )
            }
            _hasUnsavedChanges.value = false
            onSuccess()
        }
    }

    fun deleteOrder(order: OrderEntity) {
        viewModelScope.launch {
            orderRepository.deleteOrder(order)
        }
    }

    fun markUnsavedChangesHandled() {
        _hasUnsavedChanges.value = false
    }
}
