package com.restaurantpos.core.designsystem

import androidx.compose.ui.graphics.Color

// SUNMI brand orange — primary brand color, never override with dynamic color
val SunmiOrange = Color(0xFFFF5C00)
val SunmiOrangeLight = Color(0xFFFF8A40)
val SunmiOrangeDark = Color(0xFFCC4900)
val SunmiOrangeContainer = Color(0xFFFFECE0)
val OnSunmiOrange = Color(0xFFFFFFFF)
val OnSunmiOrangeContainer = Color(0xFF3D1100)

// Neutral surface tones
val PosBackground = Color(0xFFF8F5F2)
val PosSurface = Color(0xFFFFFFFF)
val PosSurfaceVariant = Color(0xFFF3EDE8)
val PosOutline = Color(0xFFD4C8C0)
val PosOnSurface = Color(0xFF1C1917)
val PosOnSurfaceVariant = Color(0xFF6B5E57)

// Semantic colors
val PosSuccess = Color(0xFF2E7D32)
val PosSuccessContainer = Color(0xFFE8F5E9)
val PosWarning = Color(0xFFF57C00)
val PosWarningContainer = Color(0xFFFFF3E0)
val PosError = Color(0xFFBA1A1A)
val PosErrorContainer = Color(0xFFFFDAD6)

// KDS status colors
val KdsNew = SunmiOrange
val KdsInProgress = Color(0xFF1565C0)
val KdsDone = PosSuccess

// Receipt WYSIWYG preview (thermal paper mock)
val ReceiptPaper = Color(0xFFFFFDE7)
val ReceiptInk = Color(0xFF212121)
val ReceiptInkLight = Color(0xFF616161)
val ReceiptBorder = Color(0xFFE0E0E0)
val ReceiptDivider = Color(0xFFBDBDBD)

// 营收涨跌色（中式惯例：红涨绿跌）
val TrendUpRed = Color(0xFFD32F2F)
val TrendDownGreen = PosSuccess

// ─────────────────────────────────────────────────────────────────────────────
// Desktop-POS design-mockup tokens (cooler light palette, additive — do not
// repurpose the warm Pos* tokens above that other screens depend on).
// ─────────────────────────────────────────────────────────────────────────────
val PosShellBg = Color(0xFFFFFFFF)          // app frame / sidebar surface
val PosContentBg = Color(0xFFFAFAFA)        // content area behind cards
val PosCardBg = Color(0xFFFFFFFF)
val PosHairline = Color(0xFFEFEDEA)         // 1px dividers / card borders
val PosTextPrimary = Color(0xFF1F2430)      // headings / item names
val PosTextSecondary = Color(0xFF6B7280)    // descriptions / labels
val PosTextMuted = Color(0xFF9CA3AF)        // placeholders / meta
val PosNavActiveBg = SunmiOrangeContainer   // active sidebar item background
val PosNavActiveFg = SunmiOrange            // active sidebar item icon/text
val PosNavInactiveFg = Color(0xFF6B7280)
val PosChipBg = Color(0xFFFFFFFF)           // top-bar selector chips
val PosChipBorder = Color(0xFFE6E3DF)

// Menu item badges
val PosBadgePopularBg = Color(0xFFFFF1E8)
val PosBadgePopularFg = Color(0xFFB45309)
val PosBadgeVeganBg = Color(0xFFE8F5EC)
val PosBadgeVeganFg = Color(0xFF2E7D32)
val PosBadgeSpicyBg = Color(0xFFFDECEC)
val PosBadgeSpicyFg = Color(0xFFD32F2F)

// Online status dot
val PosOnlineDot = Color(0xFF22C55E)

// Table floor status accents (soft blue for reserved; others reuse badge palettes)
val PosTableReservedBg = Color(0xFFE8F0FE)
val PosTableReservedFg = Color(0xFF1A56DB)

// Soft warm "plate" tint behind the menu-card emoji photo stand-in
val MenuImageTint = Color(0xFFF3EEE9)

// Analytics dashboard chart palette
val PosChartBlue = Color(0xFF3B82F6)
val PosChartGreen = Color(0xFF22C55E)
val PosChartPurple = Color(0xFF8B5CF6)
val PosChartAmber = Color(0xFFF59E0B)
val PosChartGrey = Color(0xFFCBD5E1)
