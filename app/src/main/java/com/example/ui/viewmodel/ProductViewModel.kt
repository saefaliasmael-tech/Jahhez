package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ProductRepository

    init {
        val productDao = AppDatabase.getDatabase(application).productDao()
        repository = ProductRepository(productDao)
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductEntity>> = _searchQuery
        .flatMapLatest { query ->
            repository.searchProducts(query)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addProduct(
        name: String,
        imageUri: String?,
        priceStr: String,
        unit: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("الرجاء إدخال اسم المنتج")
            return
        }
        val price = priceStr.toLongOrNull()
        if (price == null || price < 0) {
            onError("الرجاء إدخال سعر صالح")
            return
        }
        if (unit.isBlank()) {
            onError("الرجاء اختيار الوحدة")
            return
        }

        viewModelScope.launch {
            repository.insertProduct(name.trim(), imageUri, price, unit)
            onSuccess()
        }
    }

    fun updateProduct(
        id: Long,
        name: String,
        imageUri: String?,
        priceStr: String,
        unit: String,
        createdAt: Long,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("الرجاء إدخال اسم المنتج")
            return
        }
        val price = priceStr.toLongOrNull()
        if (price == null || price < 0) {
            onError("الرجاء إدخال سعر صالح")
            return
        }
        if (unit.isBlank()) {
            onError("الرجاء اختيار الوحدة")
            return
        }

        viewModelScope.launch {
            repository.updateProduct(id, name.trim(), imageUri, price, unit, createdAt)
            onSuccess()
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
        }
    }
}
