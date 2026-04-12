package com.pbz.healthmanager.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.ui.theme.Emerald50
import com.pbz.healthmanager.ui.theme.Emerald100
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import com.pbz.healthmanager.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun DeviceScreen(
    viewModel: HealthViewModel,
    onBackClick: (() -> Unit)? = null,
    onUpdateClick: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        viewModel.ensureSevenDayBloodPressureSeedData()
    }

    val healthIndices by viewModel.healthIndices.collectAsState()
    val sevenDays = buildSevenDayBloodPressure(healthIndices)
    val threeDays = sevenDays.takeLast(3)
    val todayMorningText = formatSessionBloodPressure(sevenDays.lastOrNull()?.morning)
    val todayEveningText = formatSessionBloodPressure(sevenDays.lastOrNull()?.evening)
    val todayAlert = isDayAbnormal(sevenDays.lastOrNull())

    DeviceScreenContent(
        todayMorningText = todayMorningText,
        todayEveningText = todayEveningText,
        threeDaysBloodPressure = threeDays,
        sevenDaysBloodPressure = sevenDays,
        todayAbnormal = todayAlert,
        onBackClick = onBackClick,
        onAddBloodPressureClick = { morningSys, morningDia, eveningSys, eveningDia ->
            val now = Calendar.getInstance()
            val today = Calendar.getInstance().apply {
                timeInMillis = now.timeInMillis
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (morningSys != null && morningDia != null) {
                val morningTime = Calendar.getInstance().apply {
                    timeInMillis = today.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 8)
                }.timeInMillis
                viewModel.addBloodPressureRecord(morningSys, morningDia, morningTime)
            }
            if (eveningSys != null && eveningDia != null) {
                val eveningTime = Calendar.getInstance().apply {
                    timeInMillis = today.timeInMillis
                    set(Calendar.HOUR_OF_DAY, 20)
                }.timeInMillis
                viewModel.addBloodPressureRecord(eveningSys, eveningDia, eveningTime)
            }
        },
        onUpdateClick = { onUpdateClick?.invoke() }
    )
}

