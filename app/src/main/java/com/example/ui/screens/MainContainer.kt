package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.data.store.UserRole
import com.example.ui.navigation.NavigationConfig
import com.example.ui.navigation.Screen
import com.example.ui.viewmodel.OrderViewModel
import com.example.ui.viewmodel.ProductViewModel
import com.example.ui.viewmodel.StoreSettingsViewModel

@Composable
fun MainContainer() {
    val navController = rememberNavController()
    val productViewModel: ProductViewModel = viewModel()
    val orderViewModel: OrderViewModel = viewModel()
    val storeSettingsViewModel: StoreSettingsViewModel = viewModel()

    val storeConfig by storeSettingsViewModel.storeConfig.collectAsState()
    val currentRole = storeConfig.role

    // Reactive role-based top-level items (MERCHANT gets all, CUSTOMER gets filtered list)
    val topLevelItems = remember(currentRole) {
        NavigationConfig.getTopLevelItemsForRole(currentRole)
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    val isTopLevel = topLevelItems.any { it.screen.route == currentRoute }

    // Guard: If customer is somehow on a route not allowed for their role (e.g. Suppliers), navigate home
    LaunchedEffect(currentRoute, currentRole) {
        if (!NavigationConfig.isRouteAllowedForRole(currentRoute, currentRole)) {
            navController.navigate(Screen.Home.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

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
                    topLevelItems.forEach { navItem ->
                        NavigationBarItem(
                            icon = { Icon(navItem.icon, contentDescription = navItem.title) },
                            label = { Text(navItem.title) },
                            selected = currentRoute == navItem.screen.route,
                            onClick = {
                                if (currentRoute != navItem.screen.route) {
                                    navController.navigate(navItem.screen.route) {
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
                    // Suppliers is strictly for MERCHANT role; redirect CUSTOMER gracefully
                    if (currentRole == UserRole.MERCHANT) {
                        SuppliersScreen()
                    } else {
                        LaunchedEffect(Unit) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                            }
                        }
                    }
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = storeSettingsViewModel,
                        onNavigateToDiagnostics = {
                            navController.navigate(Screen.Diagnostics.route)
                        }
                    )
                }
                composable(Screen.Diagnostics.route) {
                    DiagnosticsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
