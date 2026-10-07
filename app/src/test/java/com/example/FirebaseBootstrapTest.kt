package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProductDao
import com.example.data.ProductEntity
import com.example.data.ProductUnitEntity
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUser
import com.example.data.auth.UserProfile
import com.example.data.remote.FirebaseBootstrapManager
import com.example.data.remote.ProductRemoteModel
import com.example.data.remote.ProductRemoteRepository
import com.example.data.remote.StoreRemoteProfile
import com.example.data.remote.StoreRemoteRepository
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import com.example.data.sync.ProductSyncDao
import com.example.data.sync.ProductSyncManager
import com.example.data.sync.SyncMetadataDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseBootstrapTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var productDao: ProductDao
    private lateinit var productSyncDao: ProductSyncDao
    private lateinit var syncMetadataDao: SyncMetadataDao
    private lateinit var configRepository: StoreConfigRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = db.productDao()
        productSyncDao = db.productSyncDao()
        syncMetadataDao = db.syncMetadataDao()

        configRepository = StoreConfigRepository(context)
        configRepository.clear()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // Mock implementations to test strict execution order and payloads
    private class TestAuthRepository(
        var firebaseConfigured: Boolean = true,
        var currentUidValue: String? = null
    ) : AuthRepository {
        val callOrder = mutableListOf<String>()
        val savedProfiles = mutableMapOf<String, UserProfile>()

        override val currentUser: Flow<AuthUser?> = flowOf(null)
        override val currentUid: String?
            get() = currentUidValue
        override val isSignedIn: Boolean
            get() = currentUidValue != null

        override fun isFirebaseConfigured(): Boolean = firebaseConfigured
        override fun signOut() {
            currentUidValue = null
        }

        override suspend fun getUserProfile(uid: String): Result<UserProfile?> {
            callOrder.add("getUserProfile:$uid")
            return Result.success(savedProfiles[uid])
        }

        override suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
            callOrder.add("saveUserProfile:${profile.uid}:${profile.storeId}:${profile.role}")
            savedProfiles[profile.uid] = profile
            return Result.success(Unit)
        }

        override suspend fun ensureAnonymousSignIn(): Result<Unit> {
            callOrder.add("ensureAnonymousSignIn")
            if (currentUidValue == null) {
                currentUidValue = "anon_uid_test_123"
            }
            return Result.success(Unit)
        }
    }

    private class TestStoreRemoteRepository(
        context: Context,
        var firebaseConfigured: Boolean = true
    ) : StoreRemoteRepository(context) {
        val callOrder = mutableListOf<String>()
        val savedStores = mutableMapOf<String, StoreRemoteProfile>()

        override fun isFirebaseConfigured(): Boolean = firebaseConfigured

        override suspend fun getStoreProfile(storeId: String): Result<StoreRemoteProfile?> {
            callOrder.add("getStoreProfile:$storeId")
            return Result.success(savedStores[storeId])
        }

        override suspend fun saveStoreProfile(profile: StoreRemoteProfile): Result<Unit> {
            callOrder.add("saveStoreProfile:${profile.storeId}:${profile.ownerUid}")
            savedStores[profile.storeId] = profile
            return Result.success(Unit)
        }
    }

    private class TestProductRemoteRepository(
        context: Context,
        var firebaseConfigured: Boolean = true
    ) : ProductRemoteRepository(context) {
        val upsertedProducts = mutableListOf<ProductRemoteModel>()

        override fun isFirebaseConfigured(): Boolean = firebaseConfigured

        override suspend fun upsertProduct(
            storeId: String,
            product: ProductRemoteModel
        ): Result<Unit> {
            upsertedProducts.add(product)
            return Result.success(Unit)
        }

        override suspend fun getProductsDelta(
            storeId: String,
            sinceTimestamp: Long
        ): Result<List<ProductRemoteModel>> {
            return Result.success(emptyList())
        }
    }

    @Test
    fun testStrictSequentialOrderAndDocumentCreationOnFirstLaunch() = runBlocking {
        val mockAuth = TestAuthRepository(firebaseConfigured = true)
        val mockStoreRemote = TestStoreRemoteRepository(context, firebaseConfigured = true)
        val mockProductRemote = TestProductRemoteRepository(context, firebaseConfigured = true)

        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuth,
            productRemoteRepository = mockProductRemote
        )

        val bootstrapManager = FirebaseBootstrapManager(
            authRepository = mockAuth,
            storeRemoteRepository = mockStoreRemote,
            storeConfigRepository = configRepository,
            productSyncManager = syncManager
        )

        // Add a local product in Room beforehand
        val now = System.currentTimeMillis()
        val productId = productDao.insertProduct(
            ProductEntity(name = "سكر الأسرة 5 كغم", imageUri = null, createdAt = now, updatedAt = now)
        )
        productDao.insertProductUnit(
            ProductUnitEntity(productId = productId, unitName = "كيس", price = 6000L, isDefault = true)
        )

        // Execute Bootstrap & Sync
        val result = bootstrapManager.ensureBootstrappedAndSync()
        assertTrue(result.isSuccess)

        // Verify Step 1: Anonymous Auth was invoked first
        assertEquals("ensureAnonymousSignIn", mockAuth.callOrder[0])
        assertEquals("anon_uid_test_123", mockAuth.currentUid)

        // Verify Step 2: User profile checked and created in users/{uid}
        assertTrue(mockAuth.callOrder.contains("getUserProfile:anon_uid_test_123"))
        val createdUser = mockAuth.savedProfiles["anon_uid_test_123"]
        assertNotNull(createdUser)
        assertEquals("anon_uid_test_123", createdUser?.uid)
        assertEquals("store_default", createdUser?.storeId)
        assertEquals(UserRole.MERCHANT, createdUser?.role)

        // Verify Step 3: Merchant profile checked and created in merchants/store_default
        assertTrue(mockStoreRemote.callOrder.contains("getStoreProfile:store_default"))
        val createdStore = mockStoreRemote.savedStores["store_default"]
        assertNotNull(createdStore)
        assertEquals("store_default", createdStore?.storeId)
        assertEquals("anon_uid_test_123", createdStore?.ownerUid)
        assertEquals("ACTIVE", createdStore?.status)

        // Verify local store config updated
        val config = configRepository.storeConfig.first()
        assertEquals("store_default", config.storeId)
        assertEquals("anon_uid_test_123", config.firebaseUid)
        assertEquals(UserRole.MERCHANT, config.role)

        // Verify Step 4: Local product was enqueued and synced to Firestore
        assertEquals(1, mockProductRemote.upsertedProducts.size)
        assertEquals("سكر الأسرة 5 كغم", mockProductRemote.upsertedProducts[0].name)
        assertEquals("store_default", mockProductRemote.upsertedProducts[0].storeId)
    }

    @Test
    fun testIdempotenceWhenDocumentsAlreadyExistInFirestore() = runBlocking {
        val mockAuth = TestAuthRepository(firebaseConfigured = true, currentUidValue = "existing_uid_999")
        // Pre-populate existing user profile
        mockAuth.savedProfiles["existing_uid_999"] = UserProfile(
            uid = "existing_uid_999",
            storeId = "store_custom_1",
            role = UserRole.MERCHANT,
            displayName = "متجر النهرين"
        )

        val mockStoreRemote = TestStoreRemoteRepository(context, firebaseConfigured = true)
        // Pre-populate existing store profile
        mockStoreRemote.savedStores["store_custom_1"] = StoreRemoteProfile(
            storeId = "store_custom_1",
            storeName = "متجر النهرين",
            ownerUid = "existing_uid_999"
        )

        val mockProductRemote = TestProductRemoteRepository(context, firebaseConfigured = true)
        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuth,
            productRemoteRepository = mockProductRemote
        )

        val bootstrapManager = FirebaseBootstrapManager(
            authRepository = mockAuth,
            storeRemoteRepository = mockStoreRemote,
            storeConfigRepository = configRepository,
            productSyncManager = syncManager
        )

        val result = bootstrapManager.ensureBootstrapped()
        assertTrue(result.isSuccess)
        assertEquals("store_custom_1", result.getOrNull())

        // Ensure saveUserProfile and saveStoreProfile were NOT called since documents already existed
        assertFalse(mockAuth.callOrder.any { it.startsWith("saveUserProfile") })
        assertFalse(mockStoreRemote.callOrder.any { it.startsWith("saveStoreProfile") })
    }

    @Test
    fun testExistingLocalProductBeforeBootstrapIsAutomaticallySynced() = runBlocking {
        val mockAuth = TestAuthRepository(firebaseConfigured = true)
        val mockStoreRemote = TestStoreRemoteRepository(context, firebaseConfigured = true)
        val mockProductRemote = TestProductRemoteRepository(context, firebaseConfigured = true)

        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuth,
            productRemoteRepository = mockProductRemote
        )

        val bootstrapManager = FirebaseBootstrapManager(
            authRepository = mockAuth,
            storeRemoteRepository = mockStoreRemote,
            storeConfigRepository = configRepository,
            productSyncManager = syncManager
        )

        // 1. Local product existed in Room before Firebase Bootstrap
        val now = System.currentTimeMillis()
        val productId = productDao.insertProduct(
            ProductEntity(name = "منتج اختبار", imageUri = null, createdAt = now, updatedAt = now)
        )
        productDao.insertProductUnit(
            ProductUnitEntity(productId = productId, unitName = "قطعة", price = 1000L, isDefault = true)
        )

        // Verify product initially not in queue
        assertNull(productSyncDao.getByProductId(productId))

        // 2. Execute Bootstrap & Sync
        val syncResult = bootstrapManager.ensureBootstrappedAndSync()
        assertTrue(syncResult.isSuccess)
        assertTrue(syncResult.getOrNull()?.success == true)
        assertEquals(1, syncResult.getOrNull()?.itemsPushed)

        // 3. Verify remote repository received the write at merchants/store_default/products/{id}
        assertEquals(1, mockProductRemote.upsertedProducts.size)
        val pushed = mockProductRemote.upsertedProducts[0]
        assertEquals(productId, pushed.id)
        assertEquals("store_default", pushed.storeId)
        assertEquals("منتج اختبار", pushed.name)
        assertTrue(pushed.isActive)
        assertEquals(1, pushed.units.size)
        assertEquals("قطعة", pushed.units[0].unitName)
        assertEquals(1000L, pushed.units[0].price)

        // 4. Verify queue item was cleaned up after successful push and pending items is empty
        val pending = productSyncDao.getPendingItems("store_default")
        assertTrue(pending.isEmpty())
        assertEquals(com.example.data.sync.SyncStatus.SYNCED, syncManager.syncStatus.value)
    }

    @Test
    fun testExistingProductWithMaxRetriesIsResetAndSynced() = runBlocking {
        val mockAuth = TestAuthRepository(firebaseConfigured = true)
        val mockStoreRemote = TestStoreRemoteRepository(context, firebaseConfigured = true)
        val mockProductRemote = TestProductRemoteRepository(context, firebaseConfigured = true)

        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuth,
            productRemoteRepository = mockProductRemote
        )

        val bootstrapManager = FirebaseBootstrapManager(
            authRepository = mockAuth,
            storeRemoteRepository = mockStoreRemote,
            storeConfigRepository = configRepository,
            productSyncManager = syncManager
        )

        // Local product exists
        val now = System.currentTimeMillis()
        val productId = productDao.insertProduct(
            ProductEntity(name = "منتج قديم معلق", imageUri = null, createdAt = now, updatedAt = now)
        )
        productDao.insertProductUnit(
            ProductUnitEntity(productId = productId, unitName = "حبة", price = 2500L, isDefault = true)
        )

        // Previously failed with retryCount = 5 (PERMISSION_DENIED)
        productSyncDao.enqueue(
            com.example.data.sync.ProductSyncEntity(
                productId = productId,
                storeId = "store_default",
                operation = "UPSERT",
                status = "FAILED",
                retryCount = 5,
                errorMessage = "PERMISSION_DENIED",
                updatedAt = now
            )
        )

        // Execute Bootstrap & Sync
        val syncResult = bootstrapManager.ensureBootstrappedAndSync()
        assertTrue(syncResult.isSuccess)
        assertTrue(syncResult.getOrNull()?.success == true)
        assertEquals(1, syncResult.getOrNull()?.itemsPushed)

        // Verify it was NOT skipped and was successfully sent to Firestore
        assertEquals(1, mockProductRemote.upsertedProducts.size)
        assertEquals("منتج قديم معلق", mockProductRemote.upsertedProducts[0].name)
    }

    @Test
    fun testGracefulFailureWhenFirebaseUnconfigured() = runBlocking {
        val mockAuth = TestAuthRepository(firebaseConfigured = false)
        val mockStoreRemote = TestStoreRemoteRepository(context, firebaseConfigured = false)
        val mockProductRemote = TestProductRemoteRepository(context, firebaseConfigured = false)

        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuth,
            productRemoteRepository = mockProductRemote
        )

        val bootstrapManager = FirebaseBootstrapManager(
            authRepository = mockAuth,
            storeRemoteRepository = mockStoreRemote,
            storeConfigRepository = configRepository,
            productSyncManager = syncManager
        )

        val result = bootstrapManager.ensureBootstrapped()
        assertFalse(result.isSuccess)
    }
}
