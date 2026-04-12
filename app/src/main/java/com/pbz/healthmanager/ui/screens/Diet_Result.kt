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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pbz.healthmanager.ui.theme.HealthManagerTheme

import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import coil.compose.AsyncImage
import com.pbz.healthmanager.viewmodel.HealthViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.layout.ContentScale

@Composable
fun DishResultScreen(
    viewModel: HealthViewModel,
    onBackClick: () -> Unit = {},
    onRecordClick: () -> Unit = {}
) {
    val diet by viewModel.dietResult.collectAsState()
    val capturedBitmap by viewModel.capturedDietBitmap.collectAsState()
    val scrollState = rememberScrollState()
    
    // 如果没有数据，显示占位
    if (diet == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("未获取到识别结果")
        }
        return
    }

    // Color definitions based on the HTML/Tailwind classes
    val Emerald50 = Color(0xFFECFDF5)
    val Emerald100 = Color(0xFFD1FAE5)
    val Emerald500 = Color(0xFF10B981)
    val Emerald600 = Color(0xFF059669)
    val Gray600 = Color(0xFF4B5563)
    val Gray800 = Color(0xFF1F2937)
    val Orange50 = Color(0xFFFFF7ED)
    val Orange100 = Color(0xFFFFEDD5)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        // Header
        // <div class="h-14 bg-white flex items-center justify-between px-6 shadow-sm">
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp) // h-14
                .padding(horizontal = 24.dp), // px-6
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Outlined.ArrowBack,
                contentDescription = "Back",
                tint = Gray600,
                modifier = Modifier
                    .size(24.dp) // text-3xl approx size
                    .clickable { onBackClick() }
            )
            Text(
                text = "菜品分析",
                fontSize = 20.sp, // text-2xl approx
                fontWeight = FontWeight.Bold,
                color = Gray800
            )
            // Spacer for alignment balance
            Spacer(modifier = Modifier.width(24.dp))
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp), // px-6 py-6
            verticalArrangement = Arrangement.spacedBy(24.dp) // space-y-6
        ) {
            // Image Placeholder
            // <div class="bg-emerald-50 rounded-[32px] overflow-hidden h-56 flex items-center justify-center border-2 border-emerald-100">
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(224.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Emerald50)
                    .border(2.dp, Emerald100, RoundedCornerShape(32.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (capturedBitmap != null) {
                    AsyncImage(
                        model = capturedBitmap,
                        contentDescription = "Captured Food",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.LocalDining,
                        contentDescription = null,
                        tint = Emerald500.copy(alpha = 0.6f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            // Main Info Card
            // <div class="bg-emerald-600 p-6 rounded-[32px] text-white shadow-lg">
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Emerald600)
                    .padding(24.dp) // p-6
            ) {
                // <h2 class="text-3xl font-bold mb-2">红烧排骨</h2>
                Text(
                    text = diet?.foodName ?: "正在分析...",
                    fontSize = 24.sp, // text-3xl approx
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp) // mb-2
                )

                // <div class="flex justify-between items-end">
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // <p class="text-emerald-100 text-xl font-medium">约 250 克</p>
                    Text(
                        text = "智能识别结果",
                        fontSize = 18.sp, // text-xl approx
                        fontWeight = FontWeight.Medium,
                        color = Emerald100
                    )

                    // <p class="text-4xl font-black">480 <span class="text-lg font-normal">kcal</span></p>
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(fontSize = 30.sp, fontWeight = FontWeight.Black)) {
                                append("${diet?.calories?.toInt() ?: 0} ")
                            }
                            withStyle(style = SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal)) {
                                append("kcal")
                            }
                        },
                        color = Color.White
                    )
                }
            }

            // Advice Section
            // <div class="space-y-4">
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                // <h4 class="text-2xl font-bold text-gray-800 flex items-center gap-2">
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // <span class="w-1.5 h-6 bg-emerald-500 rounded-full"></span>
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(24.dp)
                            .clip(CircleShape)
                            .background(Emerald500)
                    )
                    Text(
                        text = "饮食规划建议",
                        fontSize = 20.sp, // text-2xl approx
                        fontWeight = FontWeight.Bold,
                        color = Gray800
                    )
                }

                // Advice Content
                // <div class="bg-orange-50 p-5 rounded-2xl border border-orange-100">
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Orange50)
                        .border(1.dp, Orange100, RoundedCornerShape(16.dp))
                        .padding(20.dp) // p-5
                ) {
                    // <p class="text-lg text-gray-800 leading-relaxed font-medium">
                    Text(
                        text = "此菜品热量偏高。建议搭配一份白灼青菜，并减少今日后续油脂摄入。适合在午餐时段食用。",
                        fontSize = 16.sp, // text-lg approx
                        color = Gray800,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp
                    )
                }

                // Action Button
                // <button class="w-full bg-emerald-600 text-white text-2xl font-bold py-5 rounded-3xl shadow-lg mt-4"
                Button(
                    onClick = { onBackClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp) // mt-4
                        .height(72.dp), // py-5 approx height (5*4*2 + line height)
                    shape = RoundedCornerShape(24.dp), // rounded-3xl
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald600),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Text(
                        text = "确认录入",
                        fontSize = 20.sp, // text-2xl approx
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 375, heightDp = 812)
@Composable
private fun DishResultScreenPreview() {
    HealthManagerTheme {
        DishResultScreen(viewModel = viewModel())
    }
}
