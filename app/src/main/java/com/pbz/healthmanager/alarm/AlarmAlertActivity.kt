package com.pbz.healthmanager.alarm

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.pbz.healthmanager.HealthApplication
import com.pbz.healthmanager.data.remote.pb.PbCloudService
import com.pbz.healthmanager.data.remote.pb.PbSession
import com.pbz.healthmanager.ui.theme.HealthManagerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AlarmAlertActivity : ComponentActivity() {

    private var periodNameState = mutableStateOf("用药提醒")
    private var alarmId: String = ""
    private var medicationApprovalNumber: String? = null
    private var scheduledTime: Long = 0L
    private var isDecisionMode: Boolean = false
    private var hasScheduledFollowUp: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        setupLockScreen()

        setContent {
            HealthManagerTheme {
                AlarmAlertContent(
                    periodName = periodNameState.value,
                    isDecisionMode = isDecisionMode,
                    onAcknowledge = {
                        stopService(Intent(this, AlarmService::class.java))
                        finish()
                    },
                    onTaken = { finishWithDecision("已服用", true) },
                    onSkip = { finishWithDecision("本次不吃", false) },
                    onLater = {
                        scheduleFollowUp()
                        stopService(Intent(this, AlarmService::class.java))
                        finish()
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        periodNameState.value = intent?.getStringExtra("EXTRA_PERIOD_NAME") ?: "用药提醒"
        alarmId = intent?.getStringExtra("EXTRA_ALARM_ID") ?: ""
        medicationApprovalNumber = intent?.getStringExtra("EXTRA_MEDICATION_APPROVAL_NUMBER")
        scheduledTime = intent?.getLongExtra("EXTRA_SCHEDULED_TIME", System.currentTimeMillis()) ?: System.currentTimeMillis()
        isDecisionMode = intent?.getBooleanExtra("EXTRA_IS_DECISION_MODE", false) ?: false

        if (!isDecisionMode && !hasScheduledFollowUp) {
            scheduleFollowUp()
            hasScheduledFollowUp = true
        }
    }

    private fun finishWithDecision(status: String, isTaken: Boolean) {
        val approval = medicationApprovalNumber
        if (approval != null) {
            val repository = (application as HealthApplication).repository
            lifecycleScope.launch {
                repository.upsertMedicationDecision(
                    medicationApprovalNumber = approval,
                    scheduledTime = scheduledTime,
                    status = status,
                    actualTime = if (isTaken) System.currentTimeMillis() else null
                )
                val elderPhone = PbSession.currentElderPhone.trim()
                if (elderPhone.isNotBlank()) {
                    val snapshot = repository.exportCoreSnapshot()
                    PbCloudService().uploadElderSnapshot(elderPhone, snapshot)
                }
            }
        }
        stopService(Intent(this, AlarmService::class.java))
        finish()
    }

    private fun scheduleFollowUp() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + 30_000L
        val followUpIntent = Intent(this, AlarmReceiver::class.java).apply {
            putExtra("EXTRA_PERIOD_NAME", periodNameState.value)
            putExtra("EXTRA_ALARM_ID", alarmId.ifBlank { "followup_${System.currentTimeMillis()}" })
            putExtra("EXTRA_MEDICATION_APPROVAL_NUMBER", medicationApprovalNumber)
            putExtra("EXTRA_SCHEDULED_TIME", scheduledTime)
            putExtra("EXTRA_IS_DECISION_MODE", true)
        }
        val requestCode = (alarmId.ifBlank { "followup" } + "_followup").hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            followUpIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun setupLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}

@Composable
private fun AlarmAlertContent(
    periodName: String,
    isDecisionMode: Boolean,
    onAcknowledge: () -> Unit,
    onTaken: () -> Unit,
    onSkip: () -> Unit,
    onLater: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF1e5d2c)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = if (isDecisionMode) "您确认是否吃药？" else "该用药啦！",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = periodName,
                fontSize = 24.sp,
                color = Color.White.copy(alpha = 0.92f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(34.dp))

            if (isDecisionMode) {
                Button(
                    onClick = onTaken,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("吃了药", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1e5d2c))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onLater,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD1FAE5)),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("待会吃", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF065F46))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSkip,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("本次不吃", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF991B1B))
                }
            } else {
                Button(
                    onClick = onAcknowledge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Text("我知道了", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1e5d2c))
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "30秒后将询问是否服药",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
