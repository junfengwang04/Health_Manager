package com.pbz.healthmanager.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat

/**
 * 闹钟广播接收器：遵循 Android 10+ 后台启动限制
 * 使用 FullScreenIntent 方案唤起提醒页面
 */
class AlarmReceiver : BroadcastReceiver() {


    override fun onReceive(context: Context, intent: Intent) {
        val periodName = intent.getStringExtra("EXTRA_PERIOD_NAME") ?: "用药提醒"
        val alarmId = intent.getStringExtra("EXTRA_ALARM_ID") ?: ""
        val medicationApprovalNumber = intent.getStringExtra("EXTRA_MEDICATION_APPROVAL_NUMBER")
        val scheduledTime = intent.getLongExtra("EXTRA_SCHEDULED_TIME", System.currentTimeMillis())
        val isDecisionMode = intent.getBooleanExtra("EXTRA_IS_DECISION_MODE", false)

        // 1. 获取 WakeLock 锁定 CPU 3秒，确保通知发出前手机不休眠
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HealthManager:AlarmReceiverLock"
        )
        wakeLock.acquire(3000L) 

        // 2. 启动前台服务（由服务统一处理铃声、语音和全屏提醒通知）
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", periodName)
            putExtra("EXTRA_ALARM_ID", alarmId)
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", scheduledTime)
            putExtra("EXTRA_IS_DECISION_MODE", isDecisionMode)
        }
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 兜底逻辑：如果服务启动失败，至少发一个普通通知
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "ALARM_FALLBACK_CHANNEL"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(channelId, "用药提醒", NotificationManager.IMPORTANCE_HIGH)
                notificationManager.createNotificationChannel(channel)
            }
            val fallbackNotification = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("用药提醒：$periodName")
                .setContentText("闹钟已响，请点击查看。")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(alarmId.hashCode(), fallbackNotification)
        }

        val alertIntent = Intent(context, AlarmAlertActivity::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", periodName)
            putExtra("EXTRA_ALARM_ID", alarmId)
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", scheduledTime)
            putExtra("EXTRA_IS_DECISION_MODE", isDecisionMode)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (alarmId + if (isDecisionMode) "_decision" else "_initial").hashCode(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "MEDICATION_ALARM_FULLSCREEN_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "用药闹钟全屏提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val title = if (isDecisionMode) "请确认是否已吃药" else "该用药啦"
        val content = if (isDecisionMode) "请选择：吃了药 / 待会吃 / 本次不吃" else "点击查看提醒页面"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.notify((alarmId + "_fullscreen").hashCode(), notification)
    }
}
