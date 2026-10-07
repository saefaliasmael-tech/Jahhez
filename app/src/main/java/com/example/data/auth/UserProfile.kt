package com.example.data.auth

import com.example.data.store.UserRole

data class UserProfile(
    val uid: String = "",
    val storeId: String = "",
    val role: UserRole = UserRole.MERCHANT,
    val displayName: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
