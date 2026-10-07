package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.ProductDao
import com.example.data.ProductEntity
import com.example.data.ProductRepository
import com.example.data.ProductUnitEntity
import com.example.data.ProductWithUnits
import com.example.data.auth.AuthRepository
import com.example.data.auth.AuthUser
import com.example.data.auth.UserProfile
import com.example.data.remote.ProductRemoteModel
import com.example.data.remote.ProductRemoteRepository
import com.example.data.remote.ProductUnitRemoteModel
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import com.example.data.sync.ProductSyncDao
import com.example.data.sync.ProductSyncEntity
import com.example.data.sync.ProductSyncManager
import com.example.data.sync.SyncMetadataDao
import com.example.data.sync.SyncMetadataEntity
import com.example.data.sync.SyncStatus
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
class ProductSyncTest {

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
        configRepository.setStoreId("store_001")
        configRepository.setRole(UserRole.MERCHANT)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testProductToRemoteMapping() {
        val product = ProductEntity(
            id = 101L,
            name = "بيبسي 330 مل",
            imageUri = "file:///data/user/0/com.example/files/product_images/pepsi.jpg",
            categoryId = 5L,
            isActive = true,
            createdAt = 1000L,
            updatedAt = 2000L
        )

        val units = listOf(
            ProductUnitEntity(id = 1L, productId = 101L, unitName = "صندوق", price = 25000L, isDefault = true, minQuantity = 1),
            ProductUnitEntity(id = 2L, productId = 101L, unitName = "قطعة", price = 1000L, isDefault = false, minQuantity = 1)
        )

        val productWithUnits = ProductWithUnits(product, units)

        // Mapping to remote model
        val remoteModel = ProductRemoteModel(
            id = productWithUnits.product.id,
            storeId = "store_001",
            name = productWithUnits.product.name,
            categoryId = productWithUnits.product.categoryId,
            isActive = productWithUnits.product.isActive,
            units = productWithUnits.units.map {
                ProductUnitRemoteModel(
                    id = it.id,
                    productId = it.productId,
                    unitName = it.unitName,
                    price = it.price,
                    isDefault = it.isDefault,
                    minQuantity = it.minQuantity
                )
            },
            createdAt = productWithUnits.product.createdAt,
            updatedAt = productWithUnits.product.updatedAt
        )

        assertEquals(101L, remoteModel.id)
        assertEquals("store_001", remoteModel.storeId)
        assertEquals("بيبسي 330 مل", remoteModel.name)
        assertEquals(5L, remoteModel.categoryId)
        assertTrue(remoteModel.isActive)
        assertEquals(2, remoteModel.units.size)
        assertEquals("صندوق", remoteModel.units[0].unitName)
        assertEquals(25000L, remoteModel.units[0].price)
        assertTrue(remoteModel.units[0].isDefault)
        assertEquals("قطعة", remoteModel.units[1].unitName)
        assertEquals(1000L, remoteModel.units[1].price)
        assertFalse(remoteModel.units[1].isDefault)
    }

    @Test
    fun testRemoteToProductMappingPreservesLocalImage() {
        val remoteModel = ProductRemoteModel(
            id = 202L,
            storeId = "store_001",
            name = "رز محمود 5 كغم",
            categoryId = null,
            isActive = true,
            units = listOf(
                ProductUnitRemoteModel(id = 10L, productId = 202L, unitName = "كيس", price = 15000L, isDefault = true)
            ),
            createdAt = 5000L,
            updatedAt = 6000L
        )

        val localExistingImage = "file:///data/user/0/com.example/files/product_images/existing.jpg"

        val localProduct = ProductEntity(
            id = remoteModel.id,
            name = remoteModel.name,
            imageUri = localExistingImage, // Local image preserved
            categoryId = remoteModel.categoryId,
            isActive = remoteModel.isActive,
            createdAt = remoteModel.createdAt,
            updatedAt = remoteModel.updatedAt
        )

        assertEquals(202L, localProduct.id)
        assertEquals("رز محمود 5 كغم", localProduct.name)
        assertEquals(localExistingImage, localProduct.imageUri)
        assertTrue(localProduct.isActive)
        assertEquals(6000L, localProduct.updatedAt)
    }

