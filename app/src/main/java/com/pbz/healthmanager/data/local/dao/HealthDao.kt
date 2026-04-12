package com.pbz.healthmanager.data.local.dao

import androidx.room.*
import com.pbz.healthmanager.data.local.entity.DietRecord
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding
import com.pbz.healthmanager.data.local.entity.MedicationLog
import com.pbz.healthmanager.data.local.entity.RecognitionHistory
import com.pbz.healthmanager.data.local.model.MedicationAlarmWithMedication
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import kotlinx.coroutines.flow.Flow

/**
 * 数据库操作接口
 */
@Dao
interface HealthDao {

    // --- Medication (药品) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: Medication): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedicationIgnore(medication: Medication): Long

    @Update
    suspend fun updateMedication(medication: Medication)

    @Delete
    suspend fun deleteMedication(medication: Medication)

    @Query("SELECT * FROM medications ORDER BY approvalNumber DESC")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY approvalNumber DESC")
    suspend fun getAllMedicationsOnce(): List<Medication>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedications(medications: List<Medication>)

    @Query("SELECT * FROM medications WHERE approvalNumber = :approvalNumber")
    suspend fun getMedicationByApprovalNumber(approvalNumber: String): Medication?

    @Query("SELECT * FROM medications WHERE :ocrText LIKE '%' || name || '%' ORDER BY LENGTH(name) DESC LIMIT 1")
    suspend fun findMedicationByOcrText(ocrText: String): Medication?

    @Query("SELECT COUNT(*) FROM medications")
    suspend fun countMedications(): Int

    // --- MedicationAlarmBinding (药品闹钟绑定) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedicationAlarmBinding(binding: MedicationAlarmBinding)

    @Query("DELETE FROM medication_alarm_bindings WHERE alarmId = :alarmId")
    suspend fun deleteMedicationAlarmBindingByAlarmId(alarmId: String)

    @Query("SELECT * FROM medication_alarm_bindings WHERE medicationApprovalNumber = :approvalNumber AND isActive = 1 ORDER BY alarmTime ASC")
    fun getActiveAlarmBindingsByMedication(approvalNumber: String): Flow<List<MedicationAlarmBinding>>

    @Transaction
    @Query("SELECT * FROM medication_alarm_bindings WHERE isActive = 1 ORDER BY updatedAt DESC")
    fun getAllActiveMedicationAlarmBindings(): Flow<List<MedicationAlarmWithMedication>>

    @Query("SELECT * FROM medication_alarm_bindings ORDER BY updatedAt DESC")
    suspend fun getAllMedicationAlarmBindingsOnce(): List<MedicationAlarmBinding>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationAlarmBindings(bindings: List<MedicationAlarmBinding>)

    // --- MedicationLog (服药记录) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationLog(log: MedicationLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicationLogs(logs: List<MedicationLog>)

    @Update
    suspend fun updateMedicationLog(log: MedicationLog)

    @Query("SELECT * FROM medication_logs WHERE medicationApprovalNumber = :approvalNumber AND scheduledTime = :scheduledTime LIMIT 1")
    suspend fun getMedicationLogByApprovalAndScheduledTime(
        approvalNumber: String,
        scheduledTime: Long
    ): MedicationLog?

    @Transaction
    @Query("SELECT * FROM medication_logs WHERE scheduledTime >= :startTime AND scheduledTime <= :endTime ORDER BY scheduledTime ASC")
    fun getMedicationLogsByDateRange(startTime: Long, endTime: Long): Flow<List<MedicationLogWithInfo>>

    @Query("SELECT * FROM medication_logs ORDER BY scheduledTime DESC")
    suspend fun getAllMedicationLogsOnce(): List<MedicationLog>

    // --- DietRecord (饮食) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDietRecord(dietRecord: DietRecord): Long

    @Update
    suspend fun updateDietRecord(dietRecord: DietRecord)

    @Delete
    suspend fun deleteDietRecord(dietRecord: DietRecord)

    @Query("SELECT * FROM diet_records ORDER BY photoTime DESC")
    fun getAllDietRecords(): Flow<List<DietRecord>>

    @Query("SELECT * FROM diet_records ORDER BY photoTime DESC")
    suspend fun getAllDietRecordsOnce(): List<DietRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDietRecords(records: List<DietRecord>)