@Composable
private fun DeviceScreenContent(
    todayMorningText: String,
    todayEveningText: String,
    threeDaysBloodPressure: List<DailyBloodPressure>,
    sevenDaysBloodPressure: List<DailyBloodPressure>,
    todayAbnormal: Boolean,
    onBackClick: (() -> Unit)? = null,
    onAddBloodPressureClick: ((Int?, Int?, Int?, Int?) -> Unit)? = null,
    onUpdateClick: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var showInputDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var morningSystolicText by remember { mutableStateOf("") }
    var morningDiastolicText by remember { mutableStateOf("") }
    var eveningSystolicText by remember { mutableStateOf("") }
    var eveningDiastolicText by remember { mutableStateOf("") }

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = { showInputDialog = false },
            title = { Text("录入血压") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("早：", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = morningSystolicText,
                            onValueChange = { morningSystolicText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("收缩压") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = morningDiastolicText,
                            onValueChange = { morningDiastolicText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("舒张压") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Text("晚：", fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = eveningSystolicText,
                            onValueChange = { eveningSystolicText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("收缩压") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = eveningDiastolicText,
                            onValueChange = { eveningDiastolicText = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("舒张压") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddBloodPressureClick?.invoke(
                            morningSystolicText.toIntOrNull(),
                            morningDiastolicText.toIntOrNull(),
                            eveningSystolicText.toIntOrNull(),
                            eveningDiastolicText.toIntOrNull()
                        )
                        showInputDialog = false
                        morningSystolicText = ""
                        morningDiastolicText = ""
                        eveningSystolicText = ""
                        eveningDiastolicText = ""
                    }
                ) { Text("确定填入") }
            },
            dismissButton = {
                TextButton(onClick = { showInputDialog = false }) { Text("取消") }
            }
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(text = "7天血压数据表", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("日期", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("早", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        Text("晚", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                    }
                    sevenDaysBloodPressure.forEach { day ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(day.dayLabel, fontSize = 17.sp, color = TextPrimary)
                            Text(formatSessionBloodPressure(day.morning), fontSize = 17.sp, color = TextPrimary)
                            Text(formatSessionBloodPressure(day.evening), fontSize = 17.sp, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("关闭", fontSize = 18.sp)
                }
            }
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Emerald50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Emerald50)
        ) {
            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "智能设备",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // Content area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .padding(bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Device grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TodayBloodPressureCard(
                        morningValue = todayMorningText,
                        eveningValue = todayEveningText,
                        modifier = Modifier.weight(1f)
                    )
                    DeviceCard(
                        title = "健康腕表",
                        status = "未配对",
                        isConnected = false,
                        icon = Icons.Outlined.Watch,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Steps trend chart
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Emerald600)
                            )
                            Text(
                                text = "最近三天血压记录",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Emerald100)
                                    .border(1.dp, Emerald600, RoundedCornerShape(999.dp))
                                    .clickable { showExportDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "导",
                                    color = Emerald600,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.AddCircle,
                                contentDescription = null,
                                tint = Emerald600,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Emerald100)
                                    .border(1.dp, Emerald600, RoundedCornerShape(999.dp))
                                    .padding(2.dp)
                                    .clickable { showInputDialog = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        BloodPressureTrendChart(
                            data = threeDaysBloodPressure,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.LightMode,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "早", fontSize = 14.sp, color = TextSecondary)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.DarkMode,
                                        contentDescription = null,
                                        tint = Color(0xFF8B5CF6),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "晚", fontSize = 14.sp, color = TextSecondary)
                                }
                            }
                            Text(
                                text = "正常参考：收缩压<135，舒张压<85",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Suggestion section
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 4.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Emerald600)
                            )
                            Text(
                                text = "今日血压建议",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SuggestionCardItem(
                                accentColor = if (todayAbnormal) Color(0xFFEF4444) else Color(0xFF10B981),
                                icon = Icons.Outlined.Favorite,
                                title = if (todayAbnormal) "今日血压偏高" else "今日血压平稳",
                                time = "刚刚",
                                description = if (todayAbnormal) "建议减少盐分摄入，休息后复测血压并按医嘱服药" else "请保持规律作息，早晚继续测量并记录",
                                modifier = Modifier.fillMaxWidth()
                            )
                            SuggestionCardItem(
                                accentColor = Color(0xFFF59E0B),
                                icon = Icons.Outlined.LightMode,
                                title = "关注晨峰血压",
                                time = "今日",
                                description = "建议起床后1小时内固定测量晨间血压，若连续偏高请联系家属或医生",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayBloodPressureCard(
    morningValue: String,
    eveningValue: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .border(
                width = 2.dp,
                color = Emerald600,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Emerald100),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Favorite,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "今日血压",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.LightMode,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = morningValue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald600
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DarkMode,
                        contentDescription = null,
                        tint = Color(0xFF8B5CF6),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = eveningValue,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald600
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(
    title: String,
    value: String? = null,
    status: String? = null,
    isConnected: Boolean,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .border(
                width = 2.dp,
                color = if (isConnected) Emerald600 else Color(0xFFF3F4F6),
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isConnected) Emerald100 else Color(0xFFF3F4F6)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isConnected) Emerald600 else TextSecondary,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) TextPrimary else TextSecondary
            )
            if (value != null) {
                Text(
                    text = value,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Emerald600 else TextSecondary
                )
            }
            if (status != null) {
                Text(
                    text = status,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isConnected) Emerald600 else TextSecondary
                )
            }
        }
    }
}

private data class BloodPressurePair(
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val measureTime: Long = 0L
)

private data class DailyBloodPressure(
    val dayStart: Long,
    val dayLabel: String,
    val morning: BloodPressurePair? = null,
    val evening: BloodPressurePair? = null
)

