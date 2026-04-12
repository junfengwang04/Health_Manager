package com.pbz.healthmanager.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import android.util.Log
import com.pbz.healthmanager.ui.theme.Emerald100
import com.pbz.healthmanager.ui.theme.Emerald50
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import com.pbz.healthmanager.ui.theme.TextPrimary
import com.pbz.healthmanager.ui.theme.TextSecondary
import com.pbz.healthmanager.viewmodel.HealthViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MedicineScreen(
    viewModel: HealthViewModel,
    onScanClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val historyList by viewModel.recognitionHistory.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Emerald50)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .background(Emerald50)
                .padding(bottom = 24.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "识别药品",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
            }

            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                // Scan card
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(28.dp),
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .border(
                                width = 1.dp,
                                color = Color(0xFF10B981), // 使用你 HTML 里的绿色
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                                .background(Emerald100),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CameraAlt,
                                contentDescription = "camera",
                                tint = Emerald600,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                        Text(
                            text = "拍照识药",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "对准药盒或说明书，系统自动查询用法用量",
                            color = TextSecondary,
                            lineHeight = 20.sp,
                            fontSize = 14.sp
                        )
                        Button(
                            onClick = {
                                Log.d("MedicineScan", "start scan button clicked")
                                Toast.makeText(context, "进入扫描页", Toast.LENGTH_SHORT).show()
                                onScanClick?.invoke()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald600)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DocumentScanner,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Text(
                                    text = "开始扫描识别",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // History header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "搜索历史",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "clear",
                        tint = TextSecondary,
                        modifier = Modifier.clickable {
                            viewModel.clearRecognitionHistory()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (historyList.isEmpty()) {
                    Text(
                        text = "暂无搜索历史",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    historyList.forEachIndexed { index, item ->
                        val isManual = item.calorie >= 1.0
                        val accentColor = if (isManual) Color(0xFFEF4444) else Color(0xFF3B82F6)
                        val dateText = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(item.timestamp))
                        val actionText = if (isManual) "手动搜索" else "识别成功"
                        HistoryItem(
                            title = item.foodName,
                            subtitle = "$dateText $actionText",
                            accentColor = accentColor,
                            onDelete = { viewModel.deleteRecognitionHistoryById(item.id) }
                        )
                        if (index != historyList.lastIndex) {
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }
                }
             }
        }
    }
}

@Composable
private fun HistoryItem(
    title: String,
    subtitle: String,
    accentColor: Color,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.MedicalServices,
                    contentDescription = null,
                    tint = accentColor
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 16.sp
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除历史",
                tint = TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MedicineScreenPreview() {
    HealthManagerTheme {
        HistoryItem(
            title = "阿司匹林肠溶片",
            subtitle = "2026-01-18 识别成功",
            accentColor = Color(0xFF3B82F6),
            onDelete = {}
        )
    }
}
