package com.pbz.healthmanager.ui.screens

import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pbz.healthmanager.alarm.AlarmScheduler
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.ui.theme.Emerald50
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.viewmodel.AlarmViewModel
import com.pbz.healthmanager.viewmodel.HealthViewModel
import java.util.Calendar

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.window.DialogProperties

import android.widget.TimePicker
import androidx.compose.ui.viewinterop.AndroidView
import android.view.LayoutInflater
import com.pbz.healthmanager.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    onBackClick: () -> Unit = {},
    alarmViewModel: AlarmViewModel = viewModel(),
    healthViewModel: HealthViewModel? = null
) {
    val context = LocalContext.current
    val alarmList by alarmViewModel.alarmList.collectAsState()
    val currentMedication by (healthViewModel?.medicineResult?.collectAsState() ?: remember { mutableStateOf<Medication?>(null) })
    val scrollState = rememberScrollState()
    val scheduler = remember { AlarmScheduler(context) }

    var showTimePicker by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf("") }
    
    // 权限请求处理 (针对 Android 13+)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "请开启通知权限以接收闹钟提醒", Toast.LENGTH_LONG).show()
            }
        }
        LaunchedEffect(Unit) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    // 处理 UI 事件 (Toast)
    LaunchedEffect(Unit) {
        alarmViewModel.uiEvent.collect { event ->
            when (event) {
                is AlarmViewModel.UiEvent.ShowTimePicker -> {
                    showTimePicker = event.id
                }
                is AlarmViewModel.UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 滚轮式时间选择器对话框
    if (showTimePicker != null) {
        var selectedHour by remember { mutableStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
        var selectedMinute by remember { mutableStateOf(Calendar.getInstance().get(Calendar.MINUTE)) }

        AlertDialog(
            onDismissRequest = { showTimePicker = null },
            title = { Text("选择时间") },
            text = {
                AndroidView(
                    factory = { ctx ->
                        TimePicker(android.view.ContextThemeWrapper(ctx, android.R.style.Theme_Holo_Light_Dialog_NoActionBar)).apply {
                            setIs24HourView(true)
                            setOnTimeChangedListener { _, hour, minute ->
                                selectedHour = hour
                                selectedMinute = minute
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    alarmViewModel.updateTime(showTimePicker!!, selectedHour, selectedMinute)
                    // 核心：设置时间后，立即同步到系统闹钟
                    val updatedItem = alarmList.find { it.id == showTimePicker }?.copy(
                        time = String.format("%02d:%02d", selectedHour, selectedMinute),
                        isActive = true
                    )
                    updatedItem?.let { scheduler.schedule(it, currentMedication?.approvalNumber) }
                    showTimePicker = null
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = null }) { Text("取消") }
            }
        )
    }

    // 新增时段名称输入对话框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("新增用药时段") },
            text = {
                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("请输入药品名称或时段") },
                    placeholder = { Text("例如：吃降压药") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (customName.isNotBlank()) {
                            alarmViewModel.addTimeSlot(customName)
                            customName = ""
                            showAddDialog = false
                        }
                    }
                ) { Text("添加") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
    
    // Custom colors based on design
    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray400 = Color(0xFF9CA3AF)
    val Gray500 = Color(0xFF6B7280)
    val Gray800 = Color(0xFF1F2937)
    val Orange100 = Color(0xFFFFEDD5)
    val Orange500 = Color(0xFFF97316)
    val Blue100 = Color(0xFFDBEAFE)
    val Blue500 = Color(0xFF3B82F6)
    val Purple100 = Color(0xFFF3E8FF)
    val Purple500 = Color(0xFFA855F7)
    val Red500 = Color(0xFFEF4444)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Gray50)
            .statusBarsPadding()
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Gray800,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBackClick() }
            )
            Text(
                text = "设置用药闹钟",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gray800
            )
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = Gray500,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Subtitle
            val currentDate = remember {
                val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                dateFormat.format(java.util.Date())
            }
            Text(
                text = "今天是 $currentDate，请确保闹钟状态已开启。",
                fontSize = 14.sp,
                color = Gray500,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 动态渲染闹钟列表
            alarmList.forEach { item ->
                val (icon, bgColor, tint) = when {
                    item.periodName.contains("早") -> Triple(Icons.Outlined.WbSunny, Orange100, Orange500)
                    item.periodName.contains("中") -> Triple(Icons.Outlined.LightMode, Blue100, Blue500)
                    item.periodName.contains("晚") -> Triple(Icons.Outlined.DarkMode, Purple100, Purple500)
                    else -> Triple(Icons.Outlined.Settings, Gray100, Gray500)
                }

                AlarmCard(
                    icon = icon,
                    iconBgColor = bgColor,
                    iconTint = tint,
                    title = item.periodName,
                    subtitle = item.suggestion,
                    subtitleColor = if (item.isActive) Gray500 else Red500,
                    time = item.time ?: "--:--",
                    isActive = item.isActive,
                    onToggle = { isActive ->
                        alarmViewModel.toggleAlarm(item.id)
                        // 核心：开关切换后，同步到系统闹钟
                        val updatedItem = item.copy(isActive = !item.isActive)
                        if (updatedItem.isActive && updatedItem.time != null) {
                            scheduler.schedule(updatedItem, currentMedication?.approvalNumber)
                        } else {
                            scheduler.cancel(updatedItem)
                        }
                    },
                    onDelete = {
                        scheduler.cancel(item) // 取消系统闹钟
                        alarmViewModel.removeTimeSlot(item.id) // 从列表移除
                    },
                    actionLabel = if (item.time == null) "点击设置" else "修改时间",
                    onAction = { showTimePicker = item.id },
                    isActionGray = !item.isActive
                )
            }

            // Add Button (Dashed)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .drawBehind {
                        drawRoundRect(
                            color = Emerald600,
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            ),
                            cornerRadius = CornerRadius(32.dp.toPx())
                        )
                    }
                    .background(Emerald50.copy(alpha = 0.5f))
                    .clickable { showAddDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = Emerald600,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "新增用药时段",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald600
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { /* TODO: Reset logic if needed */ },
                    colors = ButtonDefaults.buttonColors(containerColor = Gray100),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = "重置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Gray500
                    )
                }

                Button(
                    onClick = { 
                        // 保存修改并设定闹钟
                        // 1. 先取消列表中所有的系统闹钟，防止重复或残留
                        // 2. 对当前列表中的 isActive 项重新 schedule
                        alarmList.forEach { item ->
                            if (item.isActive && item.time != null) {
                                scheduler.schedule(item, currentMedication?.approvalNumber)
                            } else {
                                scheduler.cancel(item)
                            }
                        }
                        alarmViewModel.saveModifications()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text(
                        text = "保存修改",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmCard(
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    subtitleColor: Color = Color(0xFF6B7280), // Gray500
    time: String,
    isActive: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    actionLabel: String,
    onAction: () -> Unit,
    isActionGray: Boolean = false
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F2937) // Gray800
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "删除",
                                tint = Color.Red.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable { onDelete() }
                            )
                        }
                        Text(
                            text = subtitle,
                            fontSize = 14.sp,
                            color = subtitleColor
                        )
                    }
                }
                Switch(
                    checked = isActive,
                    onCheckedChange = { onToggle(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Emerald600,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFE5E7EB) // Gray200
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = time,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Emerald600 else Color(0xFFD1D5DB) // Gray300 if inactive
                    )
                    if (isActive) {
                        Text(
                            text = " 每天重复",
                            fontSize = 12.sp,
                            color = Color(0xFF9CA3AF), // Gray400
                            modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActionGray) Color(0xFFF3F4F6) else Emerald50) // Gray100 or Emerald50
                        .clickable { onAction() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 14.sp,
                        color = if (isActionGray) Color(0xFF9CA3AF) else Emerald600 // Gray400 or Emerald600
                    )
                }
            }
        }
    }
}

@Composable
fun TimePickerDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        ),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(IntrinsicSize.Min)
                .height(IntrinsicSize.Min)
                .background(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp),
                    text = "选择用药时间",
                    style = MaterialTheme.typography.labelMedium
                )
                content()
                Row(
                    modifier = Modifier
                        .height(40.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    TextButton(onClick = onConfirm) { Text("确定") }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun ClockScreenPreview() {
    HealthManagerTheme {
        ClockScreen()
    }
}
