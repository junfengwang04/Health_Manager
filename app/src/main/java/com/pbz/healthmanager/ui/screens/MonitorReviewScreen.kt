package com.pbz.healthmanager.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import com.pbz.healthmanager.viewmodel.GuardianMonitorSummary
import com.pbz.healthmanager.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private data class MonitorPersonUiModel(
    val name: String,
    val relationAndAge: String = "已绑定监护对象",
    val statusText: String,
    val statusStyle: StatusStyle,
    val bloodPressure: String,
    val medicineTaken: String,
    val phone: String
)

private enum class StatusStyle {
    Danger,
    Success,
    Info
}

@Composable
fun MonitorReviewScreen(
    viewModel: HealthViewModel,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = Color(0xFFF3F6FF)
    val primaryBlue = Color(0xFF2563EB)
    val boundElders by viewModel.guardianBoundElders.collectAsState()
    val boundElderPhone by viewModel.boundElderAccount.collectAsState()
    val guardianSummaries by viewModel.guardianMonitorSummaries.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshGuardianData()
    }
    LaunchedEffect(boundElders.size, boundElderPhone) {
        viewModel.refreshGuardianData()
    }
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshGuardianData()
            delay(4000L)
        }
    }

    val monitorItems = guardianSummaries.map { summary -> summary.toUiModel() }
    val currentElderPhone = boundElderPhone.ifBlank { boundElders.firstOrNull()?.phone.orEmpty() }
    val selectedCard = monitorItems.firstOrNull { it.phone == currentElderPhone } ?: monitorItems.firstOrNull()
    val elderPhone = selectedCard?.phone.orEmpty()
    val dangerItems = monitorItems.filter { it.statusStyle == StatusStyle.Danger }
    val medicineAlertItems = monitorItems.filter { isMedicineDanger(it.medicineTaken) }
    val noDataItems = monitorItems.filter { it.statusText == "未同步" }

    var detailName by remember { mutableStateOf<String?>(null) }
    if (detailName != null) {
        MonitorDetailsScreen(
            viewModel = viewModel,
            name = detailName!!,
            onBackClick = { detailName = null },
            modifier = modifier
        )
        return
    }

    var selectedBottomTab by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = background,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedBottomTab == 0,
                    onClick = { selectedBottomTab = 0 },
                    icon = { Icon(imageVector = Icons.Outlined.FavoriteBorder, contentDescription = "健康") },
                    label = { Text(text = "健康") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryBlue,
                        selectedTextColor = primaryBlue,
                        unselectedIconColor = Color(0xFF9CA3AF),
                        unselectedTextColor = Color(0xFF9CA3AF),
                        indicatorColor = primaryBlue.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedBottomTab == 1,
                    onClick = onAccountClick,
                    icon = { Icon(imageVector = Icons.Outlined.PersonOutline, contentDescription = "我的") },
                    label = { Text(text = "我的") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryBlue,
                        selectedTextColor = primaryBlue,
                        unselectedIconColor = Color(0xFF9CA3AF),
                        unselectedTextColor = Color(0xFF9CA3AF),
                        indicatorColor = primaryBlue.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .padding(innerPadding)
        ) {
            TopBar(
                title = "健康监控",
                modifier = Modifier.fillMaxWidth()
            )

            if (elderPhone.isBlank()) {
                WarningBanner(
                    text = "当前账号还未绑定老人，请到“我的账户”完成绑定",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                )
            } else if (dangerItems.isNotEmpty()) {
                WarningBanner(
                    text = "警告：${dangerItems.joinToString("、") { it.name }} 存在血压异常，请尽快查看",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                )
            } else if (medicineAlertItems.isNotEmpty()) {
                WarningBanner(
                    text = "提醒：${medicineAlertItems.joinToString("、") { it.name }} 今日服药未完成，请关注",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                )
            } else if (noDataItems.isNotEmpty()) {
                WarningBanner(
                    text = "提醒：${noDataItems.joinToString("、") { it.name }} 暂无同步数据",
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(monitorItems) { person ->
                    MonitorCard(
                        person = person,
                        onClick = {
                            if (person.phone.isNotBlank()) {
                                viewModel.selectGuardianElderForDetails(person.phone) {
                                    detailName = person.name
                                }
                            }
                        }
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
private fun TopBar(
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
private fun WarningBanner(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFFFF7ED),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFFEDD5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF97316),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB45309)
            )
        }
    }
}

@Composable
private fun MonitorCard(
    person: MonitorPersonUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF1F5F9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = person.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = person.relationAndAge,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }

                StatusChip(
                    text = person.statusText,
                    style = person.statusStyle
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "最近血压",
                    value = person.bloodPressure,
                    unit = "mmHg",
                    valueColor = if (person.statusStyle == StatusStyle.Danger) Color(0xFFEF4444) else TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "今日服药",
                    value = person.medicineTaken,
                    unit = "次",
                    valueColor = Color(0xFF2563EB),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    style: StatusStyle,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (style) {
        StatusStyle.Danger -> Color(0xFFFFF1F2) to Color(0xFFEF4444)
        StatusStyle.Success -> Color(0xFFECFDF3) to Color(0xFF22C55E)
        StatusStyle.Info -> Color(0xFFEFF6FF) to Color(0xFF3B82F6)
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = valueColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }
        }
    }
}

private fun buildBloodPressureSeries(indices: List<HealthIndex>): List<String> {
    return indices
        .asSequence()
        .filter { it.type == "血压" }
        .sortedBy { it.measureTime }
        .mapNotNull { toBloodPressureText(it) }
        .toList()
}

private fun toBloodPressureText(index: HealthIndex): String? {
    val remark = index.remark.orEmpty()
    if (remark.contains("/")) {
        val value = remark.substringAfter("DEMO_BP|", remark).trim()
        if (value.contains("/")) return value
    }
    val systolic = index.value.toInt()
    return "$systolic/--"
}

private fun isBloodPressureDanger(bp: String): Boolean {
    val parts = bp.split("/")
    val s = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val d = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return s >= 140 || d >= 90
}

private fun buildMedicineProgress(logs: List<MedicationLogWithInfo>): String {
    if (logs.isEmpty()) return "0/0"
    val taken = logs.count { it.log.status.contains("已服") }
    return "$taken/${logs.size}"
}

private fun isMedicineDanger(progress: String): Boolean {
    val parts = progress.split("/")
    val taken = parts.getOrNull(0)?.toIntOrNull() ?: return false
    val total = parts.getOrNull(1)?.toIntOrNull() ?: return false
    if (total <= 0) return false
    return taken < total
}

private fun GuardianMonitorSummary.toUiModel(): MonitorPersonUiModel {
    val status = when {
        !hasData -> "未同步"
        bloodPressureDanger -> "数据异常"
        else -> "状态良好"
    }
    val style = when {
        !hasData -> StatusStyle.Info
        bloodPressureDanger -> StatusStyle.Danger
        else -> StatusStyle.Success
    }
    return MonitorPersonUiModel(
        name = elderName,
        statusText = status,
        statusStyle = style,
        bloodPressure = latestBloodPressure,
        medicineTaken = medicineProgress,
        phone = elderPhone
    )
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MonitorReviewScreenPreview() {
}
