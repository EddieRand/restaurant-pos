package com.restaurantpos.app.kiosk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.designsystem.PosTheme
import com.restaurantpos.feature.order.OrderScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PosTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val viewModel: KioskViewModel = hiltViewModel()
                    val uiState by viewModel.uiState.collectAsState()
                    var showMemberLookup by remember { mutableStateOf(false) }

                    if (showMemberLookup) {
                        MemberLookupScreen(onBack = { showMemberLookup = false })
                    } else {
                        Box(Modifier.fillMaxSize()) {
                            when {
                                uiState.isLoading -> {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator()
                                    }
                                }

                                uiState.confirmedOrderId != null -> {
                                    OrderConfirmationScreen(
                                        orderId = uiState.confirmedOrderId!!,
                                        autoReturnCountdown = uiState.autoReturnCountdown,
                                        pickupCode = uiState.pickupCode,
                                        onNewOrder = viewModel::startNewOrder,
                                    )
                                }

                                uiState.orderId != null -> {
                                    // OrderScreen uses SavedStateHandle for orderId — navigate with the
                                    // kiosk-specific NavBackStackEntry that injects orderId via NavArgs.
                                    KioskOrderFlow(
                                        orderId = uiState.orderId!!,
                                        onOrderPlaced = viewModel::onOrderPlaced,
                                        onStartNewOrder = viewModel::startNewOrder,
                                    )
                                }
                            }

                            if (!uiState.isLoading && uiState.confirmedOrderId == null) {
                                FloatingActionButton(
                                    onClick = { showMemberLookup = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(16.dp),
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
