package com.example.data.remote

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

open class StoreRemoteRepository(private val context: Context) {

    private fun getFirestoreSafe(): FirebaseFirestore? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseFirestore.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    open fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    open suspend fun getStoreProfile(storeId: String): Result<StoreRemoteProfile?> {
        if (storeId.isBlank()) return Result.success(null)
        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val doc = firestore.collection("merchants").document(storeId).get().await()
            if (!doc.exists()) {
                Result.success(null)
            } else {
                val data = doc.data ?: return Result.success(null)
                val profile = StoreRemoteProfile(
                    storeId = data["storeId"] as? String ?: storeId,
                    storeName = data["storeName"] as? String ?: "",
                    ownerUid = data["ownerUid"] as? String ?: "",
                    ownerPhone = data["ownerPhone"] as? String ?: "",
                    whatsappNumber = data["whatsappNumber"] as? String ?: "",
                    status = data["status"] as? String ?: "ACTIVE",
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun saveStoreProfile(profile: StoreRemoteProfile): Result<Unit> {
        if (profile.storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("storeId cannot be empty"))
        }
        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val map = mapOf(
                "storeId" to profile.storeId,
                "storeName" to profile.storeName,
                "ownerUid" to profile.ownerUid,
                "ownerPhone" to profile.ownerPhone,
                "whatsappNumber" to profile.whatsappNumber,
                "status" to profile.status,
                "createdAt" to profile.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("merchants").document(profile.storeId).set(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
