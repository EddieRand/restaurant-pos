package com.restaurantpos.app.cashier

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.restaurantpos.core.designsystem.PosNavActiveBg
import com.restaurantpos.core.designsystem.PosNavActiveFg
import com.restaurantpos.core.designsystem.PosNavInactiveFg
import com.restaurantpos.core.designsystem.PosOnlineDot
import com.restaurantpos.core.designsystem.PosShellBg
import com.restaurantpos.core.designsystem.PosTextPrimary
import com.restaurantpos.core.designsystem.PosTextSecondary
import com.restaurantpos.core.designsystem.SunmiOrange

/** Top-level destinations shown in the persistent POS sidebar (mockup order). */
enum class PosDestination(@StringRes val labelRes: Int, val icon: ImageVector, val route: String) {
    POS(R.string.nav_pos, Icons.Filled.PointOfSale, "pos"),
    ORDERS(R.string.nav_orders, Icons.Filled.ReceiptLong, "order-history"),
    TABLES(R.string.nav_tables, Icons.Filled.TableRestaurant, "tables"),
    CUSTOMERS(R.string.nav_customers, Icons.Filled.People, "customers"),
    MENU(R.string.nav_menu, Icons.Filled.MenuBook, "menu"),
    DISCOUNTS(R.string.nav_discounts, Icons.Filled.Star, "discounts"),
    REPORTS(R.string.nav_reports, Icons.Filled.BarChart, "report"),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings, "settings"),
}

/**
 * Persistent left navigation for the desktop POS (matches the design mockups). Presentational:
 * the host maps the current route to [active] and handles [onSelect].
 */
@Composable
fun PosSidebar(
    active: PosDestination,
    onSelect: (PosDestination) -> Unit,
    storeName: String = "Morning Cafe",
    shiftName: String = "Manager",
    isOnline: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(208.dp)
            .background(PosShellBg),
    ) {
        // Brand
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(Color(0xFFFFF1E8)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WbSunny, contentDescription = null, tint = SunmiOrange, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(storeName, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
        }

        Spacer(Modifier.size(4.dp))

        // Nav items
        PosDestination.entries.forEach { dest ->
            NavItem(dest = dest, selected = dest == active, onClick = { onSelect(dest) })
        }

        Spacer(Modifier.weight(1f))

        // Footer: online status + shift
        Column(Modifier.padding(start = 20.dp, end = 16.dp, bottom = 20.dp, top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (isOnline) PosOnlineDot else PosNavInactiveFg))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (isOnline) R.string.sidebar_online else R.string.sidebar_offline), fontSize = 13.sp, color = PosTextSecondary)
            }
            Spacer(Modifier.size(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sidebar_shift_fmt, shiftName), fontSize = 13.sp, color = PosTextSecondary)
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NavItem(dest: PosDestination, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) PosNavActiveBg else Color.Transparent)
            .clickableNoRipple(onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = stringResource(dest.labelRes)
        Icon(
            dest.icon,
            contentDescription = label,
            tint = if (selected) PosNavActiveFg else PosNavInactiveFg,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) PosNavActiveFg else PosTextPrimary,
        )
    }
}

/** Tap handling without a ripple, to match the clean mockup pills. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
