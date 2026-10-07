package com.example.data.remote

import android.content.Context
import com.example.data.auth.AuthRepository
import com.example.data.auth.FirebaseAuthRepository
import com.example.data.auth.UserProfile
import com.example.data.store.StoreConfig
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import com.example.data.sync.ProductSyncManager
import com.example.data.sync.SyncResult
import com.example.util.DiagnosticsLogger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coordinates Firebase bootstrap on initial application launch and during sync:
 * 1. Anonymous Authentication: Ensures an active Firebase user session without creating redundant accounts.
 * 2. User Profile: Verifies or initializes `users/{uid}` in Firestore with storeId and role = MERCHANT.
 * 3. Merchant Profile: Verifies or initializes `merchants/{storeId}` in Firestore with ownerUid = UID.
 * 4. Product Sync: Queues local products and runs bidirectional synchronization.
 */
class FirebaseBootstrapManager(
    private val authRepository: AuthRepository,
    private val storeRemoteRepository: StoreRemoteRepository,
    private val storeConfigRepository: StoreConfigRepository,
    private val productSyncManager: ProductSyncManager
) {
    constructor(context: Context) : this(
        authRepository = FirebaseAuthRepository(context),
        storeRemoteRepository = StoreRemoteRepository(context),
        storeConfigRepository = StoreConfigRepository(context),
        productSyncManager = ProductSyncManager(context)
    )

    private val mutex = Mutex()

    @Volatile
    private var bootstrapped: Boolean = false

    companion object {
        const val DEFAULT_STORE_ID = "store_default"
        const val DEFAULT_DISPLAY_NAME = "تاجر جهّز"

        @Volatile
        private var instance: FirebaseBootstrapManager? = null

        fun getInstance(context: Context): FirebaseBootstrapManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseBootstrapManager(context.applicationContext).also { instance = it }
            }
        }
    }

    fun isBootstrapped(): Boolean = bootstrapped

    /**
     * Executes the strict sequential bootstrap:
     * Anonymous Auth -> User Profile -> Merchant Profile
     * Idempotent: checks before creation to avoid redundant writes.
     */
    suspend fun ensureBootstrapped(): Result<String> = mutex.withLock {
        DiagnosticsLogger.i("JahezBootstrap", "ensureBootstrapped() starting...")
        if (!authRepository.isFirebaseConfigured()) {
            DiagnosticsLogger.w("JahezBootstrap", "Firebase is not configured on this environment")
            return Result.failure(IllegalStateException("Firebase is not configured"))
        }

        // STEP 1: Anonymous Authentication
        DiagnosticsLogger.i("JahezBootstrap", "Step 1: Checking/Executing Anonymous Authentication...")
        val signInResult = authRepository.ensureAnonymousSignIn()
        if (signInResult.isFailure) {
            val err = signInResult.exceptionOrNull() ?: Exception("Anonymous sign-in failed")
            DiagnosticsLogger.e("JahezBootstrap", "Step 1 failed: ${err.message}", err)
            return Result.failure(err)
        }

        val uid = authRepository.currentUid
        if (uid == null) {
            DiagnosticsLogger.e("JahezBootstrap", "Step 1 failed: UID is null")
            return Result.failure(IllegalStateException("CurrentUser UID is null after authentication"))
        }
        DiagnosticsLogger.i("JahezBootstrap", "Step 1 success: Active session UID = $uid")

        val currentConfig = storeConfigRepository.storeConfig.first()
        val targetStoreId = currentConfig.storeId.ifBlank { DEFAULT_STORE_ID }

        // STEP 2: User Profile in Firestore (users/{uid})
        DiagnosticsLogger.i("JahezBootstrap", "Step 2: Checking user profile in users/$uid...")
        val userProfileResult = authRepository.getUserProfile(uid)
        val existingProfile = userProfileResult.getOrNull()

        val effectiveStoreId = if (existingProfile == null) {
            DiagnosticsLogger.i("JahezBootstrap", "Step 2: User profile does not exist yet. Creating users/$uid with storeId=$targetStoreId...")
            val newProfile = UserProfile(
                uid = uid,
                storeId = targetStoreId,
                role = UserRole.MERCHANT,
                displayName = DEFAULT_DISPLAY_NAME,
                phoneNumber = currentConfig.ownerPhone,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val saveResult = authRepository.saveUserProfile(newProfile)
            if (saveResult.isFailure) {
                val err = saveResult.exceptionOrNull() ?: Exception("Failed to create user profile in Firestore")
                DiagnosticsLogger.e("JahezBootstrap", "Step 2 failed: ${err.message}", err)
                return Result.failure(err)
            }
            DiagnosticsLogger.i("JahezBootstrap", "Step 2 success: Created user profile in users/$uid")
            targetStoreId
        } else {
            DiagnosticsLogger.i("JahezBootstrap", "Step 2: Existing user profile found with storeId=${existingProfile.storeId}")
            existingProfile.storeId.ifBlank { targetStoreId }
        }

        // Keep local StoreConfig synchronized with the authenticated user & store
        if (currentConfig.storeId != effectiveStoreId || currentConfig.firebaseUid != uid) {
            storeConfigRepository.setStoreId(effectiveStoreId)
            storeConfigRepository.setFirebaseUid(uid)
            storeConfigRepository.setRole(existingProfile?.role ?: UserRole.MERCHANT)
        }

        // STEP 3: Merchant Profile in Firestore (merchants/{storeId})
        DiagnosticsLogger.i("JahezBootstrap", "Step 3: Checking merchant profile in merchants/$effectiveStoreId...")
        val storeProfileResult = storeRemoteRepository.getStoreProfile(effectiveStoreId)
        val existingStore = storeProfileResult.getOrNull()

        if (existingStore == null) {
            val storeName = currentConfig.storeName.ifBlank { StoreConfig.DEFAULT_STORE_NAME }
            DiagnosticsLogger.i("JahezBootstrap", "Step 3: Merchant profile does not exist yet. Creating merchants/$effectiveStoreId with storeName=$storeName, ownerUid=$uid...")
            val newStore = StoreRemoteProfile(
                storeId = effectiveStoreId,
                storeName = storeName,
                ownerUid = uid,
                ownerPhone = currentConfig.ownerPhone,
                whatsappNumber = currentConfig.whatsappNumber,
                status = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            val saveStoreResult = storeRemoteRepository.saveStoreProfile(newStore)
            if (saveStoreResult.isFailure) {
                val err = saveStoreResult.exceptionOrNull() ?: Exception("Failed to create merchant profile in Firestore")
                DiagnosticsLogger.e("JahezBootstrap", "Step 3 failed: ${err.message}", err)
                return Result.failure(err)
            }
            DiagnosticsLogger.i("JahezBootstrap", "Step 3 success: Created merchant profile in merchants/$effectiveStoreId")
        } else {
            DiagnosticsLogger.i("JahezBootstrap", "Step 3: Existing merchant profile found in merchants/$effectiveStoreId")
        }

        bootstrapped = true
        DiagnosticsLogger.i("JahezBootstrap", "Identity bootstrap completed successfully for storeId=$effectiveStoreId, uid=$uid")
        return Result.success(effectiveStoreId)
    }

    /**
     * Executes bootstrap first, then enqueues any un-synced local items and triggers full sync.
     */
    suspend fun ensureBootstrappedAndSync(): Result<SyncResult> {
        DiagnosticsLogger.i("JahezBootstrap", "ensureBootstrappedAndSync() triggered")
        val bootstrapResult = ensureBootstrapped()
        if (bootstrapResult.isFailure) {
            val err = bootstrapResult.exceptionOrNull() ?: Exception("Bootstrap failed prior to sync")
            DiagnosticsLogger.e("JahezBootstrap", "Bootstrap pre-requisite failed: ${err.message}", err)
            return Result.failure(err)
        }

        val storeId = bootstrapResult.getOrNull() ?: DEFAULT_STORE_ID

        // STEP 4: Product Sync
        // Enqueue any local products created prior to identity initialization & reset failed items
        DiagnosticsLogger.i("JahezBootstrap", "Step 4: Enqueueing local products for sync in storeId=$storeId...")
        productSyncManager.enqueueAllLocalProductsIfMissing(storeId)

        // Execute push and pull sync with retry reset
        DiagnosticsLogger.i("JahezBootstrap", "Step 4: Executing productSyncManager.syncAll()...")
        val syncResult = productSyncManager.syncAll(resetRetries = true)
        DiagnosticsLogger.i("JahezBootstrap", "Step 4 sync result: success=${syncResult.success}, pushed=${syncResult.itemsPushed}, pulled=${syncResult.itemsPulled}, error=${syncResult.errorMessage}")

        return Result.success(syncResult)
    }
}

