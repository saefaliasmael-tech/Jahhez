package com.example.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.storeConfigDataStore: DataStore<Preferences> by preferencesDataStore(name = "store_config")

class StoreConfigRepository(private val dataStore: DataStore<Preferences>) {

    companion object {
        val KEY_STORE_ID = stringPreferencesKey("store_id")
        val KEY_STORE_NAME = stringPreferencesKey("store_name")
        val KEY_ROLE = stringPreferencesKey("user_role")
        val KEY_OWNER_PHONE = stringPreferencesKey("owner_phone")
        val KEY_WHATSAPP_NUMBER = stringPreferencesKey("whatsapp_number")
        val KEY_FIREBASE_UID = stringPreferencesKey("firebase_uid")
    }

    constructor(context: Context) : this(context.storeConfigDataStore)

    val storeConfig: Flow<StoreConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val storeId = preferences[KEY_STORE_ID] ?: ""
            val storeName = preferences[KEY_STORE_NAME] ?: StoreConfig.DEFAULT_STORE_NAME
            val roleStr = preferences[KEY_ROLE] ?: UserRole.MERCHANT.name
            val role = try {
                UserRole.valueOf(roleStr)
            } catch (e: IllegalArgumentException) {
                UserRole.MERCHANT
            }
            val ownerPhone = preferences[KEY_OWNER_PHONE] ?: ""
            val whatsappNumber = preferences[KEY_WHATSAPP_NUMBER] ?: ""
            val firebaseUid = preferences[KEY_FIREBASE_UID]

            StoreConfig(
                storeId = storeId,
                storeName = storeName,
                role = role,
                ownerPhone = ownerPhone,
                whatsappNumber = whatsappNumber,
                firebaseUid = firebaseUid
            )
        }

    val storeId: Flow<String> = storeConfig.map { it.storeId }
    val storeName: Flow<String> = storeConfig.map { it.storeName }
    val role: Flow<UserRole> = storeConfig.map { it.role }
    val firebaseUid: Flow<String?> = storeConfig.map { it.firebaseUid }

    suspend fun updateStoreDetails(
        storeName: String,
        ownerPhone: String,
        whatsappNumber: String
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_STORE_NAME] = storeName.trim().ifBlank { StoreConfig.DEFAULT_STORE_NAME }
            preferences[KEY_OWNER_PHONE] = ownerPhone.trim()
            preferences[KEY_WHATSAPP_NUMBER] = whatsappNumber.trim()
        }
    }

    suspend fun updateStoreName(storeName: String) {
        dataStore.edit { preferences ->
            preferences[KEY_STORE_NAME] = storeName.trim().ifBlank { StoreConfig.DEFAULT_STORE_NAME }
        }
    }

    suspend fun setStoreId(storeId: String) {
        dataStore.edit { preferences ->
            preferences[KEY_STORE_ID] = storeId.trim()
        }
    }

    suspend fun setRole(role: UserRole) {
        dataStore.edit { preferences ->
            preferences[KEY_ROLE] = role.name
        }
    }

    suspend fun setFirebaseUid(uid: String?) {
        dataStore.edit { preferences ->
            if (uid.isNullOrBlank()) {
                preferences.remove(KEY_FIREBASE_UID)
            } else {
                preferences[KEY_FIREBASE_UID] = uid.trim()
            }
        }
    }

    suspend fun clear() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
