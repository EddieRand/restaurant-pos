package com.restaurantpos.app.pad

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.app.pad.screens.*

@Composable
fun PadNavigation(viewModel: PadViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val cartTotal by viewModel.cartTotal.collectAsState()
    val cartCount by viewModel.cartItemCount.collectAsState()
    val canSubmit by viewModel.canSubmit.collectAsState()
    val visibleItems by viewModel.visibleMenuItems.collectAsState()

    when (state.screen) {
        PadScreen.LOADING -> PadLoadingScreen()
        PadScreen.TABLE_BINDING -> TableBindingScreen()
        PadScreen.AYCE_PACKAGE -> AycePackageScreen(
            packages = state.padConfig.aycePackages,
            onSelect = viewModel::selectAycePackage,
            onSkip = viewModel::skipAycePackage,
        )
        PadScreen.MENU -> MenuScreen(
            state = state,
            visibleItems = visibleItems,
            cartTotal = cartTotal,
            cartCount = cartCount,
            canSubmit = canSubmit,
            onCategorySelect = viewModel::selectCategory,
            onAddToCart = { item -> viewModel.addToCart(item) },
            onRemoveFromCart = viewModel::removeFromCart,
            onUpdateQty = viewModel::updateCartQty,
            onSubmit = viewModel::submitOrder,
            onCallWaiter = viewModel::callWaiter,
            onShowHistory = viewModel::showOrderHistory,
            onSwitchLocale = viewModel::switchLocale,
            onUserInteraction = viewModel::resetIdle,
        )
        PadScreen.ORDER_HISTORY -> OrderHistoryScreen(
            submittedRounds = state.submittedRounds,
            submittedItems = state.submittedItems,
            locale = state.activeLocale,
            onBack = viewModel::showMenu,
        )
        PadScreen.IDLE -> IdleScreen(
            promoLines = state.padConfig.idlePromoLines,
            tableDisplayName = state.padConfig.tableDisplayName,
            onWake = viewModel::resetIdle,
        )
    }
}
