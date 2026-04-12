package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 药品表
 * @param approvalNumber 主键 国药准字
 * @param name 药品名称
 * @param manufacturer 生产公司
 * @param timesPerDay 每天服用次数
 * @param dosePerTime 服用单次量
 * @param mealRelation 饭前or饭后服用
 * @param reminderTimesJson 提醒时间
 * @param contraindications 禁忌
 * @param expiryDate 有效期
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey val approvalNumber: String,
    val name: String,
    val manufacturer: String = "",
    val timesPerDay: Int = 3,
    val dosePerTime: String = "1粒",
    val mealRelation: String = "饭后",
    val reminderTimesJson: String = "[]",
    val contraindications: String? = null,
    val expiryDate: String? = null
) {
    val intakeTiming: String
        get() = mealRelation

    val frequency: String
        get() = when (timesPerDay) {
            1 -> "每日一次"
            2 -> "每日二次"
            3 -> "每日三次"
            else -> "每日${timesPerDay}次"
        }
}
