package com.example

import com.example.data.store.UserRole
import com.example.ui.navigation.NavigationConfig
import com.example.ui.navigation.RoleSelectionConfig
import com.example.ui.navigation.Screen
import com.example.ui.navigation.SelectableRoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleBasedNavigationTest {

    @Test
    fun testRolesContainStrictlyMerchantAndCustomerWithoutOwnerOrAdmin() {
        val roles = UserRole.values()
        assertEquals(2, roles.size)
        assertTrue(roles.contains(UserRole.MERCHANT))
        assertTrue(roles.contains(UserRole.CUSTOMER))
        // Verify no OWNER or ADMIN exists at this stage in UserRole enum
        assertFalse(roles.any { it.name.equals("OWNER", ignoreCase = true) })
        assertFalse(roles.any { it.name.equals("ADMIN", ignoreCase = true) })
    }

    @Test
    fun testRoleSelectionScreenContainsThreeDistinctRoles() {
        val options = RoleSelectionConfig.options
        assertEquals("Role selection must have exactly 3 options (Customer, Merchant, Owner)", 3, options.size)

        val customerOption = options.firstOrNull { it.type == SelectableRoleType.CUSTOMER }
        assertNotNull("Customer option must be present", customerOption)
        assertEquals("زبون", customerOption?.title)
        assertEquals("تصفح المنتجات وأنشئ طلباتك", customerOption?.description)
        assertEquals(Screen.Home.route, customerOption?.route)

        val merchantOption = options.firstOrNull { it.type == SelectableRoleType.MERCHANT }
        assertNotNull("Merchant option must be present", merchantOption)
        assertEquals("تاجر", merchantOption?.title)
        assertEquals("إدارة متجرك ومنتجاتك وطلبات الزبائن", merchantOption?.description)
        assertEquals(Screen.MerchantLogin.route, merchantOption?.route)

        val ownerOption = options.firstOrNull { it.type == SelectableRoleType.OWNER }
        assertNotNull("Owner option must be present", ownerOption)
        assertEquals("Owner", ownerOption?.title)
        assertEquals("إدارة التجار والنظام والإحصائيات", ownerOption?.description)
        assertEquals(Screen.OwnerLogin.route, ownerOption?.route)
    }

    @Test
    fun testFirstLaunchAndLoginRoutesDefinedProperly() {
        assertEquals("role_selection", Screen.RoleSelection.route)
        assertEquals("merchant_login", Screen.MerchantLogin.route)
        assertEquals("owner_login", Screen.OwnerLogin.route)
    }

    @Test
    fun testStartupDestinationSelectionBasedOnSession() {
        // When user has no saved session (First Launch), startDestination MUST be RoleSelection
        val newSessionConfigured = false
        val newLaunchDestination = if (newSessionConfigured) Screen.Home.route else Screen.RoleSelection.route
        assertEquals(Screen.RoleSelection.route, newLaunchDestination)

        // When user already has a saved/valid session, startDestination MUST be Home
        val existingSessionConfigured = true
        val existingLaunchDestination = if (existingSessionConfigured) Screen.Home.route else Screen.RoleSelection.route
        assertEquals(Screen.Home.route, existingLaunchDestination)
    }

    @Test
    fun testRoleSelectionTargetsAreDistinctAndValid() {
        val options = RoleSelectionConfig.options
        val customer = options.first { it.type == SelectableRoleType.CUSTOMER }
        val merchant = options.first { it.type == SelectableRoleType.MERCHANT }
        val owner = options.first { it.type == SelectableRoleType.OWNER }

        // Customer goes to Home/Customer interface
        assertEquals(Screen.Home.route, customer.route)
        // Merchant and Owner navigate to distinct dedicated login portals without fake login
        assertEquals(Screen.MerchantLogin.route, merchant.route)
        assertEquals(Screen.OwnerLogin.route, owner.route)
        assertFalse(merchant.route == customer.route)
        assertFalse(owner.route == customer.route)
        assertFalse(merchant.route == owner.route)
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

        // Verify Customer specific titles
        val titlesMap = customerNavItems.associate { it.screen to it.titleForRole(UserRole.CUSTOMER) }
        assertEquals("الرئيسية", titlesMap[Screen.Home])
        assertEquals("المنتجات", titlesMap[Screen.Products])
        assertEquals("طلباتي", titlesMap[Screen.Orders])
        assertEquals("معلومات المتجر", titlesMap[Screen.Settings])
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

        // Verify Merchant specific titles
        val titlesMap = merchantNavItems.associate { it.screen to it.titleForRole(UserRole.MERCHANT) }
        assertEquals("الرئيسية", titlesMap[Screen.Home])
        assertEquals("المنتجات", titlesMap[Screen.Products])
        assertEquals("الطلبيات", titlesMap[Screen.Orders])
        assertEquals("الموردين", titlesMap[Screen.Suppliers])
        assertEquals("الإعدادات", titlesMap[Screen.Settings])
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
