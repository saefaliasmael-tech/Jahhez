package com.example.data.sync

enum class SyncStatus {
    IDLE,
    SYNCING,
    SYNCED,
    PENDING,
    FAILED,
    UNCONFIGURED
}

data class SyncResult(
    val success: Boolean,
    val itemsPushed: Int = 0,
    val itemsPulled: Int = 0,
    val errorMessage: String? = null
)
