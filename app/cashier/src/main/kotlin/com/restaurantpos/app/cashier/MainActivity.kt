package com.restaurantpos.app.cashier

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Percent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import com.restaurantpos.core.designsystem.PosContentBg
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.restaurantpos.core.designsystem.PosHairline
import com.restaurantpos.core.designsystem.PosShellBg
import com.restaurantpos.core.designsystem.PosTextPrimary
import com.restaurantpos.core.designsystem.PosTextSecondary
import com.restaurantpos.core.designsystem.PosTheme
import com.restaurantpos.core.designsystem.SunmiOrange
import com.restaurantpos.core.designsystem.component.OfflineBanner
import com.restaurantpos.feature.auth.PinLoginScreen
import com.restaurantpos.feature.checkout.CheckoutScreen
import com.restaurantpos.feature.crm.CustomersScreen
import com.restaurantpos.feature.menu.MenuScreen
import com.restaurantpos.feature.order.OrderScreen
import com.restaurantpos.feature.report.OrderHistoryScreen
import com.restaurantpos.feature.report.ShiftReportScreen
import com.restaurantpos.feature.settings.PadConfigScreen
import com.restaurantpos.feature.settings.SettingsScreen
import com.restaurantpos.feature.settings.TicketConfigScreen
import com.restaurantpos.feature.tables.TablesScreen
import com.restaurantpos.feature.tables.TakeawayQueueScreen
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withCashierLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                CashierApp()
            }
        }
    }
}

@Composable
fun CashierApp() {
    val navController = rememberNavController()
    val syncVm: SyncStatusViewModel = hiltViewModel()
    val syncState by syncVm.uiState.collectAsState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        // Keep all content inside the system bars (status bar + bottom gesture-nav bar),
        // so bottom action buttons (Send to Kitchen / Checkout) aren't covered or eaten by
        // the navigation inset on devices with a bottom gesture bar.
        Column(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        // Global offline indicator (visible on every screen while disconnected)
        OfflineBanner(isOnline = syncState.isOnline, pendingCount = syncState.pendingCount)
        // Tableside PAD waiter-call notification bar (always visible when calls pending)
        WaiterCallBar()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        Row(modifier = Modifier.fillMaxSize().weight(1f)) {
        // Persistent left navigation on every screen except the login gate.
        if (currentRoute != null && currentRoute != "login") {
            PosSidebar(
                active = activeDestinationFor(currentRoute),
                onSelect = { dest -> onSidebarSelect(navController, currentRoute, dest) },
                isOnline = syncState.isOnline,
            )
            VerticalDivider(color = PosHairline)
        }
        NavHost(
            navController = navController,
            startDestination = "login",
            modifier = Modifier.weight(1f),
        ) {

            composable("login") {
                PinLoginScreen(
                    onLoginSuccess = {
                        navController.navigate("tables") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            // QSR: create standalone order and navigate to POS
            composable("qsr-start") {
                val qsrVm: QsrStarterViewModel = hiltViewModel()
                val orderId by qsrVm.orderCreated.collectAsState()
                LaunchedEffect(Unit) { qsrVm.startNewOrder() }
                LaunchedEffect(orderId) {
                    orderId?.let { id ->
                        navController.navigate("order/$id") {
                            popUpTo("qsr-start") { inclusive = true }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().background(PosContentBg), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SunmiOrange)
                }
            }

            composable("tables") {
                TablesScreen(
                    onTableSeated = { orderId -> navController.navigate("order/$orderId") },
                    onTableResumed = { orderId -> navController.navigate("order/$orderId") },
                    onNavigateToReport = { navController.navigate("report") },
                    onNavigateToSettings = { navController.navigate("settings") },
                    onNavigateToMenu = { navController.navigate("menu") },
                    onNavigateToTakeaway = { navController.navigate("takeaway") },
                    pendingSyncCount = syncState.pendingCount,
                    isOnline = syncState.isOnline,
                )
            }

            composable(
                route = "order/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            ) {
                OrderScreen(
                    onOrderPlaced = { orderId ->
                        navController.navigate("checkout/$orderId") {
                            popUpTo("order/$orderId") { inclusive = true }
                        }
                    },
                    onOrderHeld = {
                        // Held the order — go take the next walk-in (mirrors the POS sidebar tap).
                        navController.navigate("qsr-start") {
                            popUpTo("tables")
                        }
                    },
                    onStartNewOrder = {
                        // QSR payment success dismissed — must land on a FRESH order, not the
                        // now-CLOSED one (mirrors the POS sidebar tap / onOrderHeld above).
                        navController.navigate("qsr-start") {
                            popUpTo("tables")
                        }
                    },
                )
            }

            composable(
                route = "checkout/{orderId}",
                arguments = listOf(navArgument("orderId") { type = NavType.StringType }),
            ) {
                CheckoutScreen(
                    onPaymentComplete = {
                        navController.navigate("tables") {
                            popUpTo("tables") { inclusive = true }
                        }
                    },
                )
            }

            composable("report") {
                ShiftReportScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToHistory = { navController.navigate("order-history") },
                )
            }

            composable("order-history") {
                OrderHistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOrderTap = { orderId -> navController.navigate("checkout/$orderId") },
                )
            }

            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToTicketConfig = { navController.navigate("ticket-config") },
                    onNavigateToPadConfig = { navController.navigate("pad-config") },
                )
            }

            composable("ticket-config") {
                TicketConfigScreen(onBack = { navController.popBackStack() })
            }

            composable("pad-config") {
                PadConfigScreen(onBack = { navController.popBackStack() })
            }

            composable("menu") {
                MenuScreen(onBack = { navController.popBackStack() })
            }

            composable("takeaway") {
                TakeawayQueueScreen(
                    onBack = { navController.popBackStack() },
                    onOrderTap = { orderId -> navController.navigate("checkout/$orderId") },
                )
            }

            composable("discounts") { DiscountsScreen(onBack = { navController.popBackStack() }) }
            composable("customers") { CustomersScreen() }
        }
        } // end Row
        } // end Column
    }
}

