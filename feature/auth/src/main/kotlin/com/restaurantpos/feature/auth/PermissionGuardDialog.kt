package com.restaurantpos.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/**
 * Shown when the current user lacks a required permission.
 * Presents a minimal PIN entry so a supervisor can approve the action inline.
 */
@Composable
fun PermissionDeniedDialog(
    actionLabel: String,
    onDismiss: () -> Unit,
    onSupervisorApproved: () -> Unit,
    viewModel: AuthViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.auth_permission_required)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.auth_supervisor_pin_prompt, actionLabel),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (uiState.errorMessage != null) {
                    Text(
                        text = uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.auth_cancel))
            }
        },
        dismissButton = null,
    )
}
