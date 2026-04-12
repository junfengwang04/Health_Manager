package com.pbz.healthmanager.data.remote.pb

import kotlinx.serialization.Serializable

enum class LoginTarget {
    ELDER,
    GUARDIAN
}

@Serializable
data class PbAuthResponse(
    val token: String,
    val record: PbUser
)

@Serializable
data class PbUser(
    val id: String,
    val username: String? = null,
    val phone: String? = null,
    val name: String? = null,
    val deviceCode: String? = null
)

data class ElderHealthSnapshot(
    val medications: List<com.pbz.healthmanager.data.local.entity.Medication> = emptyList(),
    val medicationAlarmBindings: List<com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding> = emptyList(),
    val medicationLogs: List<com.pbz.healthmanager.data.local.entity.MedicationLog> = emptyList(),
    val dietRecords: List<com.pbz.healthmanager.data.local.entity.DietRecord> = emptyList(),
    val recognitionHistories: List<com.pbz.healthmanager.data.local.entity.RecognitionHistory> = emptyList(),
    val healthIndices: List<com.pbz.healthmanager.data.local.entity.HealthIndex> = emptyList()
)

data class GuardianBoundElder(
    val phone: String,
    val name: String,
    val status: String = "ACTIVE"
)

data class PbLoginUser(
    val id: String,
    val account: String,
    val displayName: String,
    val deviceCode: String = "",
    val target: LoginTarget,
    val boundElderAccount: String = ""
)

data class PbBindRequest(
    val guardianPhone: String,
    val guardianName: String,
    val status: String = "INACTIVE",
    val createdAt: String = "",
    val updatedAt: String = ""
)
