package com.restaurantpos.feature.menu

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.designsystem.*
import com.restaurantpos.core.model.MenuItem

private fun Map<String, String>.localeName(locale: String): String {
    val lang = locale.substringBefore('-')
    return this[lang] ?: this[locale] ?: this["en"] ?: values.firstOrNull() ?: ""
}

@Composable
private fun categoryLabel(categoryId: String): String = when (categoryId) {
    "cat-mains" -> stringResource(R.string.cat_mains)
    "cat-starters" -> stringResource(R.string.cat_starters)
    "cat-drinks" -> stringResource(R.string.cat_drinks)
    "cat-desserts" -> stringResource(R.string.cat_desserts)
    "cat-breakfast" -> stringResource(R.string.cat_breakfast)
    "cat-lunch" -> stringResource(R.string.cat_lunch)
    "cat-dinner" -> stringResource(R.string.cat_dinner)
    else -> categoryId.removePrefix("cat-").replace('-', ' ').replaceFirstChar { it.uppercase() }
}

/** Self-generated emoji "photo" stand-in (no bundled assets), keyed by item name. */
private fun menuEmoji(name: String): String = when {
    name.contains("avocado", true) -> "🥑"
    name.contains("burger", true) || name.contains("cheeseburger", true) -> "🍔"
    name.contains("pasta", true) -> "🍝"
    name.contains("salad", true) || name.contains("kale", true) -> "🥗"
    name.contains("salmon", true) || name.contains("fish", true) -> "🐟"
    name.contains("wing", true) || name.contains("chicken", true) -> "🍗"
    name.contains("acai", true) || name.contains("bowl", true) -> "🍓"
    name.contains("white", true) || name.contains("coffee", true) || name.contains("latte", true) -> "☕"
    name.contains("roll", true) -> "🥟"
    name.contains("fries", true) -> "🍟"
    name.contains("cola", true) -> "🥤"
    name.contains("lemon", true) -> "🍋"
    name.contains("tea", true) -> "🧋"
    name.contains("cake", true) -> "🍰"
    name.contains("cream", true) -> "🍨"
    else -> "🍽️"
}

private enum class MenuTab(val labelRes: Int) {
    ITEMS(R.string.menu_tab_menu_items),
    CATEGORIES(R.string.menu_tab_categories),
    MODIFIER_GROUPS(R.string.menu_tab_modifier_groups),
    MODIFIERS(R.string.menu_tab_modifiers),
    COMBOS(R.string.menu_tab_combo_meals),
}

@Composable
fun MenuScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MenuViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val formatter = remember(uiState.regionConfig) { AmountFormatter(uiState.regionConfig) }
    val locale = uiState.regionConfig.locale
    var tab by remember { mutableStateOf(MenuTab.ITEMS) }
    var query by rememberSaveable { mutableStateOf("") }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(uiState.successMessage, uiState.errorMessage) {
        val msg = uiState.successMessage ?: uiState.errorMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg); viewModel.dismissMessages()
    }

    val items = remember(uiState.items, query, locale) {
        if (query.isBlank()) uiState.items
        else uiState.items.filter { it.names.localeName(locale).contains(query, true) }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(PosContentBg)) {
            // Header
            Column(Modifier.fillMaxWidth().background(PosShellBg).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.menu_title), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Text(stringResource(R.string.menu_subtitle), fontSize = 13.sp, color = PosTextSecondary)
            }
            // Tabs
            Row(Modifier.fillMaxWidth().background(PosShellBg).horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MenuTab.entries.forEach { t -> TabItem(stringResource(t.labelRes), tab == t) { tab = t } }
            }
            HorizontalDivider(color = PosHairline)

            if (tab != MenuTab.ITEMS) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.menu_select_item_hint), fontSize = 14.sp, color = PosTextMuted)
                }
                return@Column
            }

            Row(Modifier.fillMaxSize().weight(1f)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // Toolbar
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SearchField(query, { query = it }, Modifier.width(220.dp))
                        DropdownChip(stringResource(R.string.menu_filter_category))
                        DropdownChip(stringResource(R.string.menu_filter_status))
                        DropdownChip(stringResource(R.string.menu_filter_dayparts))
                        OutlineChip(Icons.Filled.UploadFile, stringResource(R.string.menu_import_export))
                        Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.clickableNoRipple { viewModel.startNew() }) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.menu_add_item), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.menu_total_items, uiState.items.size), fontSize = 13.sp, color = PosTextMuted)
                        Spacer(Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(8.dp), color = SunmiOrangeContainer) {
                            Icon(Icons.Filled.GridView, contentDescription = null, tint = SunmiOrange, modifier = Modifier.padding(6.dp).size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    if (uiState.isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SunmiOrange) }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 200.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            gridItems(items, key = { it.id }) { item ->
                                MenuGridCard(
                                    item = item,
                                    locale = locale,
                                    formatter = formatter,
                                    selected = uiState.editingItem?.id == item.id,
                                    onClick = { viewModel.startEdit(item) },
                                )
                            }
                        }
                    }
                }

                // Detail drawer
                uiState.editingItem?.let { editing ->
                    VerticalDivider(color = PosHairline)
                    ItemDetailDrawer(
                        item = editing,
                        locale = locale,
                        formatter = formatter,
                        onClose = viewModel::dismissEdit,
                        onToggleSoldOut = { viewModel.toggleSoldOut(editing) },
                        onSave = { updated -> viewModel.saveItem(updated, uiState.editingGroups) },
                        modifier = Modifier.width(380.dp).fillMaxHeight(),
                    )
                }
            }
        }
        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ── Grid card ─────────────────────────────────────────────────────────────────

