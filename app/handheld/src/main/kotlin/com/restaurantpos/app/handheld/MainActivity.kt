package com.restaurantpos.app.handheld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.restaurantpos.core.designsystem.PosTheme
import com.restaurantpos.feature.auth.PinLoginScreen
import com.restaurantpos.feature.order.OrderScreen
import com.restaurantpos.feature.tables.TablesScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                HandheldApp()
            }
        }
    }
}

@Composable
fun HandheldApp() {
    val navController = rememberNavController()
    val syncViewModel: SyncStatusViewModel = hiltViewModel()
    val syncState by syncViewModel.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        NavHost(navController = navController, startDestination = "login") {

            composable("login") {
                PinLoginScreen(
                    onLoginSuccess = {
                        navController.navigate("tables") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable("tables") {
                TablesScreen(
                    onTableSeated = { orderId -> navController.navigate("order/$orderId") },
                    onTableResumed = { orderId -> navController.navigate("order/$orderId") },
                    pendingSyncCount = syncState.pendingCount,
                    isOnline = syncState.isOnline,
                )
            }

            composable(
                route = "order/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            ) {
                // Handheld: after placing the order (firing kitchen tickets), go back to tables.
                // No on-device checkout — cashier terminal handles payment.
                OrderScreen(
                    onOrderPlaced = {
                        navController.navigate("tables") {
                            popUpTo("tables") { inclusive = true }
                        }
                    },
                    onOrderHeld = {
                        navController.navigate("tables") {
                            popUpTo("tables") { inclusive = true }
                        }
                    },
                    onStartNewOrder = {
                        // Safety net: Checkout & Pay is wired to the same QSR inline-payment
                        // flow on every OrderScreen caller (handheld included), even though
                        // handheld's FSR design intends "no on-device checkout". Until that's
                        // properly split, at least don't strand the waiter on a CLOSED order.
                        navController.navigate("tables") {
                            popUpTo("tables") { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
