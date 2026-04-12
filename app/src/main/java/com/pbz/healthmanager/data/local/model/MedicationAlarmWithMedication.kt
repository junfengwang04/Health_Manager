package com.pbz.healthmanager.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding

data class MedicationAlarmWithMedication(
    @Embedded val binding: MedicationAlarmBinding,
    @Relation(
        parentColumn = "medicationApprovalNumber",
        entityColumn = "approvalNumber"
    )
    val medication: Medication
)
