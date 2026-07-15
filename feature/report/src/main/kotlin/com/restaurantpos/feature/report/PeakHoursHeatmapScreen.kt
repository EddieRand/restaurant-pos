package com.restaurantpos.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.text.SimpleDateFormat
import java.util.*
import com.restaurantpos.core.designsystem.PosWarningContainer
import com.restaurantpos.core.designsystem.SunmiOrange

/**
 * 高峰时段热力图 — P1 Quick Win
 *
 * 行 = 星期一~日，列 = 0~23 时
 * 颜色深浅 = 该时段的订单量
 * 灵感来源：TouchBistro 翻台率热力图
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeakHoursHeatmapScreen(
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel(),
) {
    val state by viewModel.trendUiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_peak_hours_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
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
            Text(
                "订单量按时段分布（颜色越深 = 订单越多）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.dataPoints.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.report_no_data), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                HeatmapGrid(state = state)
            }
        }
    }
}

@Composable
private fun HeatmapGrid(state: TrendUiState) {
    // 把 dataPoints 聚合为 [dayOfWeek][hour] → orderCount
    val heatmapData = remember(state.dataPoints) { buildHeatmapData(state.dataPoints) }
    val maxCount = heatmapData.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 小时标题行
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(36.dp)) // 给星期标签留位
                (0..23).forEach { hour ->
                    Text(
                        text = if (hour % 3 == 0) "${hour}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 7 行（周一~日）
            val dayLabels = listOf("一", "二", "三", "四", "五", "六", "日")
            dayLabels.forEachIndexed { dayIndex, label ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                    )
                    (0..23).forEach { hour ->
                        val count = heatmapData[dayIndex to hour] ?: 0
                        val intensity = count.toFloat() / maxCount.toFloat()
                        val color = lerpColor(
                            start = PosWarningContainer,
                            end = SunmiOrange,
                            fraction = intensity,
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .padding(1.dp),
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawRect(color = color, style = Fill)
                            }
                            if (intensity > 0.5f && count > 0) {
                                Text(
                                    text = count.toString(),
                                    fontSize = 7.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Center),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            // 图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.report_heat_low), style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(4.dp))
                Canvas(modifier = Modifier.size(16.dp, 10.dp)) {
                    drawRect(PosWarningContainer, style = Fill)
                }
                Spacer(modifier = Modifier.width(2.dp))
                Canvas(modifier = Modifier.size(16.dp, 10.dp)) {
                    drawRect(lerpColor(PosWarningContainer, SunmiOrange, 0.5f), style = Fill)
                }
                Spacer(modifier = Modifier.width(2.dp))
                Canvas(modifier = Modifier.size(16.dp, 10.dp)) {
                    drawRect(SunmiOrange, style = Fill)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.report_heat_high), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/**
 * 从 TrendDataPoint 列表构建 [dayOfWeek(0=Mon) x hour(0-23)] → orderCount 的映射
 *
 * 注意：TrendDataPoint 是日粒度，所以这里简化为——
 * 按星期分组后，把日订单数均匀摊到营业时段（8-22时）
 * TODO: 等有小时级订单数据后替换为真实分布
 */
private fun buildHeatmapData(dataPoints: List<com.restaurantpos.core.model.TrendDataPoint>): Map<Pair<Int, Int>, Int> {
    val result = mutableMapOf<Pair<Int, Int>, Int>()
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    for (point in dataPoints) {
        val date = sdf.parse(point.date) ?: continue
        val cal = Calendar.getInstance().apply { time = date }
        // Calendar.DAY_OF_WEEK: 1=Sun, 2=Mon ... 7=Sat → 转为 0=Mon ... 6=Sun
        val dow = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7

        if (point.orderCount > 0) {
            // 简化：假设营业时段 8:00-22:00，把日订单均摊到这些时段
            val peakHours = (8..22)
            val perHour = point.orderCount / peakHours.count().coerceAtLeast(1)
            val remainder = point.orderCount % peakHours.count()
            peakHours.forEachIndexed { idx, hour ->
                val count = perHour + (if (idx < remainder) 1 else 0)
                if (count > 0) {
                    result[dow to hour] = (result[dow to hour] ?: 0) + count
                }
            }
        }
    }
    return result
}

/** 线性插值两个颜色 */
private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = lerp(start.red, end.red, fraction),
        green = lerp(start.green, end.green, fraction),
        blue = lerp(start.blue, end.blue, fraction),
        alpha = 1f,
    )
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
