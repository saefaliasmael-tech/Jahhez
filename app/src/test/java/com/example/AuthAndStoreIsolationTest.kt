package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthUser
import com.example.data.auth.FirebaseAuthRepository
import com.example.data.auth.UserProfile
import com.example.data.remote.StoreRemoteProfile
import com.example.data.remote.StoreRemoteRepository
import com.example.data.store.StoreConfig
import com.example.data.store.StoreConfigRepository
import com.example.data.store.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AuthAndStoreIsolationTest {

    private lateinit var context: Context
    private lateinit var configRepository: StoreConfigRepository

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext<Context>()
        configRepository = StoreConfigRepository(context)
        configRepository.clear()
    }

    @Test
    fun testStoreIdAndUserUidStrictSeparation() = runBlocking {
        val userUid = "firebase_user_abc_123"
        val storeId = "store_baghdad_wholesaler_001"

        configRepository.setStoreId(storeId)
        configRepository.setFirebaseUid(userUid)

        val config = configRepository.storeConfig.first()
        // UID and storeId are fundamentally separate concepts
        assertNotEquals(config.storeId, config.firebaseUid)
        assertEquals(storeId, config.storeId)
        assertEquals(userUid, config.firebaseUid)

        // Updating store details MUST NOT alter userUid or storeId
        configRepository.updateStoreDetails(
            storeName = "مخازن الأمانة",
            ownerPhone = "07701112233",
            whatsappNumber = "07801112233"
        )

        val updatedConfig = configRepository.storeConfig.first()
        assertEquals(storeId, updatedConfig.storeId)
        assertEquals(userUid, updatedConfig.firebaseUid)
        assertEquals("مخازن الأمانة", updatedConfig.storeName)
    }

    @Test
    fun testUserProfileModelMappingAndValidation() {
        val profile = UserProfile(
            uid = "uid_customer_99",
            storeId = "store_001",
            role = UserRole.CUSTOMER,
            displayName = "أحمد التاجر",
            phoneNumber = "07709876543"
        )

        assertEquals("uid_customer_99", profile.uid)
        assertEquals("store_001", profile.storeId)
        assertEquals(UserRole.CUSTOMER, profile.role)
        assertEquals("أحمد التاجر", profile.displayName)
        assertEquals("07709876543", profile.phoneNumber)
        assertTrue(profile.createdAt > 0)
    }

    @Test
    fun testStoreRemoteProfileValidation() {
        val storeProfile = StoreRemoteProfile(
            storeId = "store_002",
            storeName = "الشركة الذهبية",
            ownerUid = "uid_merchant_77",
            ownerPhone = "07712345678",
            whatsappNumber = "07812345678",
            status = "ACTIVE"
        )

        assertEquals("store_002", storeProfile.storeId)
        assertEquals("الشركة الذهبية", storeProfile.storeName)
        assertEquals("uid_merchant_77", storeProfile.ownerUid)
        assertEquals("ACTIVE", storeProfile.status)
    }

    @Test
    fun testAuthRepositoryGracefulHandlingWhenFirebaseNotConfigured() = runBlocking {
        val authRepo = FirebaseAuthRepository(context)

        // When google-services.json is not initialized in test/runtime, repository must NOT crash
        assertNull(authRepo.currentUid)
        assertFalse(authRepo.isSignedIn)

        val result = authRepo.getUserProfile("test_uid")
        // Returns safe failure or handled error without throwing unhandled exception
        assertTrue(result.isFailure)

        val saveResult = authRepo.saveUserProfile(
            UserProfile(uid = "test_uid", storeId = "store_01", role = UserRole.MERCHANT)
        )
        assertTrue(saveResult.isFailure)

        // SignOut should not throw exception even when unconfigured
        authRepo.signOut()
    }

    @Test
    fun testStoreRemoteRepositoryRejectsEmptyStoreId() = runBlocking {
        val remoteRepo = StoreRemoteRepository(context)
        val invalidProfile = StoreRemoteProfile(storeId = "")

        val result = remoteRepo.saveStoreProfile(invalidProfile)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testRoleEnumIntegrity() {
        // Must contain strictly MERCHANT and CUSTOMER without OWNER or ADMIN at this stage
        val roles = UserRole.values()
        assertEquals(2, roles.size)
        assertTrue(roles.contains(UserRole.MERCHANT))
        assertTrue(roles.contains(UserRole.CUSTOMER))
    }

    @Test
    fun testStoreSettingsViewModelFactoryCreation() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val viewModel = factory.create(com.example.ui.viewmodel.StoreSettingsViewModel::class.java)
        assertNotNull(viewModel)
        assertNotNull(viewModel.storeConfig.value)
    }
}
