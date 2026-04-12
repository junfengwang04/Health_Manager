package com.pbz.healthmanager.alarm

import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.pbz.healthmanager.HealthApplication
import com.pbz.healthmanager.data.local.model.AlarmItem
import com.pbz.healthmanager.data.remote.pb.PbCloudService
import com.pbz.healthmanager.data.remote.pb.PbSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 药盒闹钟调度器：使用 AlarmManager 实现高精准后台闹钟
 * 相比系统 Intent 方式，该方案支持：
 * 1. 后台静默设置（不跳转闹钟 App）
 * 2. 精确的同步更新与取消
 * 3. 跨版本系统稳定性
 */
class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * 设置或更新闹钟
     */
    fun schedule(item: AlarmItem, medicationApprovalNumber: String? = null) {
        val time = item.time ?: return
        if (!item.isActive) return

        OppoAlarmPermissionGuide.maybeGuide(context)

        // 1. 检查精确闹钟权限 (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "请开启精确闹钟权限后再保存提醒", Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        val parts = time.split(":")
        val hour = parts[0].toInt()
        val minute = parts[1].toInt()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // 如果设定时间已过，设为明天
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", item.periodName)
            putExtra("EXTRA_ALARM_ID", item.id)
            putExtra("EXTRA_ALARM_ITEM", item)
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", calendar.timeInMillis)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.getRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 使用最高优先级的 AlarmClockInfo 方式，会在系统状态栏显示闹钟图标
        val alarmClockInfo = AlarmManager.AlarmClockInfo(
            calendar.timeInMillis,
            pendingIntent
        )

        alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)

        if (!medicationApprovalNumber.isNullOrBlank()) {
            val app = context.applicationContext as? HealthApplication
            app?.let {
                CoroutineScope(Dispatchers.IO).launch {
                    it.repository.upsertMedicationAlarmBinding(
                        alarmId = item.id,
                        medicationApprovalNumber = medicationApprovalNumber,
                        periodName = item.periodName,
                        alarmTime = time
                    )
                    val elderPhone = PbSession.currentElderPhone.trim()
                    if (elderPhone.isNotBlank()) {
                        val snapshot = it.repository.exportCoreSnapshot()
                        PbCloudService().uploadElderSnapshot(elderPhone, snapshot)
                    }
                }
            }
        }
    }

    /**
     * 取消特定的闹钟
     */
    fun cancel(item: AlarmItem) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            item.getRequestCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        val app = context.applicationContext as? HealthApplication
        app?.let {
            CoroutineScope(Dispatchers.IO).launch {
                it.repository.removeMedicationAlarmBinding(item.id)
                val elderPhone = PbSession.currentElderPhone.trim()
                if (elderPhone.isNotBlank()) {
                    val snapshot = it.repository.exportCoreSnapshot()
                    PbCloudService().uploadElderSnapshot(elderPhone, snapshot)
                }
            }
        }
    }
}