    @Query("SELECT * FROM diet_records WHERE id = :id")
    suspend fun getDietRecordById(id: Long): DietRecord?

    // --- RecognitionHistory (识别历史) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecognitionHistory(history: RecognitionHistory): Long

    @Query("SELECT * FROM recognition_history ORDER BY timestamp DESC LIMIT 3")
    fun getRecentRecognitionHistory(): Flow<List<RecognitionHistory>>

    @Query("SELECT * FROM recognition_history ORDER BY timestamp DESC")
    suspend fun getAllRecognitionHistoryOnce(): List<RecognitionHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecognitionHistories(items: List<RecognitionHistory>)

    @Query("DELETE FROM recognition_history WHERE id = :id")
    suspend fun deleteRecognitionHistoryById(id: Long)

    @Query("DELETE FROM recognition_history")
    suspend fun clearRecognitionHistory()

    // --- HealthIndex (指标) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthIndex(healthIndex: HealthIndex)

    @Update
    suspend fun updateHealthIndex(healthIndex: HealthIndex)

    @Delete
    suspend fun deleteHealthIndex(healthIndex: HealthIndex)

    @Query("SELECT * FROM health_indices ORDER BY measureTime DESC")
    fun getAllHealthIndices(): Flow<List<HealthIndex>>

    @Query("SELECT * FROM health_indices ORDER BY measureTime DESC")
    suspend fun getAllHealthIndicesOnce(): List<HealthIndex>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHealthIndices(indices: List<HealthIndex>)

    @Query("SELECT * FROM health_indices WHERE type = :type ORDER BY measureTime DESC")
    fun getHealthIndicesByType(type: String): Flow<List<HealthIndex>>

    @Query(
        "SELECT COUNT(*) FROM health_indices " +
            "WHERE type IN ('血压','收缩压','舒张压') " +
            "AND measureTime BETWEEN :startTime AND :endTime"
    )
    suspend fun countBloodPressureRecordsInRange(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM health_indices WHERE type = '血压' AND remark LIKE 'DEMO_BP|%'")
    suspend fun countDemoBloodPressureRecords(): Int

    @Query("DELETE FROM health_indices WHERE type = '血压' AND remark LIKE 'DEMO_BP|%'")
    suspend fun deleteDemoBloodPressureRecords()

    @Query("DELETE FROM medication_logs")
    suspend fun clearMedicationLogs()

    @Query("DELETE FROM medications")
    suspend fun clearMedications()

    @Query("DELETE FROM health_indices")
    suspend fun clearHealthIndices()

    @Query("DELETE FROM diet_records")
    suspend fun clearDietRecords()

    @Query("DELETE FROM medication_alarm_bindings")
    suspend fun clearMedicationAlarmBindings()

    @Transaction
    suspend fun clearAllCoreDataForAccountSwitch() {
        clearMedicationAlarmBindings()
        clearMedicationLogs()
        clearDietRecords()
        clearRecognitionHistory()
        clearHealthIndices()
        clearMedications()
    }

    @Transaction
    suspend fun replaceCoreData(snapshot: com.pbz.healthmanager.data.remote.pb.ElderHealthSnapshot) {
        if (snapshot.medications.isNotEmpty()) {
            insertMedications(snapshot.medications)
        }
        if (snapshot.medicationLogs.isNotEmpty()) {
            clearMedicationLogs()
            insertMedicationLogs(snapshot.medicationLogs)
        }
        if (snapshot.dietRecords.isNotEmpty()) {
            clearDietRecords()
            insertDietRecords(snapshot.dietRecords)
        }
        if (snapshot.recognitionHistories.isNotEmpty()) {
            clearRecognitionHistory()
            insertRecognitionHistories(snapshot.recognitionHistories)
        }
        if (snapshot.medicationAlarmBindings.isNotEmpty()) {
            clearMedicationAlarmBindings()
            insertMedicationAlarmBindings(snapshot.medicationAlarmBindings)
        }
        if (snapshot.healthIndices.isNotEmpty()) {
            clearHealthIndices()
            insertHealthIndices(snapshot.healthIndices)
        }
    }
}