@Composable
private fun MenuGridCard(item: MenuItem, locale: String, formatter: AmountFormatter, selected: Boolean, onClick: () -> Unit) {
    val name = item.names.localeName(locale)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = PosCardBg,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) SunmiOrange else PosHairline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(96.dp).background(MenuImageTint), contentAlignment = Alignment.Center) {
                Text(menuEmoji(name), fontSize = 46.sp, modifier = Modifier.alpha(if (item.isSoldOut) 0.4f else 1f))
                Icon(Icons.Filled.MoreVert, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp))
            }
            Column(Modifier.padding(12.dp)) {
                Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = PosTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(formatter.format(item.priceMinorUnit), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(categoryLabel(item.categoryId), fontSize = 12.sp, color = PosTextMuted)
                    if (item.isSoldOut) {
                        StatusBadge(stringResource(R.string.menu_sold_out), PosBadgeSpicyBg, PosBadgeSpicyFg)
                    } else {
                        StatusBadge(stringResource(R.string.menu_active), PosBadgeVeganBg, PosBadgeVeganFg)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(text: String, bg: Color, fg: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = bg) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
    }
}

// ── Detail drawer (editor) ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemDetailDrawer(
    item: MenuItem,
    locale: String,
    formatter: AmountFormatter,
    onClose: () -> Unit,
    onToggleSoldOut: () -> Unit,
    onSave: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var nameEn by remember(item.id) { mutableStateOf(item.names["en"] ?: "") }
    var priceText by remember(item.id) {
        val major = item.priceMinorUnit / 100; val minor = item.priceMinorUnit % 100
        mutableStateOf(if (minor == 0L) "$major" else "$major.${"%02d".format(minor)}")
    }
    var categoryId by remember(item.id) { mutableStateOf(item.categoryId) }
    var trackInventory by remember(item.id) { mutableStateOf(true) }
    var detailTab by remember(item.id) { mutableStateOf(0) }
    val detailTabs = listOf(
        stringResource(R.string.menu_dtab_details), stringResource(R.string.menu_dtab_pricing),
        stringResource(R.string.menu_dtab_modifiers), stringResource(R.string.menu_dtab_availability),
        stringResource(R.string.menu_dtab_history),
    )

    Column(modifier.background(PosShellBg)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MenuImageTint), contentAlignment = Alignment.Center) {
                        Text(menuEmoji(nameEn.ifBlank { item.names.localeName(locale) }), fontSize = 30.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nameEn.ifBlank { item.names.localeName(locale) }, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PosTextPrimary, modifier = Modifier.weight(1f, false))
                            Spacer(Modifier.width(8.dp))
                            if (!item.isSoldOut) StatusBadge(stringResource(R.string.menu_active), PosBadgeVeganBg, PosBadgeVeganFg)
                        }
                        Text(stringResource(R.string.menu_id_fmt, item.id.takeLast(8).uppercase()), fontSize = 12.sp, color = PosTextMuted)
                    }
                    Icon(Icons.Filled.Close, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(20.dp).clickableNoRipple(onClose))
                }
            }
            // Detail sub-tabs
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    detailTabs.forEachIndexed { idx, label ->
                        TabItem(label, detailTab == idx) { detailTab = idx }
                    }
                }
                HorizontalDivider(color = PosHairline)
            }
            if (detailTab == 0) {
                item { FieldLabel(stringResource(R.string.menu_name_en)); EditField(nameEn) { nameEn = it } }
                item { FieldLabel(stringResource(R.string.menu_category)); EditField(categoryId) { categoryId = it } }
                item {
                    FieldLabel(stringResource(R.string.menu_kitchen))
                    ReadField("Main Kitchen")
                }
                item {
                    FieldLabel(stringResource(R.string.menu_image))
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)).background(MenuImageTint), contentAlignment = Alignment.Center) {
                        Text(menuEmoji(nameEn.ifBlank { item.names.localeName(locale) }), fontSize = 32.sp)
                    }
                }
                item { ToggleRow(stringResource(R.string.menu_available), !item.isSoldOut) { onToggleSoldOut() } }
                item { ToggleRow(stringResource(R.string.menu_sold_out), item.isSoldOut) { onToggleSoldOut() } }
                item { ToggleRow(stringResource(R.string.menu_track_inventory), trackInventory) { trackInventory = it } }
            } else if (detailTab == 1) {
                item { FieldLabel(stringResource(R.string.menu_price)); EditField(priceText, KeyboardType.Decimal) { priceText = it } }
            } else {
                item { Text(stringResource(R.string.menu_select_item_hint), fontSize = 13.sp, color = PosTextMuted) }
            }
        }
        HorizontalDivider(color = PosHairline)
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosBadgeSpicyFg.copy(alpha = 0.4f)), modifier = Modifier.weight(1f).clickableNoRipple {}) {
                Row(Modifier.padding(vertical = 11.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = PosBadgeSpicyFg, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.menu_delete_item), fontSize = 14.sp, color = PosBadgeSpicyFg)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = SunmiOrange, modifier = Modifier.weight(1f).clickableNoRipple {
                val price = priceText.toDoubleOrNull()?.let { (it * 100).toLong() } ?: item.priceMinorUnit
                onSave(item.copy(
                    names = buildMap { if (nameEn.isNotBlank()) put("en", nameEn); item.names["zh"]?.let { put("zh", it) } },
                    priceMinorUnit = price,
                    categoryId = categoryId.ifBlank { item.categoryId },
                ))
            }) {
                Text(stringResource(R.string.menu_save_changes), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(vertical = 11.dp).fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

// ── Small pieces ──────────────────────────────────────────────────────────────

@Composable
private fun TabItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickableNoRipple(onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, color = if (selected) SunmiOrange else PosTextSecondary, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.height(2.dp).width(if (selected) 24.dp else 0.dp).background(SunmiOrange))
    }
}

@Composable
private fun DropdownChip(label: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple {}) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 13.sp, color = PosTextPrimary, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun OutlineChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosShellBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.clickableNoRipple {}) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = PosTextSecondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 13.sp, color = PosTextPrimary, maxLines = 1)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, fontSize = 12.sp, color = PosTextMuted, modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun EditField(value: String, keyboard: KeyboardType = KeyboardType.Text, onValueChange: (String) -> Unit) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            textStyle = TextStyle(fontSize = 14.sp, color = PosTextPrimary),
            cursorBrush = SolidColor(SunmiOrange),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun ReadField(value: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = Modifier.fillMaxWidth()) {
        Text(value, fontSize = 14.sp, color = PosTextPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = PosTextPrimary)
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = SunmiOrange))
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(10.dp), color = PosContentBg, border = BorderStroke(1.dp, PosChipBorder), modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 12.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = PosTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(stringResource(R.string.menu_title), fontSize = 14.sp, color = PosTextMuted, maxLines = 1)
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
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(clickable(interactionSource = interaction, indication = null, onClick = onClick))
}
