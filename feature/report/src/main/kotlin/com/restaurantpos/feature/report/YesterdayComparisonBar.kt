package com.restaurantpos.feature.report

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.restaurantpos.core.designsystem.TrendDownGreen
import com.restaurantpos.core.designsystem.TrendUpRed

/**
 * 昨日同时段对比条 — 插在 ReportContent LazyColumn 末尾
 *
 * 配色规则（中国股市惯例）：
 *   涨（正百分比）→ 红色 0xFFD32F2F
 *   跌（负百分比）→ 绿色 0xFF2E7D32
 */
@Composable
fun YesterdayComparisonBar(
    state: ReportUiState,
    modifier: Modifier = Modifier,
) {
    if (!state.showYesterdayComparison) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "vs 昨日同期",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            HorizontalDivider()

            ComparisonRow(
                label = "净营收",
                today = state.net,
                yesterday = state.yesterdayNet,
                changePercent = state.netChangePercent,
            )
            ComparisonRow(
                label = "订单数",
                today = state.orderCount.toString(),
                yesterday = state.yesterdayOrderCount,
                changePercent = state.orderCountChangePercent,
            )
            ComparisonRow(
                label = "客人数",
                today = state.totalGuestCount.toString(),
                yesterday = state.yesterdayGuestCount,
                changePercent = state.guestCountChangePercent,
            )
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    today: String,
    yesterday: String,
    changePercent: Double,
) {
    val color = when {
        changePercent > 0 -> TrendUpRed
        changePercent < 0 -> TrendDownGreen
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "昨 $yesterday",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "今 $today",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "%.1f%%".format(changePercent),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}
