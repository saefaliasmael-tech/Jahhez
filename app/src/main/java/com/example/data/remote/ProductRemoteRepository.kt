package com.example.data.remote

import android.content.Context
import com.example.util.DiagnosticsLogger
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

open class ProductRemoteRepository(private val context: Context) {

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

    open suspend fun getProductsDelta(
        storeId: String,
        sinceTimestamp: Long
    ): Result<List<ProductRemoteModel>> {
        if (storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("storeId cannot be empty"))
        }

        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val collectionRef = firestore.collection("merchants")
                .document(storeId)
                .collection("products")

            val querySnapshot = if (sinceTimestamp > 0L) {
                collectionRef
                    .whereGreaterThan("updatedAt", sinceTimestamp)
                    .orderBy("updatedAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
            } else {
                collectionRef
                    .orderBy("updatedAt", Query.Direction.ASCENDING)
                    .get()
                    .await()
            }

            val products = querySnapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: return@mapNotNull null

                val rawUnits = data["units"] as? List<*> ?: emptyList<Any>()
                val units = rawUnits.mapNotNull { item ->
                    val map = item as? Map<*, *> ?: return@mapNotNull null
                    ProductUnitRemoteModel(
                        id = (map["id"] as? Number)?.toLong() ?: 0L,
                        productId = (map["productId"] as? Number)?.toLong() ?: id,
                        unitName = map["unitName"] as? String ?: "",
                        price = (map["price"] as? Number)?.toLong() ?: 0L,
                        isDefault = map["isDefault"] as? Boolean ?: true,
                        minQuantity = (map["minQuantity"] as? Number)?.toInt() ?: 1
                    )
                }

                ProductRemoteModel(
                    id = id,
                    storeId = data["storeId"] as? String ?: storeId,
                    name = data["name"] as? String ?: "",
                    categoryId = (data["categoryId"] as? Number)?.toLong(),
                    isActive = data["isActive"] as? Boolean ?: true,
                    units = units,
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
            }

            Result.success(products)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun upsertProduct(
        storeId: String,
        product: ProductRemoteModel
    ): Result<Unit> {
        if (storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("storeId cannot be empty"))
        }
        if (product.id <= 0L) {
            return Result.failure(IllegalArgumentException("product id must be positive"))
        }

        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val unitsMap = product.units.map { unit ->
                mapOf(
                    "id" to unit.id,
                    "productId" to unit.productId,
                    "unitName" to unit.unitName,
                    "price" to unit.price,
                    "isDefault" to unit.isDefault,
                    "minQuantity" to unit.minQuantity
                )
            }

            val map = mutableMapOf<String, Any>(
                "id" to product.id,
                "storeId" to storeId,
                "name" to product.name,
                "isActive" to product.isActive,
                "units" to unitsMap,
                "createdAt" to product.createdAt,
                "updatedAt" to product.updatedAt
            )
            if (product.categoryId != null) {
                map["categoryId"] = product.categoryId
            }

            val docPath = "merchants/$storeId/products/${product.id}"
            DiagnosticsLogger.d("JahezRemote", "Attempting Firestore set at $docPath (unitsCount=${unitsMap.size})")

            firestore.collection("merchants")
                .document(storeId)
                .collection("products")
                .document(product.id.toString())
                .set(map)
                .await()

            DiagnosticsLogger.i("JahezRemote", "Firestore document successfully written at $docPath")
            Result.success(Unit)
        } catch (e: Exception) {
            DiagnosticsLogger.e("JahezRemote", "Firestore write failed at merchants/$storeId/products/${product.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    open suspend fun deleteProductSoft(
        storeId: String,
        productId: Long,
        updatedAt: Long = System.currentTimeMillis()
    ): Result<Unit> {
        if (storeId.isBlank()) {
            return Result.failure(IllegalArgumentException("storeId cannot be empty"))
        }
        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val docRef = firestore.collection("merchants")
                .document(storeId)
                .collection("products")
                .document(productId.toString())

            val updateMap = mapOf<String, Any>(
                "isActive" to false,
                "updatedAt" to updatedAt
            )
            docRef.update(updateMap).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
