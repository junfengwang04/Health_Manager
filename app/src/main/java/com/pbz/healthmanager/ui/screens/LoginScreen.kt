package com.pbz.healthmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Elderly
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.SignalCellular4Bar
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import com.pbz.healthmanager.ui.theme.HealthManagerTheme

@Composable
fun LoginScreen(
    onElderlyModeClick: () -> Unit,
    onGuardianModeClick: () -> Unit
) {
    val currentDate = remember {
        val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
        dateFormat.format(java.util.Date())
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1e5d2c), // 深绿色
                            Color(0xFF3a7bd5)  // 蓝色
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 主内容区
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 重新设计的图标
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HealthAndSafety,
                                contentDescription = "健康管家",
                                tint = Color(0xFF1e5d2c), // 深绿色
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "健康管家",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.5).sp
                    )

                    Text(
                        text = "守护您和家人的健康 · 2026",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(64.dp))

                    // 模式选择卡片
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // 老年用户模式
                        Button(
                            onClick = { onElderlyModeClick() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentPadding = PaddingValues(all = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xFFFFF3E0)), // 浅橙色
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Elderly,
                                            contentDescription = "老年用户模式",
                                            tint = Color(0xFFFF8C00), // 橙色
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "老年用户模式",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF333333)
                                        )
                                        Text(
                                            text = "字体更大，界面更清爽",
                                            fontSize = 14.sp,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowRight,
                                    contentDescription = "进入",
                                    tint = Color(0xFFCCCCCC),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // 子女监护模式
                        Button(
                            onClick = { onGuardianModeClick() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.95f)
                            ),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentPadding = PaddingValues(all = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color(0xE0E8F5)), // 浅蓝色
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.People,
                                            contentDescription = "子女监护模式",
                                            tint = Color(0xFF3a7bd5), // 蓝色
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "子女监护模式",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFF333333)
                                        )
                                        Text(
                                            text = "智慧监控，实时数据共享",
                                            fontSize = 14.sp,
                                            color = Color(0xFF666666)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowRight,
                                    contentDescription = "进入",
                                    tint = Color(0xFFCCCCCC),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // 底部安全提示
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.Black.copy(alpha = 0.15f))
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = "安全防护",
                            tint = Color(0xFF4CAF50), // 绿色
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "$currentDate 安全防护中",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun LoginScreenPreview() {
    HealthManagerTheme {
        LoginScreen(
            onElderlyModeClick = {},
            onGuardianModeClick = {}
        )
    }
}