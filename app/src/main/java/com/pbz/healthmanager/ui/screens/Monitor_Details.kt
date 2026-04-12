package com.pbz.healthmanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import com.pbz.healthmanager.viewmodel.HealthViewModel
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min


@Composable
fun MonitorDetailsScreen(
    viewModel: HealthViewModel,
    name: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = Color(0xFFF8FAFC)
    val primaryBlue = Color(0xFF2563EB)
    val context = LocalContext.current
    val healthIndices by viewModel.healthIndices.collectAsState()
    val todayLogs by viewModel.todayMedicationLogs.collectAsState()
    val allLogs by viewModel.allMedicationLogs.collectAsState()
    val pressureData = buildPressureData(healthIndices)
    val medicineRecords = buildTodayMedicineOverview(todayLogs)
    val adherenceText = buildAdherenceText(todayLogs)
    val bloodPressureExport = buildSevenDayBloodPressureExport(name, healthIndices)
    val medicationHistoryExport = buildMedicationHistoryExport(name, allLogs)
    val aiSummaryText by viewModel.aiHealthSummaryText.collectAsState()
    val aiSummaryStatus by viewModel.aiSummaryStatus.collectAsState()
    val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    LaunchedEffect(Unit) {
        viewModel.refreshAiSummaryNow()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(innerPadding)
        ) {
            DetailTopBar(
                title = "$name 的健康详情",
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    MedicineSection(
                        dateText = dateText,
                        adherenceText = adherenceText,
                        primaryBlue = primaryBlue,
                        records = medicineRecords,
                        onExportBp = { shareText(context, "近七天血压", bloodPressureExport) },
                        onExportMeds = { shareText(context, "历史服药记录", medicationHistoryExport) }
                    )
                }
                item {
                    TrendSection(
                        title = "15天血压趋势图",
                        xLabels = pressureData.labels,
                        systolic = pressureData.systolic,
                        diastolic = pressureData.diastolic,
                        primaryBlue = primaryBlue
                    )
                }
                item {
                    AiAnalysisSection(
                        summaryText = aiSummaryText,
                        statusText = aiSummaryStatus,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    BackToListButton(
                        onClick = onBackClick
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DetailTopBar(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFEFF6FF)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.NotificationsNone,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun MedicineSection(
    dateText: String,
    adherenceText: String,
    primaryBlue: Color,
    records: List<MedicineUiRecord>,
    onExportBp: () -> Unit,
    onExportMeds: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MedicalServices,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "服药记录 ($dateText)",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Text(
                    text = adherenceText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryBlue
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onExportBp,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0ECFF)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导出7天血压", color = Color(0xFF1D4ED8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onExportMeds,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE7F8EE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("导出服药历史", color = Color(0xFF047857), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                records.forEach { item ->
                    MedicineRecordItem(
                        name = item.name,
                        detail = item.detail,
                        statusText = item.statusText,
                        statusBg = item.statusBg,
                        statusFg = item.statusFg,
                        actionText = item.actionText,
                        actionColor = primaryBlue,
                        onActionClick = {}
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicineRecordItem(
    name: String,
    detail: String,
    statusText: String,
    statusBg: Color,
    statusFg: Color,
    actionText: String?,
    actionColor: Color,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = detail,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF64748B)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
                if (actionText != null) {
                    Text(
                        text = actionText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = actionColor,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onActionClick() }
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendSection(
    title: String,
    xLabels: List<String>,
    systolic: List<Int>,
    diastolic: List<Int>,
    primaryBlue: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.ShowChart,
                    contentDescription = null,
                    tint = primaryBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            PressureTrendChart(
                xLabels = xLabels,
                systolic = systolic,
                diastolic = diastolic,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            )
        }
    }
}

@Composable
private fun PressureTrendChart(
    xLabels: List<String>,
    systolic: List<Int>,
    diastolic: List<Int>,
    modifier: Modifier = Modifier
) {
    val yMin = 60
    val yMax = 160
    val ySteps = listOf(160, 140, 120, 100, 80, 60)
    val gridColor = Color(0xFFE2E8F0)
    val dashedColor = Color(0xFFF1F5F9)
    val systolicColor = Color(0xFFEF4444)
    val diastolicColor = Color(0xFF3B82F6)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF8FAFC))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .width(30.dp)
                .height(180.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            ySteps.forEach { y ->
                Text(
                    text = y.toString(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height
                val left = 0f
                val top = 0f
                val right = w
                val bottom = h

                drawLine(
                    color = gridColor,
                    start = Offset(left, bottom),
                    end = Offset(right, bottom),
                    strokeWidth = 2f
                )

                val gridCount = ySteps.size - 1
                for (i in 0..gridCount) {
                    val y = top + (h * i / gridCount.toFloat())
                    val stroke = Stroke(width = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    drawLine(
                        color = dashedColor,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = stroke.width,
                        pathEffect = stroke.pathEffect
                    )
                }

                fun points(values: List<Int>): List<Offset> {
                    val count = max(2, values.size)
                    val xStep = w / (count - 1).toFloat()
                    return values.mapIndexed { index, v ->
                        val clamped = min(yMax, max(yMin, v))
                        val ratio = (clamped - yMin).toFloat() / (yMax - yMin).toFloat()
                        val x = xStep * index
                        val y = bottom - ratio * h
                        Offset(x, y)
                    }
                }

                fun drawSeries(values: List<Int>, color: Color) {
                    val ps = points(values)
                    val path = Path()
                    ps.forEachIndexed { i, p ->
                        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = 6f, cap = StrokeCap.Round)
                    )
                    ps.forEach { p ->
                        drawCircle(
                            color = color,
                            radius = 6f,
                            center = p
                        )
                    }
                }

                drawSeries(systolic, systolicColor)
                drawSeries(diastolic, diastolicColor)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                xLabels.forEach { label ->
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisSection(
    summaryText: String,
    statusText: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFF2563EB),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SmartToy,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "智能分析建议",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = summaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.92f),
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TagChip(text = "低盐饮食")
                TagChip(text = "情绪稳定")
                TagChip(text = "按时服药")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = statusText,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.75f)
            )
        }
    }
}

@Composable
private fun TagChip(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White.copy(alpha = 0.20f),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun BackToListButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFE2E8F0),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "返回列表",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MonitorDetailsScreenPreview() {
}

private data class PressureUiData(
    val labels: List<String>,
    val systolic: List<Int>,
    val diastolic: List<Int>
)

private data class MedicineUiRecord(
    val name: String,
    val detail: String,
    val statusText: String,
    val statusBg: Color,
    val statusFg: Color,
    val actionText: String? = null
)

private fun buildPressureData(indices: List<HealthIndex>): PressureUiData {
    val points = indices
        .asSequence()
        .filter { it.type == "血压" }
        .sortedBy { it.measureTime }
        .mapNotNull { index ->
            val remark = index.remark.orEmpty()
            val pair = if (remark.contains("/")) {
                val value = remark.substringAfter("DEMO_BP|", remark)
                val parts = value.split("/")
                val s = parts.getOrNull(0)?.toIntOrNull()
                val d = parts.getOrNull(1)?.toIntOrNull()
                if (s != null && d != null) s to d else null
            } else {
                index.value.toInt() to 0
            }
            pair?.let {
                Triple(
                    SimpleDateFormat("M/d", Locale.getDefault()).format(Date(index.measureTime)),
                    it.first,
                    it.second
                )
            }
        }
        .toList()
    val recent = points.takeLast(6)
    return PressureUiData(
        labels = recent.map { it.first },
        systolic = recent.map { it.second },
        diastolic = recent.map { it.third }
    )
}

private fun buildTodayMedicineOverview(logs: List<MedicationLogWithInfo>): List<MedicineUiRecord> {
    if (logs.isEmpty()) {
        return listOf(
            MedicineUiRecord(
                name = "暂无今日服药记录",
                detail = "请先在老人端设置并记录服药",
                statusText = "未记录",
                statusBg = Color(0xFFFFEDD5),
                statusFg = Color(0xFFF97316),
                actionText = null
            )
        )
    }
    return logs
        .groupBy { it.log.medicationApprovalNumber }
        .values
        .take(8)
        .map { rows ->
            val med = rows.first().medication
            val total = rows.size
            val taken = rows.count { it.log.status.contains("已服") }
            val status = if (taken >= total) "已完成" else "待完成"
            MedicineUiRecord(
                name = med.name,
                detail = "计划${total}次 · 已服${taken}次 · ${med.dosePerTime} ${med.intakeTiming}",
                statusText = status,
                statusBg = if (taken >= total) Color(0xFFDCFCE7) else Color(0xFFFFEDD5),
                statusFg = if (taken >= total) Color(0xFF16A34A) else Color(0xFFF97316),
                actionText = if (taken >= total) null else "提醒他"
            )
        }
}

private fun buildAdherenceText(logs: List<MedicationLogWithInfo>): String {
    if (logs.isEmpty()) return "依从性: --"
    val taken = logs.count { it.log.status.contains("已服") }
    val rate = (taken * 100 / logs.size)
    return "依从性: $rate%"
}

private fun buildSevenDayBloodPressureExport(name: String, indices: List<HealthIndex>): String {
    val now = System.currentTimeMillis()
    val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000
    val rows = indices
        .asSequence()
        .filter { it.type == "血压" && it.measureTime >= sevenDaysAgo }
        .sortedByDescending { it.measureTime }
        .toList()
    if (rows.isEmpty()) return "$name 近七天暂无血压记录"
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("$name 近七天血压记录")
        rows.forEach { row ->
            val value = row.remark?.substringAfter("DEMO_BP|")?.takeIf { it.contains("/") } ?: "${row.value.toInt()}/--"
            appendLine("${sdf.format(Date(row.measureTime))}  $value")
        }
    }
}

private fun buildMedicationHistoryExport(name: String, logs: List<MedicationLogWithInfo>): String {
    if (logs.isEmpty()) return "$name 暂无历史服药记录"
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return buildString {
        appendLine("$name 历史服药记录")
        logs.sortedByDescending { it.log.scheduledTime }.take(80).forEach { row ->
            appendLine("${sdf.format(Date(row.log.scheduledTime))}  ${row.medication.name}  ${row.log.status}")
        }
    }
}

private fun shareText(context: android.content.Context, title: String, content: String) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(sendIntent, title))
}
