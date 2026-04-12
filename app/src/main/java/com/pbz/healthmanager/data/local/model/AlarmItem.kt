package com.pbz.healthmanager.data.local.model

import java.io.Serializable
import java.util.UUID

/**
 * 用药闹钟项数据模型
 */
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val periodName: String,
    val suggestion: String,
    val time: String? = null,
    val isActive: Boolean = false
) : Serializable {
    // 获取用于 PendingIntent 的唯一 requestCode
    fun getRequestCode(): Int = id.hashCode()
}
