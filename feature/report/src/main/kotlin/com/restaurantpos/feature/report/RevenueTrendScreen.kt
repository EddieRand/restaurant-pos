package com.restaurantpos.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.restaurantpos.core.config.AmountFormatter
import com.restaurantpos.core.config.ConfigRepository
import com.restaurantpos.core.model.TrendDataPoint
import java.text.SimpleDateFormat
import java.util.*
import java.util.*
import kotlin.math.max
import com.restaurantpos.core.designsystem.SunmiOrange
import com.restaurantpos.core.designsystem.TrendDownGreen
import com.restaurantpos.core.designsystem.TrendUpRed

/**
 * 营收趋势图 — P1 Quick Win
 *
 * 交互：日期区间选择（开始/结束）+ 绘图区（Canvas 柱状图）
 * 支持横屏，7/15/30 天快捷切换
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevenueTrendScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.trendUiState.collectAsState()

    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_revenue_trend)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    // 快捷区间按钮
                    TextButton(onClick = { viewModel.setLastNDays(7) }) { Text(stringResource(R.string.report_last_n_days, 7)) }
                    TextButton(onClick = { viewModel.setLastNDays(15) }) { Text(stringResource(R.string.report_last_n_days, 15)) }
                    TextButton(onClick = { viewModel.setLastNDays(30) }) { Text(stringResource(R.string.report_last_n_days, 30)) }
                    IconButton(onClick = { viewModel.loadTrend() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 日期区间选择行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { showStartPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.startDate ?: "开始日期")
                }
                OutlinedButton(
                    onClick = { showEndPicker = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(state.endDate ?: "结束日期")
                }
                Button(onClick = { viewModel.loadTrend() }) {
                    Text(stringResource(R.string.report_query))
                }
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.dataPoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.report_no_data_hint), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                // 汇总卡片
                TrendSummaryCard(state = state)
                // 柱状图
                Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.report_net_revenue_trend), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        TrendBarChart(
                            dataPoints = state.dataPoints,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    // Date Pickers
    if (showStartPicker) {
        DatePickerModal(
            initialEpoch = state.startEpoch,
            onConfirm = { epoch ->
                viewModel.setStartEpoch(epoch)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        DatePickerModal(
            initialEpoch = state.endEpoch,
            onConfirm = { epoch ->
                viewModel.setEndEpoch(epoch)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun TrendSummaryCard(state: TrendUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.report_total_revenue), style = MaterialTheme.typography.labelSmall)
                Text(state.totalRevenueFormatted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.report_daily_average), style = MaterialTheme.typography.labelSmall)
                Text(state.avgDailyRevenueFormatted, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.report_total_orders), style = MaterialTheme.typography.labelSmall)
                Text(state.totalOrders.toString(), style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.report_change_percent), style = MaterialTheme.typography.labelSmall)
                val color = when {
                    state.revenueChangePercent > 0 -> TrendUpRed
                    state.revenueChangePercent < 0 -> TrendDownGreen
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text("%.1f%%".format(state.revenueChangePercent), color = color, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TrendBarChart(
    dataPoints: List<TrendDataPoint>,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.isEmpty()) return

    val maxRevenue = dataPoints.maxOf { it.netRevenueMinorUnit }.toFloat().coerceAtLeast(1f)
    val dp = with(LocalDensity.current) { 12.dp.toPx() }

    Canvas(modifier = modifier.padding(vertical = 8.dp)) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = (canvasWidth / (dataPoints.size * 1.5f)).coerceAtMost(40.dp.toPx())
        val gap = (canvasWidth - barWidth * dataPoints.size) / (dataPoints.size + 1)

        // 基线
        drawLine(
            color = androidx.compose.ui.graphics.Color.Gray,
            start = Offset(0f, canvasHeight - dp),
            end = Offset(canvasWidth, canvasHeight - dp),
            strokeWidth = 1f,
        )

        dataPoints.forEachIndexed { index, point ->
            val barHeight = (point.netRevenueMinorUnit.toFloat() / maxRevenue) * (canvasHeight - dp * 2)
            val left = gap + index * (barWidth + gap)
            val top = canvasHeight - dp - barHeight

            // 柱体
            drawRect(
                color = SunmiOrange,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
            )
        }
    }

    // X 轴标签（日期）
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(dataPoints.size) { index ->
            val point = dataPoints[index]
            Text(
                text = point.date.takeLast(5), // MM-dd
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
                maxLines = 1,
            )
        }
    }
}