@Composable
private fun BloodPressureTrendChart(
    data: List<DailyBloodPressure>,
    modifier: Modifier = Modifier
) {
    data class MarkerHit(val x: Float, val y: Float, val label: String)

    val yMin = 60
    val yMax = 180
    val ySteps = listOf(180, 160, 140, 135, 120, 100, 85, 80, 60)
    val morningColor = Color(0xFFF59E0B)
    val eveningColor = Color(0xFF8B5CF6)
    val referenceColor = Color(0xFFD97706)
    val density = LocalDensity.current
    var selectedBarText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFF8FAFC))
            .padding(12.dp)
    ) {
        if (selectedBarText != null) {
            Surface(
                color = Emerald100,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = selectedBarText ?: "",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Emerald600,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(206.dp)
        ) {
            val canvasWidthPx = with(density) { maxWidth.toPx() }
            val canvasHeightPx = with(density) { 190.dp.toPx() }
            val axisLabelWidth = with(density) { 30.dp.toPx() }
            val chartLeft = axisLabelWidth + with(density) { 10.dp.toPx() }
            val chartRight = canvasWidthPx
            val chartWidth = (chartRight - chartLeft).coerceAtLeast(1f)
            val count = max(2, data.size)
            val xStep = chartWidth / (count - 1).toFloat()

            fun yFor(value: Int): Float {
                val clamped = value.coerceIn(yMin, yMax)
                val ratio = (clamped - yMin).toFloat() / (yMax - yMin).toFloat()
                return canvasHeightPx - ratio * canvasHeightPx
            }

            val markerHits = remember(data, maxWidth) {
                val hits = mutableListOf<MarkerHit>()
                data.forEachIndexed { index, day ->
                    val x = chartLeft + xStep * index
                    day.morning?.systolic?.let { hits += MarkerHit(x, yFor(it), "${day.dayLabel} 早 收缩压 $it") }
                    day.morning?.diastolic?.let { hits += MarkerHit(x, yFor(it), "${day.dayLabel} 早 舒张压 $it") }
                    day.evening?.systolic?.let { hits += MarkerHit(x, yFor(it), "${day.dayLabel} 晚 收缩压 $it") }
                    day.evening?.diastolic?.let { hits += MarkerHit(x, yFor(it), "${day.dayLabel} 晚 舒张压 $it") }
                }
                hits
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .pointerInput(markerHits) {
                        detectTapGestures { tap ->
                            val hit = markerHits.minByOrNull { hypot((it.x - tap.x).toDouble(), (it.y - tap.y).toDouble()) }
                            if (hit != null) {
                                val distance = hypot((hit.x - tap.x).toDouble(), (hit.y - tap.y).toDouble())
                                if (distance < 26.0) {
                                    selectedBarText = hit.label
                                }
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height

                fun yCanvas(value: Int): Float {
                    val clamped = value.coerceIn(yMin, yMax)
                    val ratio = (clamped - yMin).toFloat() / (yMax - yMin).toFloat()
                    return height - ratio * height
                }

                ySteps.forEach { step ->
                    val y = yCanvas(step)
                    drawLine(
                        color = if (step == 135 || step == 85) referenceColor else Color(0xFFE2E8F0),
                        start = Offset(chartLeft, y),
                        end = Offset(width, y),
                        strokeWidth = if (step == 135 || step == 85) 2.8f else 2f,
                        pathEffect = if (step == 135 || step == 85) PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f) else null
                    )
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = if (step == 135 || step == 85) android.graphics.Color.parseColor("#D97706") else android.graphics.Color.parseColor("#94A3B8")
                            textSize = 35f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                        drawText(step.toString(), axisLabelWidth, y + 10f, paint)
                    }
                }

                fun drawMarkerSun(center: Offset) {
                    drawCircle(color = Color.White, radius = 34f, center = center)
                    drawCircle(color = morningColor, radius = 26f, center = center)
                    repeat(8) { i ->
                        val angle = i * 45f
                        val rad = Math.toRadians(angle.toDouble())
                        val sx = center.x + (34 * kotlin.math.cos(rad)).toFloat()
                        val sy = center.y + (34 * kotlin.math.sin(rad)).toFloat()
                        val ex = center.x + (46 * kotlin.math.cos(rad)).toFloat()
                        val ey = center.y + (46 * kotlin.math.sin(rad)).toFloat()
                        drawLine(color = morningColor, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = 4.6f)
                    }
                }

                fun drawMarkerMoon(center: Offset) {
                    drawCircle(color = eveningColor, radius = 32f, center = center)
                    drawCircle(color = Color(0xFFF8FAFC), radius = 28f, center = Offset(center.x + 12f, center.y - 6f))
                }

                fun drawMissingMarkers(x: Float, missingMorning: Boolean, missingEvening: Boolean) {
                    val upperY = yCanvas(135)
                    val lowerY = yCanvas(85)
                    val delta = 24f
                    fun drawAt(y: Float) {
                        when {
                            missingMorning && missingEvening -> {
                                drawMarkerSun(Offset(x - delta, y))
                                drawMarkerMoon(Offset(x + delta, y))
                            }
                            missingMorning -> {
                                drawMarkerSun(Offset(x - 14f, y))
                                drawMarkerSun(Offset(x + 14f, y))
                            }
                            missingEvening -> {
                                drawMarkerMoon(Offset(x - 14f, y))
                                drawMarkerMoon(Offset(x + 14f, y))
                            }
                        }
                    }
                    drawAt(upperY)
                    drawAt(lowerY)
                }

                fun drawOneSeries(values: List<Int?>, color: Color, isMorning: Boolean, dashed: Boolean) {
                    var previous: Offset? = null
                    values.forEachIndexed { index, value ->
                        if (value == null) {
                            previous = null
                            return@forEachIndexed
                        }
                        val x = chartLeft + xStep * index
                        val y = yCanvas(value)
                        val point = Offset(x, y)
                        if (previous != null) {
                            drawLine(
                                color = color,
                                start = previous!!,
                                end = point,
                                strokeWidth = 5f,
                                cap = StrokeCap.Round,
                                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) else null
                            )
                        }
                        if (isMorning) drawMarkerSun(point) else drawMarkerMoon(point)
                        previous = point
                    }
                }

                val morningSystolic = data.map { it.morning?.systolic }
                val morningDiastolic = data.map { it.morning?.diastolic }
                val eveningSystolic = data.map { it.evening?.systolic }
                val eveningDiastolic = data.map { it.evening?.diastolic }

                drawOneSeries(morningSystolic, morningColor, true, false)
                drawOneSeries(morningDiastolic, morningColor, true, true)
                drawOneSeries(eveningSystolic, eveningColor, false, false)
                drawOneSeries(eveningDiastolic, eveningColor, false, true)

                data.forEachIndexed { index, day ->
                    val x = chartLeft + xStep * index
                    val missingMorning = day.morning?.systolic == null || day.morning?.diastolic == null
                    val missingEvening = day.evening?.systolic == null || day.evening?.diastolic == null
                    if (missingMorning || missingEvening) {
                        drawMissingMarkers(x, missingMorning, missingEvening)
                    }
                }
            }
        }

    }
}

