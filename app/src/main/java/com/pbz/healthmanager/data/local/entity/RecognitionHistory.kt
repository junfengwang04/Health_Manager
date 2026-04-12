package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 识别历史记录表
 * @param id 自增 ID
 * @param foodName 菜名
 * @param calorie 热量 (kcal)
 * @param imagePath 拍摄图片的本地绝对路径
 * @param timestamp 识别时间戳
 */
@Entity(tableName = "recognition_history")
data class RecognitionHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val calorie: Double,
    val imagePath: String?,
    val timestamp: Long
)
