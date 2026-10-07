package com.example

import com.example.data.store.UserRole
import com.example.ui.navigation.NavigationConfig
import com.example.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleBasedNavigationTest {

    @Test
    fun testRolesContainStrictlyMerchantAndCustomerWithoutOwnerOrAdmin() {
        val roles = UserRole.values()
        assertEquals(2, roles.size)
        assertTrue(roles.contains(UserRole.MERCHANT))
        assertTrue(roles.contains(UserRole.CUSTOMER))
        // Verify no OWNER or ADMIN exists at this stage
        assertFalse(roles.any { it.name.equals("OWNER", ignoreCase = true) })
        assertFalse(roles.any { it.name.equals("ADMIN", ignoreCase = true) })
    }

    @Test
    fun testCustomerNavigationExcludesSuppliersAndAdministrativeSections() {
        val customerNavItems = NavigationConfig.getTopLevelItemsForRole(UserRole.CUSTOMER)
        val customerScreens = customerNavItems.map { it.screen }

        // Customer receives Home, Products, Orders, Settings
        assertEquals(4, customerNavItems.size)
        assertTrue(customerScreens.contains(Screen.Home))
        assertTrue(customerScreens.contains(Screen.Products))
        assertTrue(customerScreens.contains(Screen.Orders))
        assertTrue(customerScreens.contains(Screen.Settings))

        // Suppliers is an administrative section and MUST be excluded for Customer
        assertFalse(customerScreens.contains(Screen.Suppliers))
    }

    @Test
    fun testMerchantNavigationIncludesAllSectionsIncludingSuppliers() {
        val merchantNavItems = NavigationConfig.getTopLevelItemsForRole(UserRole.MERCHANT)
        val merchantScreens = merchantNavItems.map { it.screen }

        // Merchant receives all 5 sections
        assertEquals(5, merchantNavItems.size)
        assertTrue(merchantScreens.contains(Screen.Home))
        assertTrue(merchantScreens.contains(Screen.Products))
        assertTrue(merchantScreens.contains(Screen.Orders))
        assertTrue(merchantScreens.contains(Screen.Suppliers))
        assertTrue(merchantScreens.contains(Screen.Settings))
    }

    @Test
    fun testRoutePermissionCheckByRole() {
        // Suppliers route allowed for MERCHANT, forbidden for CUSTOMER
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Suppliers.route, UserRole.MERCHANT))
        assertFalse(NavigationConfig.isRouteAllowedForRole(Screen.Suppliers.route, UserRole.CUSTOMER))

        // General routes allowed for both
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Home.route, UserRole.CUSTOMER))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Products.route, UserRole.CUSTOMER))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Orders.route, UserRole.CUSTOMER))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Settings.route, UserRole.CUSTOMER))

        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Home.route, UserRole.MERCHANT))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Products.route, UserRole.MERCHANT))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Orders.route, UserRole.MERCHANT))
        assertTrue(NavigationConfig.isRouteAllowedForRole(Screen.Settings.route, UserRole.MERCHANT))
    }
}
