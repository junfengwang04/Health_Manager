package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 指标表
 * @param id 主键 ID
 * @param type 指标类型 (如: 血压、心率、血糖等)
 * @param value 数值
 * @param measureTime 测量时间 (时间戳)
 * @param remark 状态备注
 */
@Entity(tableName = "health_indices")
data class HealthIndex(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val value: Double,
    val measureTime: Long,
    val remark: String? = null
)