private fun buildSevenDayBloodPressure(indices: List<HealthIndex>): List<DailyBloodPressure> {
    val calendar = Calendar.getInstance()
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    val dayStarts = (6 downTo 0).map { offset ->
        Calendar.getInstance().apply {
            timeInMillis = calendar.timeInMillis
            add(Calendar.DAY_OF_YEAR, -offset)
        }.timeInMillis
    }
    val daySet = dayStarts.toSet()

    val morningMap = mutableMapOf<Long, BloodPressurePair>()
    val eveningMap = mutableMapOf<Long, BloodPressurePair>()

    indices.forEach { index ->
        val parsed = parseBloodPressure(index) ?: return@forEach
        val recordTime = index.measureTime
        val dayStart = Calendar.getInstance().apply {
            timeInMillis = recordTime
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        if (!daySet.contains(dayStart)) return@forEach
        val hour = Calendar.getInstance().apply { timeInMillis = recordTime }.get(Calendar.HOUR_OF_DAY)
        val isMorning = hour in 0..11

        val target = if (isMorning) morningMap else eveningMap
        val existing = target[dayStart]
        val merged = BloodPressurePair(
            systolic = parsed.systolic ?: existing?.systolic,
            diastolic = parsed.diastolic ?: existing?.diastolic,
            measureTime = maxOf(existing?.measureTime ?: 0L, recordTime)
        )
        target[dayStart] = merged
    }

    val labelFormat = SimpleDateFormat("MM/dd", Locale.getDefault())
    return dayStarts.map { dayStart ->
        DailyBloodPressure(
            dayStart = dayStart,
            dayLabel = labelFormat.format(Date(dayStart)),
            morning = morningMap[dayStart],
            evening = eveningMap[dayStart]
        )
    }
}

private fun parseBloodPressure(index: HealthIndex): BloodPressurePair? {
    val type = index.type
    val remark = index.remark.orEmpty()
    val valueInt = index.value.roundToInt()

    if (type.contains("收缩压")) {
        return BloodPressurePair(systolic = valueInt, measureTime = index.measureTime)
    }
    if (type.contains("舒张压")) {
        return BloodPressurePair(diastolic = valueInt, measureTime = index.measureTime)
    }
    if (!type.contains("血压")) {
        return null
    }

    val source = "$type $remark"
    val match = Regex("(\\d{2,3})\\D+(\\d{2,3})").find(source)
    if (match != null) {
        val sys = match.groupValues[1].toIntOrNull()
        val dia = match.groupValues[2].toIntOrNull()
        return BloodPressurePair(systolic = sys, diastolic = dia, measureTime = index.measureTime)
    }

    return BloodPressurePair(systolic = valueInt, measureTime = index.measureTime)
}

private fun formatTodayBloodPressure(day: DailyBloodPressure?): String {
    if (day == null) return "--/--"
    val candidates = listOfNotNull(day.morning, day.evening)
    val latest = candidates.maxByOrNull { it.measureTime } ?: return "--/--"
    val s = latest.systolic?.toString() ?: "--"
    val d = latest.diastolic?.toString() ?: "--"
    return "$s/$d"
}

private fun formatSessionBloodPressure(pair: BloodPressurePair?): String {
    if (pair == null) return "--/--"
    val s = pair.systolic?.toString() ?: "--"
    val d = pair.diastolic?.toString() ?: "--"
    return "$s/$d"
}

private fun isDayAbnormal(day: DailyBloodPressure?): Boolean {
    if (day == null) return false
    val points = listOfNotNull(day.morning, day.evening)
    return points.any { (it.systolic ?: 0) > 135 || (it.diastolic ?: 0) > 85 }
}

@Composable
private fun SuggestionCardItem(
    accentColor: Color,
    icon: ImageVector,
    title: String,
    time: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(22.dp),
        tonalElevation = 0.dp,
        modifier = modifier,
        border = BorderStroke(1.dp, Color(0xFFF3F4F6))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = time,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
                Text(
                    text = description,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun DeviceScreenPreview() {
    HealthManagerTheme {
        val previewDays = listOf(
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "03/27",
                morning = BloodPressurePair(130, 80, 1L),
                evening = BloodPressurePair(126, 78, 2L)
            ),
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "03/28",
                morning = BloodPressurePair(136, 86, 3L),
                evening = BloodPressurePair(133, 84, 4L)
            ),
            DailyBloodPressure(dayStart = 0L, dayLabel = "03/29"),
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "03/30",
                morning = BloodPressurePair(138, 88, 5L)
            ),
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "03/31",
                evening = BloodPressurePair(129, 79, 6L)
            ),
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "04/01",
                morning = BloodPressurePair(134, 82, 7L),
                evening = BloodPressurePair(130, 80, 8L)
            ),
            DailyBloodPressure(
                dayStart = 0L,
                dayLabel = "04/02",
                morning = BloodPressurePair(132, 82, 9L)
            )
        )
        DeviceScreenContent(
            todayMorningText = "132/82",
            todayEveningText = "128/80",
            threeDaysBloodPressure = previewDays.takeLast(3),
            sevenDaysBloodPressure = previewDays,
            todayAbnormal = false
        )
    }
}
