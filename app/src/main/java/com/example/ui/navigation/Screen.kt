package com.example.ui.navigation

sealed class Screen(val route: String) {
    object RoleSelection : Screen("role_selection")
    object MerchantLogin : Screen("merchant_login")
    object OwnerLogin : Screen("owner_login")
    object Home : Screen("home")
    object Products : Screen("products")
    object Orders : Screen("orders")
    object Suppliers : Screen("suppliers")
    object Settings : Screen("settings")
    object Diagnostics : Screen("diagnostics")
    object CreateOrder : Screen("create_order")
    object EditOrder : Screen("edit_order/{orderId}") {
        fun createRoute(orderId: Long): String = "edit_order/$orderId"
    }
}
