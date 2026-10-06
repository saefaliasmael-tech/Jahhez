package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.OrderViewModel
import com.example.ui.viewmodel.ProductViewModel

@Composable
fun MainContainer() {
    val navController = rememberNavController()
    val productViewModel: ProductViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()

    val topLevelItems = listOf(
        Triple(Screen.Home, "الرئيسية", Icons.Default.Home),
        Triple(Screen.Products, "المنتجات", Icons.Default.Inventory2),
        Triple(Screen.Orders, "الطلبيات", Icons.Default.ListAlt),
        Triple(Screen.Suppliers, "الموردين", Icons.Default.LocalShipping),
        Triple(Screen.Settings, "الإعدادات", Icons.Default.Settings)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val isTopLevel = topLevelItems.any { it.first.route == currentRoute }

    if (isTopLevel && currentRoute != Screen.Home.route) {
        BackHandler {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    topLevelItems.forEach { (screen, title, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = title) },
                            label = { Text(title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToProducts = {
                            navController.navigate(Screen.Products.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToOrders = {
                            navController.navigate(Screen.Orders.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
                composable(Screen.Products.route) {
                    ProductsScreen(viewModel = productViewModel)
                }
                composable(Screen.Orders.route) {
                    OrdersScreen(
                        orderViewModel = orderViewModel,
                        onCreateNewOrder = {
                            navController.navigate(Screen.CreateOrder.route)
                        },
                        onOpenOrder = { orderId ->
                            navController.navigate(Screen.EditOrder.createRoute(orderId))
                        }
                    )
                }
                composable(Screen.CreateOrder.route) {
                    CreateEditOrderScreen(
                        orderViewModel = orderViewModel,
                        orderId = null,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToProducts = {
                            navController.navigate(Screen.Products.route)
                        }
                    )
                }
                composable(
                    route = Screen.EditOrder.route,
                    arguments = listOf(
                        navArgument("orderId") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val orderId = backStackEntry.arguments?.getLong("orderId") ?: 0L
                    CreateEditOrderScreen(
                        orderViewModel = orderViewModel,
                        orderId = orderId,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToProducts = {
                            navController.navigate(Screen.Products.route)
                        }
                    )
                }
                composable(Screen.Suppliers.route) {
                    SuppliersScreen()
                }
                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