/** Maps the current nav route to the sidebar destination that should appear active. */
private fun activeDestinationFor(route: String): PosDestination = when {
    route.startsWith("order/") || route.startsWith("checkout/") -> PosDestination.POS
    route == "tables" -> PosDestination.TABLES
    route == "order-history" || route == "takeaway" -> PosDestination.ORDERS
    route == "customers" -> PosDestination.CUSTOMERS
    route == "menu" -> PosDestination.MENU
    route == "discounts" -> PosDestination.DISCOUNTS
    route == "report" -> PosDestination.REPORTS
    route.startsWith("settings") || route == "ticket-config" || route == "pad-config" -> PosDestination.SETTINGS
    else -> PosDestination.POS
}

/**
 * Sidebar navigation. POS opens the order-taking flow; with no active order it routes to
 * Tables (where an order is started). Top-level destinations use single-top + popUpTo so the
 * back stack stays shallow.
 */
private fun onSidebarSelect(nav: NavHostController, currentRoute: String, dest: PosDestination) {
    val target = when (dest) {
        PosDestination.POS ->
            if (currentRoute.startsWith("order/") || currentRoute.startsWith("checkout/")) return else "qsr-start"
        else -> dest.route
    }
    nav.navigate(target) {
        launchSingleTop = true
        popUpTo("tables")
    }
}

@Composable
private fun DiscountsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(PosContentBg)) {
        Row(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.discounts_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
        }
        HorizontalDivider(color = PosHairline)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Percent, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.discounts_management_title), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.discounts_management_hint), fontSize = 14.sp, color = PosTextSecondary)
            }
        }
    }
}

private fun Context.withCashierLocale(): Context {
    val locale = Locale.SIMPLIFIED_CHINESE
    Locale.setDefault(locale)
    val localized = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(localized)
}
