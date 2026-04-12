package com.pbz.healthmanager.ui.screens

import android.media.AudioManager
import android.content.Intent
import android.net.Uri
import android.app.TimePickerDialog
import android.speech.tts.TextToSpeech
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.pbz.healthmanager.alarm.AlarmScheduler
import com.pbz.healthmanager.data.local.model.AlarmItem
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import com.pbz.healthmanager.data.remote.pb.PbBindRequest
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import com.pbz.healthmanager.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.Date

import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(
    viewModel: HealthViewModel,
    onSwitchFamilyClick: (() -> Unit)? = null,
    onAddMedicineClick: (() -> Unit)? = null,
    onTabChange: ((Int) -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(0) }
    var selectedMedicineApproval by remember { mutableStateOf<String?>(null) }
    val alarmBindings by viewModel.activeAlarmMedicationBindings.collectAsState()
    val todayLogs by viewModel.todayMedicationLogs.collectAsState()
    val allLogs by viewModel.allMedicationLogs.collectAsState()
    val aiSummaryText by viewModel.aiHealthSummaryText.collectAsState()
    val aiSummaryFromQwen by viewModel.aiSummaryFromQwen.collectAsState()
    val aiSummaryStatus by viewModel.aiSummaryStatus.collectAsState()
    val aiSummaryErrorDetail by viewModel.aiSummaryErrorDetail.collectAsState()
    val currentLoginDisplayName by viewModel.currentLoginDisplayName.collectAsState()
    val currentElderDeviceCode by viewModel.currentElderDeviceCode.collectAsState()
    val pendingBindRequests by viewModel.pendingBindRequests.collectAsState()
    val bindActionMessage by viewModel.bindActionMessage.collectAsState()
    var showDeviceCodeDialog by remember { mutableStateOf(false) }
    var showBindRequestDialog by remember { mutableStateOf(false) }
    var cancelBindPhonePending by remember { mutableStateOf<String?>(null) }
    val activeMedicineCards = remember(alarmBindings, todayLogs, allLogs) {
        alarmBindings
            .groupBy { it.medication.approvalNumber }
            .map { (_, rows) ->
                val medication = rows.first().medication
                val bindings = rows.map { it.binding }
                MedicineSummaryCard(
                    name = medication.name,
                    tint = colorForMedication(medication.approvalNumber),
                    detail = buildMedicineDetailData(
                        medication = medication,
                        bindings = bindings,
                        todayLogs = todayLogs,
                        allLogs = allLogs
                    )
                )
            }
    }
    val historyMedicineCards = remember(activeMedicineCards, allLogs) {
        val activeApprovals = activeMedicineCards.map { it.detail.approvalNumber }.toSet()
        allLogs
            .groupBy { it.log.medicationApprovalNumber }
            .filterKeys { it !in activeApprovals }
            .map { (_, rows) ->
                val medication = rows.first().medication
                MedicineSummaryCard(
                    name = medication.name,
                    tint = colorForMedication(medication.approvalNumber),
                    detail = buildMedicineDetailData(
                        medication = medication,
                        bindings = emptyList(),
                        todayLogs = todayLogs,
                        allLogs = allLogs
                    )
                )
            }
    }
    val allMedicineCards = remember(activeMedicineCards, historyMedicineCards) {
        (activeMedicineCards + historyMedicineCards)
            .associateBy { it.detail.approvalNumber }
            .values
            .toList()
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.refreshAiSummaryNow()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.refreshPendingBindRequestsForElder()
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        if (selectedMedicineApproval != null) {
            val selected = allMedicineCards.firstOrNull { it.detail.approvalNumber == selectedMedicineApproval }?.detail
            if (selected == null) {
                selectedMedicineApproval = null
            } else {
            MedicineDetailPage(
                    detail = selected,
                    onBack = { selectedMedicineApproval = null }
            )
            return@Surface
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF7F8FA))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Emerald600)
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .border(3.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "👴", fontSize = 28.sp)
                            }
                            Text(
                                text = currentLoginDisplayName.ifBlank { "当前账号" },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { onSwitchFamilyClick?.invoke() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Group,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "切家人",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }


                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { showDeviceCodeDialog = true }
                        ) {
                            Text(
                                "设备码",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable { showBindRequestDialog = true }
                        ) {
                            Text(
                                "绑定请求",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(1.dp, Color(0xFFF1F3F5))
            ) {
                ReportTab(
                    text = "💊 吃的药品",
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        onTabChange?.invoke(0)
                    },
                    modifier = Modifier.weight(1f)
                )
                ReportTab(
                    text = "📊 总结报告",
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        onTabChange?.invoke(1)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            if (selectedTab == 0) {
                MedicinePage(
                    activeMedicines = activeMedicineCards,
                    historyMedicines = historyMedicineCards,
                    onAddMedicineClick = onAddMedicineClick,
                    onMedicineClick = { selectedMedicineApproval = it.approvalNumber }
                )
            } else {
                SummaryPage(
                    summaryText = aiSummaryText,
                    fromQwen = aiSummaryFromQwen,
                    statusText = aiSummaryStatus,
                    errorDetail = aiSummaryErrorDetail,
                    onRefreshAi = { viewModel.refreshAiSummaryNow() }
                )
            }
        }
        if (showDeviceCodeDialog) {
            AlertDialog(
                onDismissRequest = { showDeviceCodeDialog = false },
                title = { Text("您的设备码") },
                text = { Text(currentElderDeviceCode.ifBlank { "正在生成..." }, fontSize = 26.sp, fontWeight = FontWeight.Bold) },
                confirmButton = {
                    TextButton(onClick = { showDeviceCodeDialog = false }) {
                        Text("知道了")
                    }
                }
            )
        }
        if (showBindRequestDialog) {
            AlertDialog(
                onDismissRequest = { showBindRequestDialog = false },
                title = { Text("绑定请求与绑定记录") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        val visibleRequests = pendingBindRequests.filter { bindStatusLabel(it) != "已取消" }
                        if (visibleRequests.isEmpty()) {
                            Text("暂无绑定请求")
                        } else {
                            visibleRequests.forEach { request ->
                                val statusText = bindStatusLabel(request)
                                val isActive = statusText == "已绑定"
                                val textColor = if (isActive) Color.Black else Color(0xFF92400E)
                                Surface(
                                    color = if (isActive) Color(0xFFF3F4F6) else Color(0xFFFFFBEB),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "手机号：${request.guardianPhone}",
                                            color = textColor,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "状态：$statusText",
                                            color = textColor,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            if (statusText == "待确认") {
                                                Button(
                                                    onClick = { viewModel.approveBindRequest(request.guardianPhone) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) { Text("确定绑定", color = Color.White, fontSize = 12.sp) }
                                            }
                                            Button(
                                                onClick = { cancelBindPhonePending = request.guardianPhone },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                                                shape = RoundedCornerShape(10.dp)
                                            ) { Text("取消绑定", color = Color.White, fontSize = 14.sp) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBindRequestDialog = false }) {
                        Text("关闭")
                    }
                }
            )
        }
        if (bindActionMessage.isNotBlank()) {
            AlertDialog(
                onDismissRequest = { viewModel.clearBindActionMessage() },
                title = { Text("提示") },
                text = { Text(bindActionMessage) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearBindActionMessage() }) { Text("确定") }
                }
            )
        }
        if (cancelBindPhonePending != null) {
            val phone = cancelBindPhonePending.orEmpty()
            AlertDialog(
                onDismissRequest = { cancelBindPhonePending = null },
                title = { Text("确认取消绑定") },
                text = { Text("是否确定取消绑定【$phone】？") },
                confirmButton = {
                    TextButton(onClick = {
                        cancelBindPhonePending = null
                        viewModel.cancelBindRequestFromElder(phone)
                    }) {
                        Text("确定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { cancelBindPhonePending = null }) { Text("取消") }
                }
            )
        }
    }
}

@Composable
private fun ReportTab(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            fontSize = 30.sp / 1.5f,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (selected) Color(0xFF065F46) else Color(0xFF9CA3AF)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(if (selected) Color(0xFF10B981) else Color.Transparent)
        )
    }
}

@Composable
private fun MedicinePage(
    activeMedicines: List<MedicineSummaryCard>,
    historyMedicines: List<MedicineSummaryCard>,
    onAddMedicineClick: (() -> Unit)? = null,
    onMedicineClick: (MedicineDetailData) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("正在服用 ${activeMedicines.size} 种", fontSize = 16.sp, color = TextSecondary)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onAddMedicineClick?.invoke() }
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = Emerald600,
                    modifier = Modifier.size(22.dp)
                )
                Text("添加", color = Emerald600, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }

        if (activeMedicines.isEmpty()) {
            Text(
                text = "暂未设置用药闹钟，请先在闹钟页为药品设置提醒",
                color = TextSecondary,
                fontSize = 17.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            activeMedicines.forEach { item ->
                ReportMedicineCard(
                    name = item.name,
                    tint = item.tint,
                    onClick = { onMedicineClick(item.detail) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "历史服用药",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        if (historyMedicines.isEmpty()) {
            Text(
                text = "暂无历史服药记录",
                color = TextSecondary,
                fontSize = 16.sp
            )
        } else {
            historyMedicines.forEach { item ->
                ReportMedicineCard(
                    name = item.name,
                    tint = item.tint,
                    onClick = { onMedicineClick(item.detail) }
                )
            }
        }
    }
}

@Composable
private fun ReportMedicineCard(
    name: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MedicalServices,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(30.dp)
                )
            }
            Text(
                text = name,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A),
                modifier = Modifier.weight(1f)
            )
            Text("›", fontSize = 30.sp, color = Color(0xFFCBD5E1), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MedicineDetailPage(
    detail: MedicineDetailData,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scheduler = remember { AlarmScheduler(context) }
    var showExportDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(Color(0xFFF7F8FA))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF3F4F6))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ArrowBack,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = detail.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (detail.showTodayStatus) {
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(28.dp),
                        tonalElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("今天服药状态", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                Text(todayDateText(), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Emerald600)
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                detail.todayStatuses.forEach { (label, status) ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        StatusBox(status = status)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(label, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            AlarmSettingSection(
                                detail = detail,
                                scheduler = scheduler
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = Color(0xFF1D4ED8),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("用药说明", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1D4ED8))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = detail.instruction,
                            fontSize = 20.sp,
                            color = Color(0xFF1E3A8A),
                            lineHeight = 32.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("往日记录", fontSize = 40.sp / 1.5f, fontWeight = FontWeight.ExtraBold, color = TextPrimary, modifier = Modifier.padding(horizontal = 6.dp))
                    TextButton(onClick = { showExportDialog = true }) {
                        Text("一键导出", fontSize = 16.sp, color = Emerald600, fontWeight = FontWeight.Bold)
                    }
                }

                detail.history.forEach { item ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(24.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (item.ok) Modifier else Modifier.border(2.dp, Color(0xFFEF4444), RoundedCornerShape(24.dp)))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(item.date, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                                Text(
                                    item.result,
                                    fontSize = 18.sp,
                                    color = if (item.ok) Emerald600 else Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Icon(
                                imageVector = if (item.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = if (item.ok) Emerald600 else Color(0xFFEF4444),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                if (showExportDialog) {
                    AlertDialog(
                        onDismissRequest = { showExportDialog = false },
                        title = { Text("导出服药记录", fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                        text = { Text(detail.exportText, fontSize = 15.sp, color = TextPrimary) },
                        confirmButton = {
                            TextButton(onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "${detail.title}服药记录")
                                    putExtra(Intent.EXTRA_TEXT, detail.exportText)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "导出服药记录"))
                                showExportDialog = false
                            }) { Text("导出分享", fontSize = 16.sp) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showExportDialog = false }) { Text("关闭", fontSize = 16.sp) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmSettingSection(
    detail: MedicineDetailData,
    scheduler: AlarmScheduler
) {
    val context = LocalContext.current
    val current = detail.alarmBindings.associateBy { it.periodName }
    val periodNames = linkedSetOf("早晨用药", "中午用药", "晚上用药").apply {
        addAll(detail.alarmBindings.map { it.periodName })
    }.toList()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("设置闹钟", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
        periodNames.forEach { period ->
            val binding = current[period]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("$period  ${binding?.alarmTime ?: "--:--"}", fontSize = 16.sp, color = TextPrimary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (binding == null) "设置" else "更新",
                        color = Emerald600,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val initialHour = binding?.alarmTime?.substringBefore(":")?.toIntOrNull() ?: 8
                            val initialMin = binding?.alarmTime?.substringAfter(":")?.toIntOrNull() ?: 0
                            TimePickerDialog(
                                context,
                                { _, hour, minute ->
                                    val timeText = String.format("%02d:%02d", hour, minute)
                                    val alarmId = binding?.alarmId ?: "${detail.approvalNumber}_${period.hashCode()}"
                                    scheduler.schedule(
                                        item = AlarmItem(
                                            id = alarmId,
                                            periodName = period,
                                            suggestion = "饭后半小时",
                                            time = timeText,
                                            isActive = true
                                        ),
                                        medicationApprovalNumber = detail.approvalNumber
                                    )
                                },
                                initialHour,
                                initialMin,
                                true
                            ).show()
                        }
                    )
                    if (binding != null) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "关闭",
                            color = Color(0xFFEF4444),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                scheduler.cancel(
                                    AlarmItem(
                                        id = binding.alarmId,
                                        periodName = binding.periodName,
                                        suggestion = "饭后半小时",
                                        time = binding.alarmTime,
                                        isActive = false
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBox(status: TakeState) {
    val bg = when (status) {
        TakeState.DONE -> Color(0xFFECFDF5)
        TakeState.MISSED -> Color(0xFFFEF2F2)
        TakeState.PENDING -> Color.White
    }
    val border = when (status) {
        TakeState.DONE -> Color(0xFF10B981)
        TakeState.MISSED -> Color(0xFFEF4444)
        TakeState.PENDING -> Color(0xFFE5E7EB)
    }
    val tint = when (status) {
        TakeState.DONE -> Color(0xFF10B981)
        TakeState.MISSED -> Color(0xFFEF4444)
        TakeState.PENDING -> Color(0xFF9CA3AF)
    }
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(3.dp, border, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (status == TakeState.DONE) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
        } else if (status == TakeState.MISSED) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = tint, modifier = Modifier.size(38.dp))
        }
    }
}

private data class MedicineSummaryCard(
    val name: String,
    val tint: Color,
    val detail: MedicineDetailData
)

private data class MedicineDetailData(
    val approvalNumber: String,
    val title: String,
    val instruction: String,
    val showTodayStatus: Boolean,
    val alarmBindings: List<MedicationAlarmBinding>,
    val todayStatuses: List<Pair<String, TakeState>>,
    val history: List<MedicineHistoryItem>,
    val exportText: String
)

private data class MedicineHistoryItem(
    val date: String,
    val result: String,
    val ok: Boolean
)

private enum class TakeState {
    DONE,
    MISSED,
    PENDING
}

private fun buildMedicineDetailData(
    medication: Medication,
    bindings: List<MedicationAlarmBinding>,
    todayLogs: List<MedicationLogWithInfo>,
    allLogs: List<MedicationLogWithInfo>
): MedicineDetailData {
    val byTime = todayLogs
        .filter { it.log.medicationApprovalNumber == medication.approvalNumber }
        .associateBy { hmText(it.log.scheduledTime) }

    val todayStatuses = bindings
        .sortedBy { it.alarmTime }
        .map { binding ->
            val state = when (byTime[binding.alarmTime]?.log?.status) {
                "已服用" -> TakeState.DONE
                "本次不吃", "已跳过" -> TakeState.MISSED
                else -> TakeState.PENDING
            }
            binding.periodName to state
        }

    val historyByDay = allLogs
        .filter { it.log.medicationApprovalNumber == medication.approvalNumber }
        .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.log.scheduledTime)) }
        .toList()
        .sortedByDescending { it.first }

    val history = historyByDay
        .take(5)
        .map { (_, rows) ->
            val done = rows.count { it.log.status == "已服用" }
            val missed = rows.count { it.log.status == "本次不吃" || it.log.status == "已跳过" }
            val pending = rows.count { it.log.status != "已服用" && it.log.status != "本次不吃" && it.log.status != "已跳过" }
            val dayTime = rows.maxOfOrNull { it.log.scheduledTime } ?: 0L
            MedicineHistoryItem(
                date = SimpleDateFormat("M月d日 (E)", Locale.CHINA).format(Date(dayTime)),
                result = when {
                    missed == 0 && pending == 0 -> "当日服药 $done 次，全部按时服用 √"
                    else -> "当日服药 $done 次，未服/跳过 $missed 次，待确认 $pending 次"
                },
                ok = missed == 0 && pending == 0
            )
        }

    val exportText = buildString {
        appendLine("${medication.name} - 全部服药记录")
        historyByDay.forEach { (_, rows) ->
            val done = rows.count { it.log.status == "已服用" }
            val missed = rows.count { it.log.status == "本次不吃" || it.log.status == "已跳过" }
            val pending = rows.count { it.log.status != "已服用" && it.log.status != "本次不吃" && it.log.status != "已跳过" }
            val dayTime = rows.maxOfOrNull { it.log.scheduledTime } ?: 0L
            val dateText = SimpleDateFormat("yyyy年M月d日 E", Locale.CHINA).format(Date(dayTime))
            appendLine("$dateText：服药$done 次，未服/跳过 $missed 次，待确认 $pending 次")
        }
    }

    return MedicineDetailData(
        approvalNumber = medication.approvalNumber,
        title = medication.name,
        instruction = "每日 ${medication.timesPerDay} 次，${medication.mealRelation}服用，每次 ${medication.dosePerTime}。",
        showTodayStatus = bindings.isNotEmpty(),
        alarmBindings = bindings.sortedBy { it.alarmTime },
        todayStatuses = when {
            todayStatuses.isNotEmpty() -> todayStatuses
            history.isNotEmpty() -> listOf("当前无闹钟" to TakeState.PENDING)
            else -> listOf("今日无闹钟" to TakeState.PENDING)
        },
        history = history,
        exportText = exportText
    )
}

private fun hmText(time: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(time))

private fun todayDateText(): String = SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date())

private fun bindStatusLabel(request: PbBindRequest): String {
    return when (request.status.uppercase()) {
        "ACTIVE" -> "已绑定"
        "INACTIVE" -> "待确认"
        else -> "待确认"
    }
}

private fun colorForMedication(key: String): Color {
    val colors = listOf(
        Color(0xFF10B981),
        Color(0xFFF97316),
        Color(0xFF3B82F6),
        Color(0xFFA855F7),
        Color(0xFFEF4444),
        Color(0xFFEAB308)
    )
    val index = kotlin.math.abs(key.hashCode()) % colors.size
    return colors[index]
}

@Composable
private fun SummaryPage(
    summaryText: String,
    fromQwen: Boolean,
    statusText: String,
    errorDetail: String,
    onRefreshAi: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var ttsReady by remember { mutableStateOf(false) }
    val tts = remember<TextToSpeech?> {
        var ref: TextToSpeech? = null
        ref = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val candidates = listOf(Locale.SIMPLIFIED_CHINESE, Locale.CHINA, Locale.CHINESE, Locale.getDefault())
                val supported = candidates.firstOrNull { locale ->
                    val result = ref?.setLanguage(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
                    result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
                }
                ttsReady = supported != null
            } else {
                ttsReady = false
            }
        }
        ref
    }
    DisposableEffect(tts) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    fun speakSummary() {
        if (!ttsReady) {
            Toast.makeText(context, "语音引擎初始化中，先尝试播报", Toast.LENGTH_SHORT).show()
            scope.launch {
                delay(900)
                val retryResult = tts?.speak(summaryText, TextToSpeech.QUEUE_FLUSH, null, "health_summary_retry")
                if (retryResult == TextToSpeech.SUCCESS) {
                    Toast.makeText(context, "语音播报已开始", Toast.LENGTH_SHORT).show()
                }
            }
        }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val current = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: current
        val target = kotlin.math.max(current, (max * 0.7f).toInt())
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        tts?.setSpeechRate(0.82f)
        tts?.setPitch(1.0f)
        val result = tts?.speak(summaryText, TextToSpeech.QUEUE_FLUSH, null, "health_summary_report")
        if (result == TextToSpeech.ERROR) {
            Toast.makeText(context, "播报失败，正在打开语音引擎安装入口", Toast.LENGTH_SHORT).show()
            val launchCandidates = listOf(
                Intent("com.android.settings.TTS_SETTINGS"),
                Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
                Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA),
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")),
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))
            )
            val launched = launchCandidates.firstOrNull { intent ->
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.resolveActivity(context.packageManager) != null
            }?.let { intent ->
                runCatching { context.startActivity(intent) }.isSuccess
            } ?: false
            if (!launched) {
                Toast.makeText(context, "未找到可用安装入口，请手动安装语音引擎", Toast.LENGTH_LONG).show()
            }
        }
    }




    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = Color(0xFFECFDF5),
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("AI 健康管家", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
                    Surface(
                        color = Emerald600,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier.clickable { speakSummary() }
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VolumeUp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = summaryText,
                    fontSize = 18.sp,
                    color = Color(0xFF1E293B),
                    lineHeight = 27.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "使用Qwen3.5-Flash总结所得",
                    fontSize = 13.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.clickable { onRefreshAi() }
                )
                if (errorDetail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = errorDetail.take(140),
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
            }
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(26.dp),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("血压趋势 (本周)", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = TextPrimary)
                Spacer(modifier = Modifier.height(10.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                ) {
                    val values = listOf(120f, 118f, 135f, 122f, 119f, 121f, 118f)
                    val minV = 110f
                    val maxV = 140f
                    val left = 18f
                    val right = size.width - 10f
                    val top = 8f
                    val bottom = size.height - 20f
                    val stepX = (right - left) / (values.size - 1)
                    fun y(v: Float): Float = bottom - ((v - minV) / (maxV - minV)) * (bottom - top)
                    repeat(4) { i ->
                        val gy = top + (bottom - top) * i / 3f
                        drawLine(
                            color = Color(0xFFE5E7EB),
                            start = Offset(left, gy),
                            end = Offset(right, gy),
                            strokeWidth = 1.5f
                        )
                    }
                    var prev: Offset? = null
                    values.forEachIndexed { index, value ->
                        val p = Offset(left + stepX * index, y(value))
                        if (prev != null) {
                            drawLine(
                                color = Color(0xFFEF4444),
                                start = prev!!,
                                end = p,
                                strokeWidth = 5f,
                                cap = StrokeCap.Round
                            )
                        }
                        drawCircle(color = Color.White, radius = 6f, center = p)
                        drawCircle(color = Color(0xFFEF4444), radius = 4.2f, center = p)
                        prev = p
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(color = Color(0xFFFEF2F2), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = "⚠️ 预警：周三波动较大，建议减少盐分摄入。",
                        color = Color(0xFFB91C1C),
                        fontSize = 16.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun ReportScreenPreview() {
    HealthManagerTheme {
        SummaryPage(
            summaryText = "健康管家总结：近7天预计服药14次，已服药12次，未服2次，服药完成率约85%。近7天平均血压约132/82mmHg，超标1次。建议：保持清淡饮食，按时服药，晨起继续测压。",
            false,
            statusText = "预览",
            errorDetail = "",
            onRefreshAi = {},
        )
    }
}
