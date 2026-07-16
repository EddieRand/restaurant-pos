package com.restaurantpos.feature.crm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.*
import com.restaurantpos.core.model.Customer
import com.restaurantpos.core.model.LoyaltyTransaction
import com.restaurantpos.core.model.LoyaltyTxnType
import java.text.SimpleDateFormat
import java.util.*

private val sinceFmt = SimpleDateFormat("MMM d, yyyy", Locale.US)
private val visitFmt = SimpleDateFormat("yyyy年M月d日", Locale.SIMPLIFIED_CHINESE)
private val txnFmt = SimpleDateFormat("MMM d · HH:mm", Locale.US)
private const val DAY_MS = 86_400_000L

@Composable
fun CustomersScreen(
    modifier: Modifier = Modifier,
    viewModel: CustomersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }
    val list = uiState.filtered

    Column(modifier = modifier.fillMaxSize().background(PosContentBg)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.cust_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Text(stringResource(R.string.cust_subtitle), fontSize = 13.sp, color = PosTextSecondary)
            }
            SearchField(uiState.query, viewModel::setQuery, Modifier.width(260.dp))
            Spacer(Modifier.width(10.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple {}) {
                Icon(Icons.Filled.FilterList, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.padding(10.dp).size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            AddButton()
        }
        HorizontalDivider(color = PosHairline)

        Row(Modifier.fillMaxSize().weight(1f)) {
            // ── Left: tabs + count + table ──
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    CustTab(stringResource(R.string.cust_tab_all), uiState.tab == CustomersTab.ALL) { viewModel.setTab(CustomersTab.ALL) }
                    CustTab(stringResource(R.string.cust_tab_recent), uiState.tab == CustomersTab.RECENT) { viewModel.setTab(CustomersTab.RECENT) }
                    CustTab(stringResource(R.string.cust_tab_loyal), uiState.tab == CustomersTab.LOYAL) { viewModel.setTab(CustomersTab.LOYAL) }
                }
                HorizontalDivider(color = PosHairline)

                if (list.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.cust_empty), fontSize = 15.sp, color = PosTextSecondary)
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.cust_total, uiState.all.size),
                        fontSize = 13.sp, color = PosTextMuted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                    TableHeader()
                    HorizontalDivider(color = PosHairline)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { c ->
                            CustomerTableRow(c, formatter, c.id == uiState.selectedId) { viewModel.select(c.id) }
                            HorizontalDivider(color = PosHairline)
                        }
                    }
                }
            }

            VerticalDivider(color = PosHairline)

            CustomerDetail(
                customer = uiState.selected,
                transactions = uiState.transactions,
                formatter = formatter,
                modifier = Modifier.width(400.dp).fillMaxHeight(),
            )
        }
    }
}

// ── Table ───────────────────────────────────────────────────────────────────

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().background(PosContentBg).padding(horizontal = 20.dp, vertical = 10.dp)) {
        HeaderCell(stringResource(R.string.cust_col_customer), 2f)
        HeaderCell(stringResource(R.string.cust_col_phone), 1.4f)
        HeaderCell(stringResource(R.string.cust_col_email), 2f)
        HeaderCell(stringResource(R.string.cust_col_spent), 1.1f)
        HeaderCell(stringResource(R.string.cust_col_visits), 0.7f)
        HeaderCell(stringResource(R.string.cust_col_last_visit), 1.2f)
        Spacer(Modifier.width(24.dp))
    }
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PosTextMuted, modifier = Modifier.weight(weight))
}

