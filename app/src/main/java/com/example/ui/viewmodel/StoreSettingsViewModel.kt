package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUser
import com.example.data.auth.FirebaseAuthRepository
import com.example.data.remote.FirebaseBootstrapManager
import com.example.data.store.StoreConfig
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoreSettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val authRepository: AuthRepository = FirebaseAuthRepository(application),
    private val bootstrapManager: FirebaseBootstrapManager = FirebaseBootstrapManager.getInstance(application)
) : AndroidViewModel(application) {

    init {
        viewModelScope.launch {
            try {
                bootstrapManager.ensureBootstrappedAndSync()
            } catch (e: Exception) {
                // Ignore when offline or unconfigured
            }
        }
    }

    private val repository = StoreConfigRepository(application)

    val storeConfig: StateFlow<StoreConfig> = repository.storeConfig
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StoreConfig()
        )

    val currentUser: StateFlow<AuthUser?> = authRepository.currentUser
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val isFirebaseConfigured: Boolean
        get() = authRepository.isFirebaseConfigured()

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun updateStoreDetails(
        storeName: String,
        ownerPhone: String,
        whatsappNumber: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.updateStoreDetails(
                storeName = storeName,
                ownerPhone = ownerPhone,
                whatsappNumber = whatsappNumber
            )
            _saveMessage.value = "تم حفظ بيانات المتجر بنجاح"
            onSuccess()
        }
    }

    fun setRole(role: UserRole) {
        viewModelScope.launch {
            repository.setRole(role)
        }
    }

    fun setStoreId(storeId: String) {
        viewModelScope.launch {
            repository.setStoreId(storeId)
        }
    }

    fun signOut() {
        authRepository.signOut()
    }

    fun clearSaveMessage() {
        _saveMessage.value = null
    }
}
