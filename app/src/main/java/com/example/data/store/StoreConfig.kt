package com.example.data.store

data class StoreConfig(
    val storeId: String = "",
    val storeName: String = DEFAULT_STORE_NAME,
    val role: UserRole = UserRole.MERCHANT,
    val ownerPhone: String = "",
    val whatsappNumber: String = "",
    val firebaseUid: String? = null
) {
    companion object {
        const val DEFAULT_STORE_NAME = "متجري"
    }
}
