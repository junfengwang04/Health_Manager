package com.pbz.healthmanager.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.pbz.healthmanager.data.local.dao.HealthDao
import com.pbz.healthmanager.data.local.entity.DietRecord
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding
import com.pbz.healthmanager.data.local.entity.MedicationLog
import com.pbz.healthmanager.data.local.entity.RecognitionHistory
import com.pbz.healthmanager.data.local.model.MedicationAlarmWithMedication
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import com.pbz.healthmanager.data.remote.pb.ElderHealthSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.UUID

/**
 * 业务仓库，封装 DAO 操作
 */
class HealthRepository(
    private val appContext: Context,
    private val healthDao: HealthDao
) {
    suspend fun exportCoreSnapshot(): ElderHealthSnapshot {
        return withContext(Dispatchers.IO) {
            ElderHealthSnapshot(
                medications = healthDao.getAllMedicationsOnce(),
                medicationAlarmBindings = healthDao.getAllMedicationAlarmBindingsOnce(),
                medicationLogs = healthDao.getAllMedicationLogsOnce(),
                dietRecords = healthDao.getAllDietRecordsOnce(),
                recognitionHistories = healthDao.getAllRecognitionHistoryOnce(),
                healthIndices = healthDao.getAllHealthIndicesOnce()
            )
        }
    }

    suspend fun importCoreSnapshot(snapshot: ElderHealthSnapshot) {
        withContext(Dispatchers.IO) {
            val validMeds = snapshot.medications.filter {
                val compact = it.approvalNumber.trim().replace("\\s".toRegex(), "")
                Regex("国药准字[A-Za-z]\\d{8}").matches(compact)
            }
            validMeds.forEach { med ->
                val existing = healthDao.getMedicationByApprovalNumber(med.approvalNumber)
                if (existing == null) {
                    healthDao.insertMedicationIgnore(med)
                } else {
                    healthDao.updateMedication(
                        existing.copy(
                            name = med.name,
                            manufacturer = med.manufacturer,
                            timesPerDay = med.timesPerDay,
                            dosePerTime = med.dosePerTime,
                            mealRelation = med.mealRelation,
                            reminderTimesJson = med.reminderTimesJson,
                            contraindications = med.contraindications,
                            expiryDate = med.expiryDate
                        )
                    )
                }
            }
            healthDao.replaceCoreData(snapshot.copy(medications = emptyList()))
        }
    }

    suspend fun clearLocalCoreDataForAccountSwitch() {
        withContext(Dispatchers.IO) {
            healthDao.clearAllCoreDataForAccountSwitch()
        }
    }

    // --- Medication (药品) ---

    val allMedications: Flow<List<Medication>> = healthDao.getAllMedications()

    suspend fun insertMedication(medication: Medication): Long {
        val existing = healthDao.getMedicationByApprovalNumber(medication.approvalNumber)
        return if (existing == null) {
            healthDao.insertMedicationIgnore(medication)
        } else {
            healthDao.updateMedication(
                existing.copy(
                    name = medication.name,
                    manufacturer = medication.manufacturer,
                    timesPerDay = medication.timesPerDay,
                    dosePerTime = medication.dosePerTime,
                    mealRelation = medication.mealRelation,
                    reminderTimesJson = medication.reminderTimesJson,
                    contraindications = medication.contraindications,
                    expiryDate = medication.expiryDate
                )
            )
            existing.approvalNumber.hashCode().toLong()
        }
    }

    suspend fun updateMedication(medication: Medication) {
        healthDao.updateMedication(medication)
    }

    suspend fun deleteMedication(medication: Medication) {
        healthDao.deleteMedication(medication)
    }

    suspend fun getMedicationByApprovalNumber(approvalNumber: String): Medication? {
        return healthDao.getMedicationByApprovalNumber(approvalNumber)
    }

    suspend fun findMedicationByApprovalNumber(rawApprovalNumber: String): Medication? {
        val candidates = buildApprovalNumberCandidates(rawApprovalNumber)
        for (candidate in candidates) {
            val existing = healthDao.getMedicationByApprovalNumber(candidate)
            if (existing != null) return existing
        }
        return null
    }

    suspend fun findMedicationByOcrText(ocrText: String): Medication? {
        if (ocrText.isBlank()) return null
        return healthDao.findMedicationByOcrText(ocrText)
    }

    val activeMedicationAlarmBindings: Flow<List<MedicationAlarmWithMedication>> =
        healthDao.getAllActiveMedicationAlarmBindings()

    fun getActiveAlarmBindingsByMedication(approvalNumber: String): Flow<List<MedicationAlarmBinding>> {
        return healthDao.getActiveAlarmBindingsByMedication(approvalNumber)
    }

    suspend fun upsertMedicationAlarmBinding(
        alarmId: String,
        medicationApprovalNumber: String,
        periodName: String,
        alarmTime: String
    ) {
        healthDao.upsertMedicationAlarmBinding(
            MedicationAlarmBinding(
                alarmId = alarmId,
                medicationApprovalNumber = medicationApprovalNumber,
                periodName = periodName,
                alarmTime = alarmTime,
                isActive = true,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun removeMedicationAlarmBinding(alarmId: String) {
        healthDao.deleteMedicationAlarmBindingByAlarmId(alarmId)
    }

    // --- MedicationLog (服药记录) ---

    suspend fun insertMedicationLog(log: MedicationLog): Long {
        return healthDao.insertMedicationLog(log)
    }

    suspend fun upsertMedicationDecision(
        medicationApprovalNumber: String,
        scheduledTime: Long,
        status: String,
        actualTime: Long? = null
    ) {
        val existing = healthDao.getMedicationLogByApprovalAndScheduledTime(
            approvalNumber = medicationApprovalNumber,
            scheduledTime = scheduledTime
        )
        if (existing == null) {
            healthDao.insertMedicationLog(
                MedicationLog(
                    medicationApprovalNumber = medicationApprovalNumber,
                    scheduledTime = scheduledTime,
                    actualTime = actualTime,
                    status = status
                )
            )
        } else {
            healthDao.updateMedicationLog(
                existing.copy(
                    actualTime = actualTime,
                    status = status
                )
            )
        }
    }

    /**
     * 根据日期查询当天的服药记录 (包含药品详情)
     */
    fun getMedicationLogsByDate(timestamp: Long): Flow<List<MedicationLogWithInfo>> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        
        calendar.apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        val endTime = calendar.timeInMillis
        
        return healthDao.getMedicationLogsByDateRange(startTime, endTime)
    }

    fun getMedicationLogsRecentDays(days: Int = 7): Flow<List<MedicationLogWithInfo>> {
        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(days - 1))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()
        return healthDao.getMedicationLogsByDateRange(startTime, endTime)
    }

    fun getAllMedicationLogs(): Flow<List<MedicationLogWithInfo>> {
        return healthDao.getMedicationLogsByDateRange(0L, System.currentTimeMillis())
    }

    // --- DietRecord (饮食) ---

    val allDietRecords: Flow<List<DietRecord>> = healthDao.getAllDietRecords()

    suspend fun insertDietRecord(dietRecord: DietRecord): Long {
        return healthDao.insertDietRecord(dietRecord)
    }

    suspend fun insertDietFromRecognition(
        bitmap: Bitmap,
        foodName: String,
        calories: Double,
        protein: Double = 0.0,
        carbs: Double = 0.0,
        photoTime: Long = System.currentTimeMillis()
    ): DietRecord? {
        val imagePath = saveDietBitmap(bitmap)
        val record = DietRecord(
            foodName = foodName,
            calories = calories,
            protein = protein,
            carbs = carbs,
            photoTime = photoTime,
            imagePath = imagePath
        )
        val id = healthDao.insertDietRecord(record)
        return healthDao.getDietRecordById(id)
    }

    suspend fun getDietRecordById(id: Long): DietRecord? {
        return healthDao.getDietRecordById(id)
    }

    suspend fun updateDietRecord(dietRecord: DietRecord) {
        healthDao.updateDietRecord(dietRecord)
    }

    suspend fun deleteDietRecord(dietRecord: DietRecord) {
        healthDao.deleteDietRecord(dietRecord)
    }

    // --- RecognitionHistory (识别历史) ---

    val recentRecognitionHistory: Flow<List<RecognitionHistory>> = healthDao.getRecentRecognitionHistory()

    suspend fun insertRecognitionHistory(history: RecognitionHistory): Long {
        return healthDao.insertRecognitionHistory(history)
    }

    suspend fun insertRecognitionHistory(
        name: String,
        source: String,
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        val sourceCode = if (source == "manual") 1.0 else 0.0
        return healthDao.insertRecognitionHistory(
            RecognitionHistory(
                foodName = name,
                calorie = sourceCode,
                imagePath = null,
                timestamp = timestamp
            )
        )
    }

    suspend fun deleteRecognitionHistoryById(id: Long) {
        healthDao.deleteRecognitionHistoryById(id)
    }

    suspend fun clearRecognitionHistory() {
        healthDao.clearRecognitionHistory()
    }

    // --- HealthIndex (指标) ---

    val allHealthIndices: Flow<List<HealthIndex>> = healthDao.getAllHealthIndices()

    suspend fun insertHealthIndex(healthIndex: HealthIndex) {
        healthDao.insertHealthIndex(healthIndex)
    }

    suspend fun insertHealthIndex(type: String, value: Double, remark: String? = null, measureTime: Long = System.currentTimeMillis()) {
        healthDao.insertHealthIndex(
            HealthIndex(
                type = type,
                value = value,
                measureTime = measureTime,
                remark = remark
            )
        )
    }

    suspend fun updateHealthIndex(healthIndex: HealthIndex) {
        healthDao.updateHealthIndex(healthIndex)
    }

    suspend fun deleteHealthIndex(healthIndex: HealthIndex) {
        healthDao.deleteHealthIndex(healthIndex)
    }

    fun getHealthIndicesByType(type: String): Flow<List<HealthIndex>> {
        return healthDao.getHealthIndicesByType(type)
    }

    suspend fun ensureRecentSevenDaysBloodPressureSeedData() {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val demoCount = healthDao.countDemoBloodPressureRecords()
            if (demoCount >= 9) return@withContext

            healthDao.deleteDemoBloodPressureRecords()

            val samples = listOf(
                Triple(-6, 8, "128/79"),  Triple(-6, 20, "124/77"),
                Triple(-5, 8, "142/92"),  Triple(-5, 20, "136/88"),
                Triple(-4, 8, "133/83"),
                Triple(-2, 20, "129/81"),
                Triple(-1, 8, "138/86"),  Triple(-1, 20, "131/84"),
                Triple(0, 8, "140/90")
            )

            samples.forEach { (dayOffset, hour, bp) ->
                val measureTime = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, dayOffset)
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                healthDao.insertHealthIndex(
                    HealthIndex(
                        type = "血压",
                        value = bp.substringBefore('/').toDouble(),
                        measureTime = measureTime,
                        remark = "DEMO_BP|$bp"
                    )
                )
            }
        }
    }

    suspend fun forceResetRecentSevenDaysBloodPressureSeedData() {
        withContext(Dispatchers.IO) {
            healthDao.deleteDemoBloodPressureRecords()
            ensureRecentSevenDaysBloodPressureSeedData()
        }
    }

    suspend fun addBloodPressureRecord(
        systolic: Int,
        diastolic: Int,
        measureTime: Long = System.currentTimeMillis()
    ) {
        withContext(Dispatchers.IO) {
            healthDao.insertHealthIndex(
                HealthIndex(
                    type = "血压",
                    value = systolic.toDouble(),
                    measureTime = measureTime,
                    remark = "$systolic/$diastolic"
                )
            )
        }
    }

    private suspend fun saveDietBitmap(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            val dir = File(appContext.filesDir, "diet_images")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "diet_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
            runCatching {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                    out.flush()
                }
                file.absolutePath
            }.getOrNull()
        }
    }

    private fun buildApprovalNumberCandidates(raw: String): List<String> {
        val trimmed = raw.trim().replace(" ", "")
        val normalized = normalizeApprovalNumber(trimmed)
        val noPrefix = trimmed.removePrefix("国药准字")
        val normalizedNoPrefix = normalized.removePrefix("国药准字")
        return listOf(
            trimmed,
            trimmed.uppercase(),
            normalized,
            noPrefix,
            noPrefix.uppercase(),
            normalizedNoPrefix
        ).distinct()
    }

    private fun normalizeApprovalNumber(raw: String): String {
        val cleaned = raw.trim().replace(" ", "")
        val noPrefix = cleaned.removePrefix("国药准字")
        val match = Regex("^[ZHJS]\\d{8}$", RegexOption.IGNORE_CASE).find(noPrefix)
        val core = match?.value?.uppercase() ?: noPrefix.uppercase()
        return if (core.startsWith("国药准字")) core else "国药准字$core"
    }
}
