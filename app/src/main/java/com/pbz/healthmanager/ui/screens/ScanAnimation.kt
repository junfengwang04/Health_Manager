package com.pbz.healthmanager.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun ScanAnimationOverlay() {
    // 呼吸动画 (用于十字对焦)
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    // 激光扫描线动画 (上下移动)
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanLine"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val crossSize = 40.dp.toPx()
            val strokeWidth = 2.dp.toPx()
            val crossColor = Color.White.copy(alpha = alpha)

            // 绘制呼吸十字
            drawLine(
                color = crossColor,
                start = center.copy(x = center.x - crossSize / 2),
                end = center.copy(x = center.x + crossSize / 2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = crossColor,
                start = center.copy(y = center.y - crossSize / 2),
                end = center.copy(y = center.y + crossSize / 2),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // 绘制激光扫描线
            val scanAreaHeight = size.height * 0.6f // 扫描区域占屏幕高度的 60%
            val startY = (size.height - scanAreaHeight) / 2
            val currentY = startY + (scanAreaHeight * scanLineY)
            
            val lineWidth = size.width * 0.8f
            val startX = (size.width - lineWidth) / 2
            
            // 激光线渐变效果
            val brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFF10B981).copy(alpha = 0.8f), // Emerald500
                    Color.Transparent
                ),
                startY = currentY - 20.dp.toPx(),
                endY = currentY + 20.dp.toPx()
            )
            
            drawRect(
                brush = brush,
                topLeft = Offset(startX, currentY - 10.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(lineWidth, 20.dp.toPx())
            )
            
            // 中心高亮实线
            drawLine(
                color = Color(0xFF34D399), // Emerald400
                start = Offset(startX + lineWidth * 0.1f, currentY),
                end = Offset(startX + lineWidth * 0.9f, currentY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