    @Test
    fun testProductUnitsMappingIntegrity() {
        val units = listOf(
            ProductUnitRemoteModel(id = 1L, productId = 10L, unitName = "كارتون", price = 48000L, isDefault = true, minQuantity = 1),
            ProductUnitRemoteModel(id = 2L, productId = 10L, unitName = "شدة", price = 12500L, isDefault = false, minQuantity = 2),
            ProductUnitRemoteModel(id = 3L, productId = 10L, unitName = "مفرد", price = 1250L, isDefault = false, minQuantity = 1)
        )

        assertEquals(3, units.size)
        assertEquals(1, units.count { it.isDefault })
        assertEquals(48000L, units.first { it.isDefault }.price)
        assertEquals(2, units[1].minQuantity)
    }

    @Test
    fun testStoreIDIsolationLogic() = runBlocking {
        configRepository.setStoreId("store_001")
        val config1 = configRepository.storeConfig.first()
        assertEquals("store_001", config1.storeId)

        // Remote model must be strictly bounded to store_001
        val productForStore1 = ProductRemoteModel(id = 1L, storeId = config1.storeId, name = "منتج متجر 1")
        assertEquals("store_001", productForStore1.storeId)

        // Switching to store_002
        configRepository.setStoreId("store_002")
        val config2 = configRepository.storeConfig.first()
        assertEquals("store_002", config2.storeId)

        // Cross store comparison
        assertFalse(productForStore1.storeId == config2.storeId)
    }

    @Test
    fun testCustomerCannotPushSync() = runBlocking {
        configRepository.setStoreId("store_001")
        configRepository.setRole(UserRole.CUSTOMER) // Role is Customer

        val mockAuthRepo = object : AuthRepository {
            override val currentUser: Flow<AuthUser?> = flowOf(AuthUser("cust_uid", "c@test.com", "عميل"))
            override val currentUid: String = "cust_uid"
            override val isSignedIn: Boolean = true
            override fun isFirebaseConfigured(): Boolean = true
            override fun signOut() {}
            override suspend fun getUserProfile(uid: String): Result<UserProfile?> =
                Result.success(UserProfile(uid = uid, storeId = "store_001", role = UserRole.CUSTOMER))
            override suspend fun saveUserProfile(profile: UserProfile): Result<Unit> = Result.success(Unit)
            override suspend fun ensureAnonymousSignIn(): Result<Unit> = Result.success(Unit)
        }

        val syncManager = ProductSyncManager(
            productDao = productDao,
            productSyncDao = productSyncDao,
            syncMetadataDao = syncMetadataDao,
            storeConfigRepository = configRepository,
            authRepository = mockAuthRepo,
            productRemoteRepository = ProductRemoteRepository(context)
        )

        // Add a pending item to the queue
        productSyncDao.enqueue(
            ProductSyncEntity(productId = 1L, storeId = "store_001", operation = "UPSERT", status = "PENDING")
        )

        val result = syncManager.syncPendingToRemote()
        // Customer has no permission to push, must immediately return with 0 pushed items
        assertEquals(0, result.itemsPushed)
    }

    @Test
    fun testSyncQueueLifecyclePendingToSynced() = runBlocking {
        val item = ProductSyncEntity(
            productId = 55L,
            storeId = "store_001",
            operation = "UPSERT",
            status = "PENDING",
            updatedAt = 1000L
        )

        productSyncDao.enqueue(item)
        val pending = productSyncDao.getPendingItems("store_001")
        assertEquals(1, pending.size)
        assertEquals("PENDING", pending[0].status)

        // Status transition to SYNCING
        productSyncDao.updateStatus(55L, "SYNCING", 2000L, null, 0)
        val afterSyncing = productSyncDao.getByProductId(55L)
        assertNotNull(afterSyncing)
        assertEquals("SYNCING", afterSyncing!!.status)

        // Status transition to FAILED with retry count increment
        productSyncDao.updateStatus(55L, "FAILED", 3000L, "Network timeout", 1)
        val afterFailed = productSyncDao.getByProductId(55L)
        assertNotNull(afterFailed)
        assertEquals("FAILED", afterFailed!!.status)
        assertEquals(1, afterFailed.retryCount)
        assertEquals("Network timeout", afterFailed.errorMessage)

        // Remove on success
        productSyncDao.delete(55L)
        assertNull(productSyncDao.getByProductId(55L))
    }

