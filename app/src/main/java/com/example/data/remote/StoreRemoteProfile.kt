package com.example.data.remote

data class StoreRemoteProfile(
    val storeId: String = "",
    val storeName: String = "",
    val ownerUid: String = "",
    val ownerPhone: String = "",
    val whatsappNumber: String = "",
    val status: String = "ACTIVE",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
