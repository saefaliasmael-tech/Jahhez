package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.store.StoreConfig
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StoreConfigRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: StoreConfigRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        repository = StoreConfigRepository(context)
        repository.clear()
    }

    @Test
    fun testDefaultValues() = runBlocking {
        val config = repository.storeConfig.first()
        assertEquals("", config.storeId)
        assertEquals(StoreConfig.DEFAULT_STORE_NAME, config.storeName)
        assertEquals("متجري", config.storeName)
        assertEquals(UserRole.MERCHANT, config.role)
        assertEquals("", config.ownerPhone)
        assertEquals("", config.whatsappNumber)
    }

    @Test
    fun testSaveAndReadStoreName() = runBlocking {
        repository.updateStoreName("أسواق الفرات للجملة")
        val config = repository.storeConfig.first()
        assertEquals("أسواق الفرات للجملة", config.storeName)
        assertEquals("أسواق الفرات للجملة", repository.storeName.first())
    }

    @Test
    fun testUpdateStoreNameFallbackWhenBlank() = runBlocking {
        repository.updateStoreName("   ")
        val config = repository.storeConfig.first()
        assertEquals("متجري", config.storeName)
    }

    @Test
    fun testSavePhoneAndWhatsapp() = runBlocking {
        repository.updateStoreDetails(
            storeName = "شركة بغداد",
            ownerPhone = "07701234567",
            whatsappNumber = "07801234567"
        )
        val config = repository.storeConfig.first()
        assertEquals("شركة بغداد", config.storeName)
        assertEquals("07701234567", config.ownerPhone)
        assertEquals("07801234567", config.whatsappNumber)
    }

    @Test
    fun testSaveRole() = runBlocking {
        assertEquals(UserRole.MERCHANT, repository.role.first())
        repository.setRole(UserRole.CUSTOMER)
        assertEquals(UserRole.CUSTOMER, repository.role.first())
        repository.setRole(UserRole.MERCHANT)
        assertEquals(UserRole.MERCHANT, repository.role.first())
    }

    @Test
    fun testSaveStoreId() = runBlocking {
        repository.setStoreId("store_baghdad_992")
        val id = repository.storeId.first()
        assertEquals("store_baghdad_992", id)
        val config = repository.storeConfig.first()
        assertEquals("store_baghdad_992", config.storeId)
    }

    @Test
    fun testStoreIdNeverModifiedByUpdateStoreDetails() = runBlocking {
        repository.setStoreId("store_unique_fixed_123")
        repository.updateStoreDetails(
            storeName = "متجر جديد",
            ownerPhone = "07770000000",
            whatsappNumber = "07880000000"
        )
        val config = repository.storeConfig.first()
        assertEquals("متجر جديد", config.storeName)
        assertEquals("store_unique_fixed_123", config.storeId)
    }

    @Test
    fun testPersistenceAcrossRepositoryRecreation() = runBlocking {
        repository.setStoreId("store_permanent_456")
        repository.setRole(UserRole.CUSTOMER)
        repository.updateStoreDetails(
            storeName = "متجر الأمل للمواد الغذائية",
            ownerPhone = "07901112233",
            whatsappNumber = "07904445566"
        )

        // Simulate app restart / recreation with a fresh repository instance
        val freshRepository = StoreConfigRepository(context)
        val freshConfig = freshRepository.storeConfig.first()

        assertEquals("store_permanent_456", freshConfig.storeId)
        assertEquals(UserRole.CUSTOMER, freshConfig.role)
        assertEquals("متجر الأمل للمواد الغذائية", freshConfig.storeName)
        assertEquals("07901112233", freshConfig.ownerPhone)
        assertEquals("07904445566", freshConfig.whatsappNumber)
    }

    @Test
    fun testSaveAndReadFirebaseUid() = runBlocking {
        assertEquals(null, repository.firebaseUid.first())
        repository.setFirebaseUid("firebase_user_7788")
        assertEquals("firebase_user_7788", repository.firebaseUid.first())
        assertEquals("firebase_user_7788", repository.storeConfig.first().firebaseUid)

        repository.setFirebaseUid(null)
        assertEquals(null, repository.firebaseUid.first())
    }

    @Test
    fun testHasConfiguredSessionLifecycle() = runBlocking {
        // Initially after clear()
        assertEquals(false, repository.hasConfiguredSession.first())

        // Once a role is selected/saved
        repository.setRole(UserRole.CUSTOMER)
        assertEquals(true, repository.hasConfiguredSession.first())

        // When cleared again
        repository.clear()
        assertEquals(false, repository.hasConfiguredSession.first())

        // Saving MERCHANT role marks session configured as well
        repository.setRole(UserRole.MERCHANT)
        assertEquals(true, repository.hasConfiguredSession.first())
    }
}
