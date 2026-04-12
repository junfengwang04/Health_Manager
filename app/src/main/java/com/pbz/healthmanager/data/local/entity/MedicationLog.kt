package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * 服药记录表
 * @param id 主键 ID
 * @param medicationApprovalNumber 关联的药品国药准字 (approvalNumber)
 * @param scheduledTime 应服药时间 (时间戳)
 * @param actualTime 实际服药时间 (时间戳)
 * @param status 服药状态 (如: 已服用、待服用、已跳过)
 */
@Entity(
    tableName = "medication_logs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["approvalNumber"],
            childColumns = ["medicationApprovalNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MedicationLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicationApprovalNumber: String,
    val scheduledTime: Long,
    val actualTime: Long? = null,
    val status: String
)
