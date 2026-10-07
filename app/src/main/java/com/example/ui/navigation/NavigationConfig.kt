package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.data.store.UserRole

/**
 * Metadata definition for a top-level destination in the bottom navigation bar.
 *
 * @param screen The associated Screen destination.
 * @param title The title displayed for this destination.
 * @param icon The icon displayed for this destination.
 * @param allowedRoles The set of UserRoles authorized to view this destination.
 */
data class NavigationItem(
    val screen: Screen,
    val title: String,
    val icon: ImageVector,
    val allowedRoles: Set<UserRole>
)

object NavigationConfig {

    /**
     * Centralized definition of all top-level destinations with their role access rules.
     * Ready for future extensions (such as OWNER or other roles) without rewriting navigation logic.
     */
    val allTopLevelItems: List<NavigationItem> = listOf(
        NavigationItem(
            screen = Screen.Home,
            title = "الرئيسية",
            icon = Icons.Default.Home,
            allowedRoles = setOf(UserRole.MERCHANT, UserRole.CUSTOMER)
        ),
        NavigationItem(
            screen = Screen.Products,
            title = "المنتجات",
            icon = Icons.Default.Inventory2,
            allowedRoles = setOf(UserRole.MERCHANT, UserRole.CUSTOMER)
        ),
        NavigationItem(
            screen = Screen.Orders,
            title = "الطلبيات",
            icon = Icons.Default.ListAlt,
            allowedRoles = setOf(UserRole.MERCHANT, UserRole.CUSTOMER)
        ),
        NavigationItem(
            screen = Screen.Suppliers,
            title = "الموردين",
            icon = Icons.Default.LocalShipping,
            allowedRoles = setOf(UserRole.MERCHANT) // Admin/management only, strictly hidden for CUSTOMER
        ),
        NavigationItem(
            screen = Screen.Settings,
            title = "الإعدادات",
            icon = Icons.Default.Settings,
            allowedRoles = setOf(UserRole.MERCHANT, UserRole.CUSTOMER)
        )
    )

    /**
     * Filters top-level items dynamically according to the current [UserRole].
     * MERCHANT receives both customer-facing and merchant-specific management sections.
     * CUSTOMER receives only customer-allowed sections with management sections hidden.
     */
    fun getTopLevelItemsForRole(role: UserRole): List<NavigationItem> {
        return allTopLevelItems.filter { item ->
            role in item.allowedRoles
        }
    }

    /**
     * Checks if a given route is accessible for a specified [UserRole].
     */
    fun isRouteAllowedForRole(route: String?, role: UserRole): Boolean {
        if (route == null) return true
        val matchedItem = allTopLevelItems.firstOrNull { it.screen.route == route } ?: return true
        return role in matchedItem.allowedRoles
    }
}
