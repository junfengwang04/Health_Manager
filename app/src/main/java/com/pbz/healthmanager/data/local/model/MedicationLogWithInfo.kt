package com.pbz.healthmanager.data.local.model

import androidx.room.Embedded
import androidx.room.Relation
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationLog

/**
 * 带有药品名称的服药记录 (Join 结果)
 */
data class MedicationLogWithInfo(
    @Embedded val log: MedicationLog,
    @Relation(
        parentColumn = "medicationApprovalNumber",
        entityColumn = "approvalNumber"
    )
    val medication: Medication
)
