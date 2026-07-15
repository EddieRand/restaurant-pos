package com.restaurantpos.feature.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.restaurantpos.core.designsystem.PosHairline
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight Compose-Canvas charts for the Reports dashboard (no external chart lib).
 * Values are caller-provided; series are normalised to the drawable area.
 */

data class LineSeries(val points: List<Float>, val color: Color, val dashed: Boolean = false)

@Composable
fun LineChart(series: List<LineSeries>, modifier: Modifier = Modifier, heightDp: Int = 200) {
    val maxV = max(1f, series.flatMap { it.points }.maxOrNull() ?: 1f)
    Canvas(modifier.fillMaxWidth().height(heightDp.dp)) {
        val w = size.width; val h = size.height
        val padB = 8f; val drawH = h - padB
        // baseline gridlines (3)
        for (i in 0..3) {
            val y = drawH * i / 3
            drawLine(PosHairline, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(w, y), strokeWidth = 1f)
        }
        series.forEach { s ->
            if (s.points.size < 2) return@forEach
            val stepX = w / (s.points.size - 1)
            val path = Path()
            s.points.forEachIndexed { i, v ->
                val x = stepX * i
                val y = drawH - (v / maxV) * drawH * 0.92f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                color = s.color,
                style = Stroke(
                    width = 6f,
                    cap = StrokeCap.Round,
                    pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 12f)) else null,
                ),
            )
        }
    }
}

@Composable
fun BarChart(values: List<Float>, color: Color, modifier: Modifier = Modifier, heightDp: Int = 160) {
    val maxV = max(1f, values.maxOrNull() ?: 1f)
    Canvas(modifier.fillMaxWidth().height(heightDp.dp)) {
        if (values.isEmpty()) return@Canvas
        val w = size.width; val h = size.height
        val gap = w / values.size * 0.3f
        val barW = (w / values.size) - gap
        values.forEachIndexed { i, v ->
            val barH = (v / maxV) * h * 0.92f
            val x = (w / values.size) * i + gap / 2
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x, h - barH),
                size = androidx.compose.ui.geometry.Size(max(2f, barW), barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
            )
        }
    }
}

data class DonutSegment(val value: Float, val color: Color)

@Composable
fun DonutChart(segments: List<DonutSegment>, modifier: Modifier = Modifier, sizeDp: Int = 150) {
    val total = max(1f, segments.sumOf { it.value.toDouble() }.toFloat())
    Box(modifier.height(sizeDp.dp)) {
        Canvas(Modifier.fillMaxWidth().height(sizeDp.dp)) {
            val d = min(size.width, size.height)
            val stroke = d * 0.18f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(d - stroke, d - stroke)
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - d) / 2 + inset, inset)
            var start = -90f
            segments.forEach { seg ->
                val sweep = seg.value / total * 360f
                drawArc(
                    color = seg.color,
                    startAngle = start,
                    sweepAngle = sweep - 1.5f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
    }
}
