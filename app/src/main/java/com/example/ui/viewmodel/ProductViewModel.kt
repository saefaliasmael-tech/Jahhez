package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import com.example.data.ProductWithUnits
import com.example.data.ProductRepository
import com.example.data.remote.FirebaseBootstrapManager
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import com.example.data.sync.ProductSyncManager
import com.example.data.sync.SyncStatus
import com.example.util.ImageStorageHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProductViewModel @JvmOverloads constructor(
    application: Application,
    val bootstrapManager: FirebaseBootstrapManager = FirebaseBootstrapManager.getInstance(application)
) : AndroidViewModel(application) {
    private val repository: ProductRepository
    val syncManager: ProductSyncManager
    private val storeConfigRepo: StoreConfigRepository

    init {
        val productDao = AppDatabase.getDatabase(application).productDao()
        repository = ProductRepository(productDao, application)
        syncManager = ProductSyncManager(application)
        storeConfigRepo = StoreConfigRepository(application)

        // Strict bootstrap and sync on startup:
        // Anonymous Auth -> User Profile -> Merchant Profile -> Product Sync
        viewModelScope.launch {
            try {
                bootstrapManager.ensureBootstrappedAndSync()
            } catch (e: Exception) {
                // Ignore offline startup failure
            }
        }
    }

    val userRole: StateFlow<UserRole> = storeConfigRepo.role
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserRole.MERCHANT
        )

    val syncStatus: StateFlow<SyncStatus> = syncManager.syncStatus

    val pendingSyncCount: StateFlow<Int> = syncManager.pendingCount
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun triggerSync() {
        viewModelScope.launch {
            bootstrapManager.ensureBootstrappedAndSync()
        }
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: StateFlow<List<ProductWithUnits>> = _searchQuery
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

    /**
     * Copies selected PhotoPicker URI to permanent internal storage.
     * Returns permanent file URI string.
     */
    fun saveImagePermanently(sourceUri: Uri): String? {
        return ImageStorageHelper.saveImageLocally(getApplication(), sourceUri)
    }

    fun addProduct(
        name: String,
        imageUri: String?,
        units: List<ProductUnitEntity>,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("الرجاء إدخال اسم المنتج")
            return
        }
        if (units.isEmpty()) {
            onError("الرجاء إضافة وحدة بيع واحدة على الأقل")
            return
        }
        for (unit in units) {
            if (unit.unitName.isBlank()) {
                onError("الرجاء تحديد اسم الوحدة")
                return
            }
            if (unit.price < 0) {
                onError("السعر يجب أن يكون 0 أو أكثر")
                return
            }
        }

        viewModelScope.launch {
            try {
                repository.insertProduct(name.trim(), imageUri, units)
                onSuccess()
                syncManager.syncPendingToRemote()
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء حفظ المنتج")
            }
        }
    }

    fun updateProduct(
        id: Long,
        name: String,
        imageUri: String?,
        units: List<ProductUnitEntity>,
        createdAt: Long,
        oldImageUri: String?,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (name.isBlank()) {
            onError("الرجاء إدخال اسم المنتج")
            return
        }
        if (units.isEmpty()) {
            onError("الرجاء إضافة وحدة بيع واحدة على الأقل")
            return
        }
        for (unit in units) {
            if (unit.unitName.isBlank()) {
                onError("الرجاء تحديد اسم الوحدة")
                return
            }
            if (unit.price < 0) {
                onError("السعر يجب أن يكون 0 أو أكثر")
                return
            }
        }

        viewModelScope.launch {
            try {
                repository.updateProduct(id, name.trim(), imageUri, units, createdAt, oldImageUri)
                onSuccess()
                syncManager.syncPendingToRemote()
            } catch (e: Exception) {
                onError(e.message ?: "حدث خطأ أثناء تحديث المنتج")
            }
        }
    }

    fun deleteProduct(product: ProductEntity) {
        viewModelScope.launch {
            repository.deleteProduct(product)
            syncManager.syncPendingToRemote()
        }
    }
}
