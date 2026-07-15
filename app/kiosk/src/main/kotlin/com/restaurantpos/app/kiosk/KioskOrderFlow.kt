package com.restaurantpos.app.kiosk

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.restaurantpos.feature.order.OrderScreen

/**
 * Minimal single-destination NavHost that supplies the orderId via SavedStateHandle
 * so that OrderViewModel can pick it up correctly.
 */
@Composable
fun KioskOrderFlow(
    orderId: String,
    onOrderPlaced: (orderId: String) -> Unit,
    onStartNewOrder: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "order/$orderId",
    ) {
        composable(
            route = "order/{orderId}",
            arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
        ) {
            OrderScreen(
                onOrderPlaced = onOrderPlaced,
                showStaffActions = false,
                onStartNewOrder = onStartNewOrder,
            )
        }
    }
}
