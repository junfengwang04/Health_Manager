package com.pbz.healthmanager.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 监听系统重启广播，重新调度所有已开启的药盒闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 注意：由于当前 AlarmViewModel 的数据可能存储在 Room 或 DataStore 中
            // 这里应该调用 Repository 加载数据并使用 AlarmScheduler 重新 schedule
            // 考虑到当前是 UI 层管理状态，建议将闹钟状态持久化后在此恢复
            // TODO: 加载本地数据库中的所有 isActive 闹钟并重新设定
        }
    }
}
