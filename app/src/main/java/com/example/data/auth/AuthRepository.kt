package com.example.data.auth

import android.content.Context
import com.example.data.store.UserRole
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val currentUser: Flow<AuthUser?>
    val currentUid: String?
    val isSignedIn: Boolean
    fun isFirebaseConfigured(): Boolean
    fun signOut()
    suspend fun getUserProfile(uid: String): Result<UserProfile?>
    suspend fun saveUserProfile(profile: UserProfile): Result<Unit>
    suspend fun ensureAnonymousSignIn(): Result<Unit>
}

class FirebaseAuthRepository(
    private val context: Context
) : AuthRepository {

    private fun getFirebaseAuthSafe(): FirebaseAuth? {
        return try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseAuth.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

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

    override fun isFirebaseConfigured(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val auth = getFirebaseAuthSafe()
        if (auth == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            val authUser = user?.let {
                AuthUser(
                    uid = it.uid,
                    email = it.email,
                    displayName = it.displayName,
                    isAnonymous = it.isAnonymous
                )
            }
            trySend(authUser)
        }

        auth.addAuthStateListener(listener)
        awaitClose {
            auth.removeAuthStateListener(listener)
        }
    }

    override val currentUid: String?
        get() = getFirebaseAuthSafe()?.currentUser?.uid

    override val isSignedIn: Boolean
        get() = getFirebaseAuthSafe()?.currentUser != null

    override fun signOut() {
        try {
            getFirebaseAuthSafe()?.signOut()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override suspend fun getUserProfile(uid: String): Result<UserProfile?> {
        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            if (!doc.exists()) {
                Result.success(null)
            } else {
                val data = doc.data ?: return Result.success(null)
                val roleStr = data["role"] as? String ?: UserRole.MERCHANT.name
                val role = try {
                    UserRole.valueOf(roleStr)
                } catch (e: Exception) {
                    UserRole.MERCHANT
                }

                val profile = UserProfile(
                    uid = data["uid"] as? String ?: uid,
                    storeId = data["storeId"] as? String ?: "",
                    role = role,
                    displayName = data["displayName"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                )
                Result.success(profile)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val firestore = getFirestoreSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            val map = mapOf(
                "uid" to profile.uid,
                "storeId" to profile.storeId,
                "role" to profile.role.name,
                "displayName" to profile.displayName,
                "phoneNumber" to profile.phoneNumber,
                "createdAt" to profile.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection("users").document(profile.uid).set(map).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun ensureAnonymousSignIn(): Result<Unit> {
        val auth = getFirebaseAuthSafe()
            ?: return Result.failure(IllegalStateException("Firebase is not configured"))

        return try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
