package com.pbz.healthmanager.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pbz.healthmanager.data.local.dao.HealthDao
import com.pbz.healthmanager.data.local.entity.DietRecord
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.MedicationAlarmBinding
import com.pbz.healthmanager.data.local.entity.MedicationLog
import com.pbz.healthmanager.data.local.entity.RecognitionHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.String

/**
 * App 数据库定义
 */
@Database(
    entities = [Medication::class, DietRecord::class, HealthIndex::class, MedicationLog::class, RecognitionHistory::class, MedicationAlarmBinding::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun healthDao(): HealthDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        //获取数据库单例
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "health_manager_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                CoroutineScope(Dispatchers.IO).launch {
                    ensureInitialData(instance.healthDao())
                }
                instance
            }
        }

        private suspend fun ensureInitialData(healthDao: HealthDao) {
            val hasMedication = healthDao.countMedications() > 0
            if (hasMedication) return

            val initialMedications = listOf(
                Medication(
                    approvalNumber = "国药准字H20003263",
                    name = "阿莫西林胶囊",
                    manufacturer = "珠海联邦制药有限公司",
                    timesPerDay = 3,
                    dosePerTime = "1粒",
                    mealRelation = "饭后",
                    contraindications = "对青霉素过敏者禁用"
                ),
                Medication(
                    approvalNumber = "国药准字H20110027",
                    name = "精氨酸布洛芬片",
                    manufacturer = "海南赞邦制药有限公司",
                    timesPerDay = 2,
                    dosePerTime = "1片",
                    mealRelation = "随餐",
                    contraindications = "对本品过敏者禁用"
                )
            )
            initialMedications.forEach { medication ->
                healthDao.insertMedication(medication)
            }

            val initialIndices = listOf(
                HealthIndex(
                    type = "血压",
                    value = 125.0,
                    remark = "正常范围",
                    measureTime = System.currentTimeMillis() - 86400000 // 昨天
                ),
                HealthIndex(
                    type = "心率",
                    value = 72.0,
                    remark = "静息心率",
                    measureTime = System.currentTimeMillis()
                )
            )
            initialIndices.forEach { index ->
                healthDao.insertHealthIndex(index)
            }
        }
    }
}
