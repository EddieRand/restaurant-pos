package com.restaurantpos.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val PosShapes = Shapes(
    // Extra small: chips, small badges
    extraSmall = RoundedCornerShape(4.dp),
    // Small: input fields, small cards
    small = RoundedCornerShape(8.dp),
    // Medium: menu item cards, table cards
    medium = RoundedCornerShape(12.dp),
    // Large: bottom sheets, dialogs
    large = RoundedCornerShape(16.dp),
    // Extra large: full-screen panels
    extraLarge = RoundedCornerShape(28.dp),
)
