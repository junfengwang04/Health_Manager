package com.pbz.healthmanager.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_alarm_bindings",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["approvalNumber"],
            childColumns = ["medicationApprovalNumber"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("medicationApprovalNumber")
    ]
)
data class MedicationAlarmBinding(
    @PrimaryKey val alarmId: String,
    val medicationApprovalNumber: String,
    val periodName: String,
    val alarmTime: String,
    val isActive: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)
