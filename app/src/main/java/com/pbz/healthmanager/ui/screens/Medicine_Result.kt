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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmAdd
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.ui.theme.Emerald100
import com.pbz.healthmanager.ui.theme.Emerald50
import com.pbz.healthmanager.ui.theme.Emerald600
import com.pbz.healthmanager.ui.theme.HealthManagerTheme

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pbz.healthmanager.viewmodel.HealthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

@Composable
fun MedicineResultScreen(
    viewModel: HealthViewModel,
    onBackClick: () -> Unit = {},
    onManualSetClick: () -> Unit = {}
) {
    val medicine by viewModel.medicineResult.collectAsState()
    val capturedBitmap by viewModel.capturedMedicineBitmap.collectAsState()
    val scrollState = rememberScrollState()

    // Color definitions
    val Emerald800 = Color(0xFF065F46)
    val Emerald600 = Color(0xFF059669)
    val Emerald500 = Color(0xFF10B981)
    val Emerald50 = Color(0xFFECFDF5)
    val Gray50 = Color(0xFFF9FAFB)
    val Gray100 = Color(0xFFF3F4F6)
    val Gray200 = Color(0xFFE5E7EB)
    val Gray500 = Color(0xFF6B7280)
    val Gray600 = Color(0xFF4B5563)
    val Gray800 = Color(0xFF1F2937)
    val Red50 = Color(0xFFFFEBEE)
    val Red700 = Color(0xFFB91C1C)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Gray600,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBackClick() }
            )
            Text(
                text = medicine?.name ?: "识别结果",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Gray800
            )
            Spacer(modifier = Modifier.width(24.dp))
        }

        if (medicine == null) {
            // Placeholder hint
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Medication,
                        contentDescription = null,
                        tint = Gray200,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无药品识别结果",
                        fontSize = 18.sp,
                        color = Gray500
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onBackClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("返回重新扫描")
                    }
                }
            }
        } else {
            // Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp) // space-y-6
            ) {
                // Image Placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(192.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Gray100),
                    contentAlignment = Alignment.Center
                ) {
                    if (capturedBitmap != null) {
                        AsyncImage(
                            model = capturedBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(192.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Medication,
                            contentDescription = null,
                            tint = Emerald600.copy(alpha = 0.5f),
                            modifier = Modifier.size(96.dp)
                        )
                    }
                }

                // Medicine Details
                Column {
                    Text(
                        text = medicine!!.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Emerald800,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Approval Number
                        DetailItem(
                            label = "国药准字",
                            value = medicine!!.approvalNumber,
                            backgroundColor = Emerald50,
                            labelColor = Gray500,
                            valueColor = Gray800
                        )

                        // Manufacturer
                        DetailItem(
                            label = "生产厂家",
                            value = medicine!!.manufacturer.ifEmpty { "未知" },
                            backgroundColor = Emerald50,
                            labelColor = Gray500,
                            valueColor = Gray800
                        )

                        // Usage Logic: timesPerDay, dosePerTime, mealRelation
                        val usageText = "每日 ${medicine!!.timesPerDay} 次，每次 ${medicine!!.dosePerTime}，${medicine!!.mealRelation}服用"
                        DetailItem(
                            label = "用法用量",
                            value = usageText,
                            backgroundColor = Emerald50,
                            labelColor = Gray500,
                            valueColor = Gray800
                        )

                        // Contraindications
                        DetailItem(
                            label = "禁忌",
                            value = medicine!!.contraindications?.takeIf { it.isNotBlank() } ?: "尚无禁忌信息",
                            backgroundColor = Red50,
                            labelColor = Red700,
                            valueColor = Gray800,
                            isWarning = true
                        )
                    }
                }

                // Medication Alarm Section
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(32.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, Emerald500),
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "用药闹钟",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Emerald800
                            )
                            Icon(
                                imageVector = Icons.Filled.AlarmAdd,
                                contentDescription = null,
                                tint = Emerald500,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { onBackClick() },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Text(
                                    text = "确认录入",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            Button(
                                onClick = { onManualSetClick() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Emerald100,
                                    contentColor = Color(0xFF047857)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(64.dp)
                            ) {
                                Text(
                                    text = "手动调整",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailItem(
    label: String,
    value: String,
    backgroundColor: Color,
    labelColor: Color,
    valueColor: Color,
    modifier: Modifier = Modifier,
    isWarning: Boolean = false
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = labelColor,
            fontWeight = if (isWarning) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun MedicineResultScreenPreview() {
    HealthManagerTheme {
        MedicineResultScreen(viewModel = viewModel())
    }
}
