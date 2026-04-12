package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 饮食表
 * @param id 主键 ID
 * @param foodName 食物名称
 * @param calories 热量 (kcal)
 * @param protein 蛋白质 (g)
 * @param carbs 碳水化合物 (g)
 * @param photoTime 拍照/记录时间 (时间戳)
 * @param imagePath 图片本地路径
 */
@Entity(tableName = "diet_records")
data class DietRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodName: String,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val photoTime: Long,
    val imagePath: String? = null
)