@Composable
private fun CustomerTableRow(c: Customer, formatter: AmountFormatter, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (selected) SunmiOrangeContainer else PosShellBg).clickableNoRipple(onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(2f), verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.name, 34)
            Spacer(Modifier.width(12.dp))
            Text(c.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = PosTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            statusBadge(c)?.let { Spacer(Modifier.width(8.dp)); it() }
        }
        Text(c.phone, fontSize = 13.sp, color = PosTextSecondary, modifier = Modifier.weight(1.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(c.email ?: "—", fontSize = 13.sp, color = PosTextSecondary, modifier = Modifier.weight(2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(formatter.format(c.totalSpendMinorUnit), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary, modifier = Modifier.weight(1.1f))
        Text("${c.totalVisits}", fontSize = 13.sp, color = PosTextPrimary, modifier = Modifier.weight(0.7f))
        Text(lastVisitText(c.lastVisitAt), fontSize = 13.sp, color = PosTextSecondary, modifier = Modifier.weight(1.2f), maxLines = 1)
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = PosTextMuted, modifier = Modifier.width(24.dp))
    }
}

/** Returns a composable VIP/New badge, or null if the customer has neither flag. */
@Composable
private fun statusBadge(c: Customer): (@Composable () -> Unit)? {
    val vip = c.tags.any { it.equals("VIP", true) } || c.membershipTierId == "tier-gold"
    val isNew = !vip && (System.currentTimeMillis() - c.registeredAt) < 30 * DAY_MS
    return when {
        vip -> { { Badge(stringResource(R.string.cust_badge_vip), PosBadgePopularBg, PosBadgePopularFg) } }
        isNew -> { { Badge(stringResource(R.string.cust_badge_new), PosBadgeVeganBg, PosBadgeVeganFg) } }
        else -> null
    }
}

@Composable
private fun Badge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

// ── Detail panel ──────────────────────────────────────────────────────────────

@Composable
private fun CustomerDetail(
    customer: Customer?,
    transactions: List<LoyaltyTransaction>,
    formatter: AmountFormatter,
    modifier: Modifier = Modifier,
) {
    if (customer == null) {
        Box(modifier.background(PosShellBg), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.cust_select_hint), fontSize = 14.sp, color = PosTextMuted)
        }
        return
    }
    Column(modifier.background(PosShellBg)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(customer.name, 52)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(customer.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                            statusBadge(customer)?.let { Spacer(Modifier.width(8.dp)); it() }
                        }
                        Text(stringResource(R.string.cust_customer_since, sinceFmt.format(Date(customer.registeredAt))), fontSize = 12.sp, color = PosTextMuted)
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoLine(stringResource(R.string.cust_phone), customer.phone)
                    customer.email?.takeIf { it.isNotBlank() }?.let { InfoLine(stringResource(R.string.cust_email), it) }
                    customer.birthday?.takeIf { it.isNotBlank() }?.let { InfoLine(stringResource(R.string.cust_birthday), it) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(stringResource(R.string.cust_spent), formatter.format(customer.totalSpendMinorUnit), Modifier.weight(1f))
                    StatTile(stringResource(R.string.cust_visits), "${customer.totalVisits}", Modifier.weight(1f))
                    val avg = if (customer.totalVisits > 0) customer.totalSpendMinorUnit / customer.totalVisits else 0L
                    StatTile(stringResource(R.string.cust_avg_spend), formatter.format(avg), Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.cust_recent_orders), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                    Text(stringResource(R.string.cust_view_all), fontSize = 13.sp, color = SunmiOrange, modifier = Modifier.clickableNoRipple {})
                }
            }
            if (transactions.isEmpty()) {
                item { Text(stringResource(R.string.cust_no_activity), fontSize = 13.sp, color = PosTextMuted) }
            } else {
                items(transactions, key = { it.id }) { txn -> TransactionRow(txn) }
            }
            customer.notes?.takeIf { it.isNotBlank() }?.let { note ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.cust_notes_title), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                        Text(stringResource(R.string.cust_add_note), fontSize = 13.sp, color = SunmiOrange, modifier = Modifier.clickableNoRipple {})
                    }
                    Spacer(Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(sinceFmt.format(Date(customer.registeredAt)), fontSize = 11.sp, color = PosTextMuted)
                            Spacer(Modifier.height(2.dp))
                            Text(note, fontSize = 13.sp, color = PosTextSecondary)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = PosHairline)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.weight(1f).clickableNoRipple {}) {
                Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cust_edit), fontSize = 14.sp, color = PosTextPrimary)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.weight(1f).clickableNoRipple {}) {
                Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.cust_new_order), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PosTextMuted)
        Text(value, fontSize = 13.sp, color = PosTextPrimary)
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, modifier = modifier) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(label, fontSize = 11.sp, color = PosTextSecondary)
        }
    }
}

@Composable
private fun TransactionRow(txn: LoyaltyTransaction) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.description, fontSize = 13.sp, color = PosTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(txnFmt.format(Date(txn.createdAt)), fontSize = 11.sp, color = PosTextMuted)
        }
        val earned = txn.type == LoyaltyTxnType.EARN || (txn.type == LoyaltyTxnType.ADJUST && txn.points >= 0)
        Text((if (txn.points >= 0) "+" else "") + "${txn.points}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (earned) PosBadgeVeganFg else PosBadgeSpicyFg)
    }
}

// ── Shared ──────────────────────────────────────────────────────────────────

@Composable
private fun CustTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickableNoRipple(onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) SunmiOrange else PosTextSecondary)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(if (selected) 28.dp else 0.dp).background(SunmiOrange))
    }
}

@Composable
private fun Avatar(name: String, size: Int) {
    val initials = name.trim().split(" ").filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size.dp).clip(CircleShape).background(SunmiOrangeContainer), contentAlignment = Alignment.Center) {
        Text(initials, fontSize = (size / 2.6).sp, fontWeight = FontWeight.Bold, color = SunmiOrange)
    }
}

@Composable
private fun tierLabel(tierId: String?): String = when (tierId?.removePrefix("tier-")?.lowercase()) {
    null, "", "member" -> stringResource(R.string.cust_tier_member)
    "gold" -> stringResource(R.string.cust_tier_gold)
    "silver" -> stringResource(R.string.cust_tier_silver)
    "bronze" -> stringResource(R.string.cust_tier_bronze)
    else -> tierId!!.removePrefix("tier-").replaceFirstChar { it.uppercase() }
}

@Composable
private fun lastVisitText(epoch: Long): String {
    if (epoch <= 0) return "—"
    val now = System.currentTimeMillis()
    val days = (now - epoch) / DAY_MS
    return when (days) {
        0L -> stringResource(R.string.cust_today)
        1L -> stringResource(R.string.cust_yesterday)
        else -> visitFmt.format(Date(epoch))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.cust_search_hint), fontSize = 14.sp, color = PosTextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(
                    value = value, onValueChange = onValueChange, singleLine = true,
                    textStyle = TextStyle(fontSize = 14.sp, color = PosTextPrimary),
                    cursorBrush = SolidColor(SunmiOrange), modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AddButton() {
    Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.clickableNoRipple {}) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cust_add), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