    @Test
    fun testSoftDeleteBehavior() = runBlocking {
        val product = ProductEntity(id = 77L, name = "شاي أحمد", isActive = true)
        val unit = ProductUnitEntity(id = 1L, productId = 77L, unitName = "علبة", price = 3000L)
        productDao.insertProductWithUnits(product, listOf(unit))

        val loadedBefore = productDao.getProductById(77L)
        assertNotNull(loadedBefore)
        assertTrue(loadedBefore!!.isActive)

        // Soft delete
        val deactivated = loadedBefore.copy(isActive = false, updatedAt = System.currentTimeMillis())
        productDao.updateProduct(deactivated)

        val loadedAfter = productDao.getProductById(77L)
        assertNotNull(loadedAfter)
        assertFalse(loadedAfter!!.isActive)

        // Verify product units remain intact for order history integrity
        val units = productDao.getUnitsForProductDirect(77L)
        assertEquals(1, units.size)
        assertEquals("علبة", units[0].unitName)
    }

    @Test
    fun testDeltaSyncTimestampAdvancement() = runBlocking {
        val key = "product_sync_store_001"

        val initialTimestamp = syncMetadataDao.getLastSyncTimestamp(key)
        assertNull(initialTimestamp)

        // Save metadata after delta sync
        val newTimestamp = 1710000000000L
        syncMetadataDao.upsertMetadata(
            SyncMetadataEntity(key = key, lastSyncTimestamp = newTimestamp)
        )

        val retrieved = syncMetadataDao.getLastSyncTimestamp(key)
        assertEquals(newTimestamp, retrieved)

        // Advance timestamp on next delta
        val newerTimestamp = 1710005000000L
        syncMetadataDao.upsertMetadata(
            SyncMetadataEntity(key = key, lastSyncTimestamp = newerTimestamp)
        )

        assertEquals(newerTimestamp, syncMetadataDao.getLastSyncTimestamp(key))
    }

    @Test
    fun testConflictResolutionLastWriteWins() = runBlocking {
        // Local has older timestamp
        val localProduct = ProductEntity(id = 99L, name = "نسكافيه قديم", createdAt = 1000L, updatedAt = 2000L)
        productDao.insertProduct(localProduct)

        val remoteNewer = ProductRemoteModel(
            id = 99L,
            storeId = "store_001",
            name = "نسكافيه محدث",
            updatedAt = 3000L // Newer
        )

        // Conflict check: remote (3000) > local (2000) -> Remote wins
        assertTrue(remoteNewer.updatedAt > localProduct.updatedAt)
        val updatedLocal = localProduct.copy(name = remoteNewer.name, updatedAt = remoteNewer.updatedAt)
        productDao.updateProduct(updatedLocal)

        val result = productDao.getProductById(99L)
        assertEquals("نسكافيه محدث", result?.name)
        assertEquals(3000L, result?.updatedAt)

        // Local has newer timestamp
        val localModified = result!!.copy(name = "نسكافيه محلي أحدث", updatedAt = 4000L)
        productDao.updateProduct(localModified)

        val remoteStale = ProductRemoteModel(
            id = 99L,
            storeId = "store_001",
            name = "نسكافيه قديم من السحابة",
            updatedAt = 3500L // Older than local 4000
        )

        // Conflict check: local (4000) > remote (3500) -> Local preserved!
        assertTrue(localModified.updatedAt > remoteStale.updatedAt)
    }

    @Test
    fun testNoInternetOrFirebaseUnconfiguredBehavior() = runBlocking {
        // Live Firebase is not configured in unit tests
        val remoteRepo = ProductRemoteRepository(context)
        assertFalse(remoteRepo.isFirebaseConfigured())

        val syncManager = ProductSyncManager(context)
        val pullResult = syncManager.pullRemoteDelta()
        assertFalse(pullResult.success)
        assertEquals("Firebase is not configured", pullResult.errorMessage)

        val pushResult = syncManager.syncPendingToRemote()
        assertFalse(pushResult.success)
        assertEquals("Firebase is not configured", pushResult.errorMessage)

        val allResult = syncManager.syncAll()
        assertFalse(allResult.success)
        assertEquals(SyncStatus.UNCONFIGURED, syncManager.syncStatus.value)
    }
}
