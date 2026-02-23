package org.nighthawklabs.homescanner.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.nighthawklabs.homescanner.App
import org.nighthawklabs.homescanner.ui.crop.CropScreen
import org.nighthawklabs.homescanner.ui.crop.CropViewModel
import org.nighthawklabs.homescanner.ui.home.HomeScreen
import org.nighthawklabs.homescanner.ui.home.HomeViewModel
import org.nighthawklabs.homescanner.ui.inventory.InventoryHomeScreen
import org.nighthawklabs.homescanner.ui.inventory.InventoryItemDetailsScreen
import org.nighthawklabs.homescanner.ui.inventory.InventoryItemDetailsViewModel
import org.nighthawklabs.homescanner.ui.inventory.InventoryViewModel
import org.nighthawklabs.homescanner.ui.receipt.ReceiptDetailsScreen
import org.nighthawklabs.homescanner.ui.receipt.ReceiptDetailsViewModel
import org.nighthawklabs.homescanner.ui.scan.ScanScreen
import org.nighthawklabs.homescanner.ui.scan.ScanViewModel

@Composable
fun ReceiptScannerNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as App).container
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Receipt, contentDescription = null) },
                    label = { Text("Receipts") },
                    selected = currentDestination?.route == "home" ||
                        currentDestination?.route?.startsWith("receipt/") == true ||
                        currentDestination?.route?.startsWith("scan") == true ||
                        currentDestination?.route?.startsWith("crop/") == true,
                    onClick = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Inventory, contentDescription = null) },
                    label = { Text("Inventory") },
                    selected = currentDestination?.route == "inventory" ||
                        currentDestination?.route?.startsWith("inventory/item/") == true,
                    onClick = {
                        navController.navigate("inventory") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
        composable("home") {
            val viewModel: HomeViewModel = viewModel(factory = appContainer.homeViewModelFactory())
            HomeScreen(
                viewModel = viewModel,
                onScanClick = { navController.navigate("scan") },
                onReceiptClick = { id -> navController.navigate("receipt/$id") }
            )
        }
        composable("scan") {
            val viewModel: ScanViewModel = viewModel(factory = appContainer.scanViewModelFactory())
            ScanScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCaptureComplete = { id ->
                    navController.navigate("receipt/$id") {
                        popUpTo("home") { inclusive = false }
                    }
                }
            )
        }
        composable(
            route = "receipt/{receiptId}",
            arguments = listOf(navArgument("receiptId") { type = NavType.StringType })
        ) { backStackEntry ->
            val receiptId = backStackEntry.arguments?.getString("receiptId") ?: return@composable
            val viewModel: ReceiptDetailsViewModel = viewModel(
                factory = appContainer.receiptDetailsViewModelFactory(receiptId)
            )
            ReceiptDetailsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditPage = { id, pageIdx ->
                    navController.navigate("crop/$id/$pageIdx") {
                        popUpTo("receipt/$receiptId") { inclusive = false }
                    }
                }
            )
        }
        composable(
            route = "crop/{receiptId}/{pageIndex}",
            arguments = listOf(
                navArgument("receiptId") { type = NavType.StringType },
                navArgument("pageIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val receiptId = backStackEntry.arguments?.getString("receiptId") ?: return@composable
            val pageIndex = backStackEntry.arguments?.getInt("pageIndex") ?: 0
            val viewModel: CropViewModel = viewModel(
                factory = appContainer.cropViewModelFactory(receiptId, pageIndex)
            )
            CropScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onCropped = { navController.popBackStack() }
            )
        }
        composable("inventory") {
            val viewModel: InventoryViewModel = viewModel(
                factory = appContainer.inventoryViewModelFactory()
            )
            InventoryHomeScreen(
                viewModel = viewModel,
                onItemClick = { itemId -> navController.navigate("inventory/item/$itemId") }
            )
        }
        composable(
            route = "inventory/item/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
            val viewModel: InventoryItemDetailsViewModel = viewModel(
                factory = appContainer.inventoryItemDetailsViewModelFactory(itemId)
            )
            InventoryItemDetailsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onReceiptClick = { receiptId ->
                    navController.navigate("receipt/$receiptId")
                }
            )
        }
    }
    }
}
