package com.pbz.healthmanager.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pbz.healthmanager.data.local.model.AlarmItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 用药闹钟 ViewModel，管理闹钟列表状态和交互逻辑
 */
class AlarmViewModel : ViewModel() {

    // 1. 定义私有和公开的状态流
    private val _alarmList = MutableStateFlow<List<AlarmItem>>(
        listOf(
            AlarmItem(id = "morning_alarm", periodName = "早晨用药", suggestion = "饭后半小时"),
            AlarmItem(id = "noon_alarm", periodName = "中午用药", suggestion = "饭后半小时"),
            AlarmItem(id = "night_alarm", periodName = "晚上用药", suggestion = "饭后半小时")
        )
    )
    val alarmList: StateFlow<List<AlarmItem>> = _alarmList.asStateFlow()

    // 定义 UI 事件流，用于通知 UI 弹出 TimePickerDialog 或 Toast
    sealed class UiEvent {
        data class ShowTimePicker(val id: String) : UiEvent()
        data class ShowToast(val message: String) : UiEvent()
    }

    private val _uiEvent = MutableSharedFlow<UiEvent>()
    val uiEvent: SharedFlow<UiEvent> = _uiEvent.asSharedFlow()

    /**
     * 切换闹钟开关状态
     */
    fun toggleAlarm(id: String) {
        _alarmList.update { currentList ->
            currentList.map { item ->
                if (item.id == id) {
                    if (item.time == null && !item.isActive) {
                        // 边界处理：尝试开启一个 time 为 null 的项，发送 UI 事件弹出时间选择器
                        viewModelScope.launch {
                            _uiEvent.emit(UiEvent.ShowTimePicker(id))
                        }
                        item // 不改变状态
                    } else {
                        // 正常切换开关
                        item.copy(isActive = !item.isActive)
                    }
                } else {
                    item
                }
            }
        }
    }

    /**
     * 更新时间
     */
    fun updateTime(id: String, hour: Int, minute: Int) {
        val formattedTime = String.format("%02d:%02d", hour, minute)
        _alarmList.update { currentList ->
            currentList.map { item ->
                if (item.id == id) {
                    // 更新时间后，自动将 isActive 置为 true
                    item.copy(time = formattedTime, isActive = true)
                } else {
                    item
                }
            }
        }
    }

    /**
     * 添加自定义时间段
     */
    fun addTimeSlot(customName: String) {
        _alarmList.update { currentList ->
            currentList + AlarmItem(
                periodName = customName,
                suggestion = "请备注说明"
            )
        }
    }

    /**
     * 移除用药时段
     */
    fun removeTimeSlot(id: String) {
        _alarmList.update { currentList ->
            currentList.filter { it.id != id }
        }
    }

    /**
     * 保存修改
     */
    fun saveModifications() {
        viewModelScope.launch {
            // 过滤出当前已激活且已设置时间的数据
            val activeAlarms = _alarmList.value.filter { it.isActive && it.time != null }
            
            // 模拟保存操作 (如存入 Room)
            delay(1000) 
            
            // 保存成功后发送通知
            _uiEvent.emit(UiEvent.ShowToast("闹钟设置已成功保存"))
        }
    }
}
