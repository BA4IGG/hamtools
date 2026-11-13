package com.ham.tools.ui.network

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.ham.tools.ui.components.InfoSurface
import com.ham.tools.ui.components.KeyValueRow
import com.ham.tools.ui.components.NoteCard
import com.ham.tools.ui.components.SectionLabel
import com.ham.tools.ui.components.StatusBanner
import com.ham.tools.ui.components.ToolScaffold
import com.ham.tools.ui.components.ToolScrollColumn
import com.ham.tools.ui.theme.AppShapes
import com.ham.tools.util.GeoPoint
import com.ham.tools.util.GridMath
import com.ham.tools.util.PathResult
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 网格与方位：梅登海德网格互转、大圆距离与双向方位。
 *
 * 这一页的计算完全离线：网格编解码与 Haversine/方位角都是纯函数，
 * 不请求任何地图服务，也不引入地图 SDK。罗盘只是角度示意，不是地图底图。
 */
@Composable
fun GridSquareScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    var fromGrid by rememberSaveable { mutableStateOf("OM89") }
    var toGrid by rememberSaveable { mutableStateOf("PM01") }
    var latInput by rememberSaveable { mutableStateOf("31.23") }
    var lonInput by rememberSaveable { mutableStateOf("121.47") }

    val fromPoint = remember(fromGrid) { GridMath.decode(fromGrid) }
    val toPoint = remember(toGrid) { GridMath.decode(toGrid) }
    val path: PathResult? = remember(fromPoint, toPoint) {
        if (fromPoint != null && toPoint != null) GridMath.path(fromPoint, toPoint) else null
    }
    val converted = remember(latInput, lonInput) {
        val lat = latInput.toDoubleOrNull()
        val lon = lonInput.toDoubleOrNull()
        if (lat != null && lon != null && lat in -90.0..90.0 && lon in -180.0..180.0) {
            GridMath.encode(lat, lon, 3)
        } else "—"
    }

    ToolScaffold(title = "网格与方位", onBack = onBack, modifier = modifier) { padding ->
        ToolScrollColumn(padding) {
            SectionLabel(text = "两点网格")
            OutlinedTextField(
                value = fromGrid,
                onValueChange = { fromGrid = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "本台网格（2/4/6/8 字符）") },
                singleLine = true,
                shape = AppShapes.small
            )
            OutlinedTextField(
                value = toGrid,
                onValueChange = { toGrid = it.uppercase() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "目标网格") },
                singleLine = true,
                shape = AppShapes.small
            )

            if (fromPoint == null || toPoint == null) {
                StatusBanner(
                    text = "网格格式不正确。示例：OM89（4 字符）、OM89ab（6 字符）",
                    warning = true
                )
            } else {
                InfoSurface(title = "解析结果") {
                    KeyValueRow(label = "本台中心", value = formatPoint(fromPoint))
                    KeyValueRow(label = "目标中心", value = formatPoint(toPoint))
                }

                SectionLabel(text = "大圆路径")
                InfoSurface {
                    KeyValueRow(label = "距离", value = "%.1f km".format(path!!.distanceKm))
                    KeyValueRow(
                        label = "去程方位",
                        value = "%.1f° · %s".format(path!!.bearingDeg, GridMath.compass(path!!.bearingDeg))
                    )
                    KeyValueRow(
                        label = "回程方位",
                        value = "%.1f° · %s".format(path!!.backBearingDeg, GridMath.compass(path!!.backBearingDeg))
                    )
                }
                CompassDial(bearingDeg = path!!.bearingDeg)
            }

            SectionLabel(text = "经纬度 → 网格")
            OutlinedTextField(
                value = latInput,
                onValueChange = { latInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "纬度（-90 ~ 90）") },
                singleLine = true,
                shape = AppShapes.small
            )
            OutlinedTextField(
                value = lonInput,
                onValueChange = { lonInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = "经度（-180 ~ 180）") },
                singleLine = true,
                shape = AppShapes.small
            )
            InfoSurface(title = "6 字符网格") {
                Text(text = converted, style = MaterialTheme.typography.titleLarge)
            }

            NoteCard(
                text = "网格按「经度字符、纬度字符」交替编码，decode 返回格子中心点。距离用 Haversine 大圆公式，方位为初始大圆方位角；回程方位独立计算，不是简单加 180°。本页不依赖任何地图服务。"
            )
        }
    }
}

private fun formatPoint(point: GeoPoint): String =
    "%.3f°, %.3f°".format(point.lat, point.lon)

/** 只画角度指示，不渲染任何地理底图。 */
@Composable
private fun CompassDial(bearingDeg: Double) {
    val lineColor = MaterialTheme.colorScheme.outline
    val pointerColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = min(size.width, size.height) * 0.4f
        drawCircle(
            color = lineColor,
            radius = radius,
            center = Offset(cx, cy),
            style = Stroke(width = 2.dp.toPx())
        )
        drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(cx, cy))
        // 正北参考线
        drawLine(
            color = lineColor,
            start = Offset(cx, cy - radius),
            end = Offset(cx, cy - radius * 0.75f),
            strokeWidth = 3.dp.toPx()
        )
        val rad = Math.toRadians(bearingDeg - 90.0)
        val tip = Offset(
            cx + (radius * 0.85f * cos(rad)).toFloat(),
            cy + (radius * 0.85f * sin(rad)).toFloat()
        )
        drawLine(
            color = pointerColor,
            start = Offset(cx, cy),
            end = tip,
            strokeWidth = 5.dp.toPx()
        )
        drawCircle(color = pointerColor, radius = 7.dp.toPx(), center = tip)
    }
}
