package com.pbz.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import androidx.compose.foundation.text.KeyboardOptions
import com.pbz.healthmanager.viewmodel.HealthViewModel

private data class GuardianUiModel(
    val name: String,
    val phone: String,
    val phoneMasked: String
)

@Composable
fun MonitorAccountScreen(
    viewModel: HealthViewModel,
    onBackClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val pageBg = Color(0xFFF8FAFC)
    val primaryBlue = Color(0xFF2563EB)

    var selectedBottomTab by remember { mutableIntStateOf(1) }
    var showAddSheet by remember { mutableStateOf(false) }
    var cancelBindingPhonePending by remember { mutableStateOf<String?>(null) }

    val guardianPhone by viewModel.currentGuardianPhone.collectAsState()
    val boundElders by viewModel.guardianBoundElders.collectAsState()
    val bindActionMessage by viewModel.bindActionMessage.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshGuardianData()
    }

    val guardians = boundElders.map {
        GuardianUiModel(
            name = it.name,
            phone = it.phone,
            phoneMasked = maskPhone(it.phone)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = pageBg,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(
                    selected = selectedBottomTab == 0,
                    onClick = onBackClick,
                    icon = { Icon(imageVector = Icons.Outlined.FavoriteBorder, contentDescription = "健康") },
                    label = { Text("健康") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryBlue,
                        selectedTextColor = primaryBlue,
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = primaryBlue.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedBottomTab == 1,
                    onClick = { selectedBottomTab = 1 },
                    icon = { Icon(imageVector = Icons.Outlined.PersonOutline, contentDescription = "我的") },
                    label = { Text("我的") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = primaryBlue,
                        selectedTextColor = primaryBlue,
                        unselectedIconColor = Color(0xFF94A3B8),
                        unselectedTextColor = Color(0xFF94A3B8),
                        indicatorColor = primaryBlue.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    Text(
                        text = "我的账户",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                }

                item {
                    ProfileCard(
                        name = "当前子女账号",
                        phoneMasked = maskPhone(guardianPhone),
                        badgeText = "高级监护人",
                        primaryBlue = primaryBlue
                    )
                }

                item {
                    GuardiansSection(
                        guardians = guardians,
                        onAddClick = { showAddSheet = true },
                        onCancelBinding = { phone -> cancelBindingPhonePending = phone },
                        primaryBlue = primaryBlue
                    )
                }

                item {
                    MenuSection(
                        onLogoutClick = onLogoutClick
                    )
                }

                item { Spacer(modifier = Modifier.height(10.dp)) }
            }

            if (showAddSheet) {
                AddGuardianBottomSheet(
                    primaryBlue = primaryBlue,
                    onDismiss = { showAddSheet = false },
                    onConfirm = { deviceCode ->
                        viewModel.submitBindRequestByDeviceCode(deviceCode)
                        showAddSheet = false
                    }
                )
            }
            if (bindActionMessage.isNotBlank()) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { viewModel.clearBindActionMessage() },
                    title = { Text("提示") },
                    text = { Text(bindActionMessage) },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearBindActionMessage() }) {
                            Text("知道了")
                        }
                    }
                )
            }
            if (cancelBindingPhonePending != null) {
                val phone = cancelBindingPhonePending.orEmpty()
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { cancelBindingPhonePending = null },
                    title = { Text("确认取消绑定") },
                    text = { Text("是否确定取消绑定【$phone】？") },
                    confirmButton = {
                        TextButton(onClick = {
                            cancelBindingPhonePending = null
                            viewModel.cancelBindRequestFromGuardian(phone)
                        }) { Text("确定") }
                    },
                    dismissButton = {
                        TextButton(onClick = { cancelBindingPhonePending = null }) { Text("取消") }
                    }
                )
            }
        }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    phoneMasked: String,
    badgeText: String,
    primaryBlue: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = primaryBlue,
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4ADE80))
                        .border(
                            width = 2.dp,
                            color = primaryBlue
                        )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = phoneMasked,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun GuardiansSection(
    guardians: List<GuardianUiModel>,
    onAddClick: () -> Unit,
    onCancelBinding: (String) -> Unit,
    primaryBlue: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "当前监护对象",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "共 ${guardians.size} 位",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFF1F5F9))
        ) {
            Column(
                modifier = Modifier.padding(8.dp)
            ) {
                guardians.forEachIndexed { index, guardian ->
                    GuardianRow(
                        name = guardian.name,
                        phoneMasked = guardian.phoneMasked,
                        onCancel = { onCancelBinding(guardian.phone) }
                    )
                    if (index != guardians.lastIndex) {
                        Spacer(modifier = Modifier.height(1.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Color(0xFFF1F5F9))
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                    }
                }
            }
        }

        DashedAddButton(
            text = "添加监护对象",
            primaryBlue = primaryBlue,
            onClick = onAddClick
        )
    }
}

@Composable
private fun GuardianRow(
    name: String,
    phoneMasked: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFE2E8F0)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Person,
                contentDescription = null,
                tint = Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

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
                text = phoneMasked,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF94A3B8)
            )
        }

        Icon(
            imageVector = Icons.Outlined.Shield,
            contentDescription = null,
            tint = Color(0xFF22C55E),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Surface(
            color = Color(0xFFFEE2E2),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.clickable { onCancel() }
        ) {
            Text(
                text = "取消绑定",
                color = Color(0xFFB91C1C),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun DashedAddButton(
    text: String,
    primaryBlue: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dashColor = Color(0xFFBFDBFE)
    val bg = Color(0xFFEFF6FF)
    Surface(
        color = bg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable { onClick() }
            .drawBehind {
                val strokeWidth = 2.dp.toPx()
                val inset = strokeWidth / 2f
                drawRoundRect(
                    color = dashColor,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                    )
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AddCircleOutline,
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = primaryBlue
            )
        }
    }
}

@Composable
private fun MenuSection(
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 1.dp,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF1F5F9))
    ) {
        Column {
            MenuRow(
                icon = Icons.Outlined.Logout,
                iconBg = Color(0xFFFFE4E6),
                iconTint = Color(0xFFEF4444),
                text = "退出登录",
                textColor = Color(0xFFEF4444),
                onClick = onLogoutClick,
                showChevron = false
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    text: String,
    textColor: Color,
    onClick: () -> Unit,
    showChevron: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color(0xFFCBD5E1),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFF1F5F9))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGuardianBottomSheet(
    primaryBlue: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var deviceCodeInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 14.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(56.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFE2E8F0))
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 26.dp)
        ) {
            Text(
                text = "添加绑定",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请输入老人端的六位设备码，提交后会发送绑定请求到老人端确认。",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = TextSecondary,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                color = Color(0xFFF1F5F9),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Phone,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = deviceCodeInput,
                        onValueChange = { input ->
                            deviceCodeInput = input.filter { it.isDigit() }.take(6)
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF334155)
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (deviceCodeInput.isBlank()) {
                                Text(
                                    text = "请输入6位设备码",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            innerTextField()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onDismiss() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "取消",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                    }
                }

                Surface(
                    color = primaryBlue,
                    shape = RoundedCornerShape(18.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
                        .clickable { onConfirm(deviceCodeInput) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "确认绑定",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MonitorAccountScreenPreview() {
}

private fun maskPhone(phone: String): String {
    if (phone.length < 7) return phone
    return "${phone.substring(0, 3)} **** ${phone.substring(phone.length - 4)}"
}
