package com.pbz.healthmanager.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.*

/**
 * 闹钟前台服务：确保在后台和熄屏状态下，铃声和播报能立即启动
 */
class AlarmService : Service(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var periodName: String = "用药提醒"
    private var alarmId: String = "default_alarm"
    private var medicationApprovalNumber: String? = null
    private var scheduledTime: Long = 0L
    private var isDecisionMode: Boolean = false

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            periodName = intent?.getStringExtra("EXTRA_PERIOD_NAME") ?: "用药提醒"
            alarmId = intent?.getStringExtra("EXTRA_ALARM_ID") ?: "default_alarm"
            medicationApprovalNumber = intent?.getStringExtra("EXTRA_MEDICATION_APPROVAL_NUMBER")
            scheduledTime = intent?.getLongExtra("EXTRA_SCHEDULED_TIME", System.currentTimeMillis()) ?: System.currentTimeMillis()
            isDecisionMode = intent?.getBooleanExtra("EXTRA_IS_DECISION_MODE", false) ?: false

            // 核心修复：先创建通知并启动前台，这是最关键的步骤
            val notification = createForegroundNotification(periodName, alarmId)
            startForeground(1001, notification)

            // 2. 异步初始化铃声和震动，防止阻塞主线程或导致闪退
            handler.post {
                openAlertActivityNow()
                playAlarmSound()
                startVibration()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // 如果启动前台服务失败，尝试记录并优雅停止
            stopSelf()
        }

        return START_STICKY
    }

    private fun createForegroundNotification(title: String, alarmId: String): Notification {
        val channelId = "ALARM_SERVICE_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "正在运行的闹钟",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null) // 服务通知本身不响铃，由 MediaPlayer 响
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val alertIntent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", title)
            putExtra("EXTRA_ALARM_ID", alarmId)
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", scheduledTime)
            putExtra("EXTRA_IS_DECISION_MODE", isDecisionMode)
            // 核心修复：移除 FLAG_ACTIVITY_CLEAR_TOP，改用更温和的跳转，避免破坏主任务栈
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            alarmId.hashCode(),
            alertIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("【用药提醒】$title")
            .setContentText("正在为您播报，请及时处理。")
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .build()
    }

    private fun openAlertActivityNow() {
        val intent = Intent(this, AlarmAlertActivity::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", periodName)
            putExtra("EXTRA_ALARM_ID", alarmId)
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", scheduledTime)
            putExtra("EXTRA_IS_DECISION_MODE", isDecisionMode)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(intent) }
    }

    private fun playAlarmSound() {
        try {
            // 确保 AudioManager 不为空
            if (audioManager == null) audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 自动调大音量
            val maxVolume = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 7
            audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, (maxVolume * 0.8).toInt(), 0)

            val alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            // 释放旧的 MediaPlayer (如果存在)
            mediaPlayer?.release()
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@AlarmService, alert)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startVibration() {
        try {
            if (vibrator == null) vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 1000, 500, 1000, 500)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                engine.setLanguage(Locale.CHINESE)
                // 延迟开始播报
                handler.postDelayed({
                    requestAudioAndSpeak()
                }, 1000)
            }
        }
    }

    private fun requestAudioAndSpeak() {
        mediaPlayer?.setVolume(0.15f, 0.15f) // 压低背景音
        val currentTime = SimpleDateFormat("HH点mm分", Locale.getDefault()).format(Date())
        val text = "现在是 $currentTime，$periodName，请您及时吃药。"
        speakRepeatedly(text, 3)
    }

    private fun speakRepeatedly(text: String, count: Int) {
        if (count <= 0 || tts == null) return
        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "SERVICE_TTS_$count")
        handler.postDelayed({ speakRepeatedly(text, count - 1) }, 10000)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop()
        tts?.shutdown()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        vibrator?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
