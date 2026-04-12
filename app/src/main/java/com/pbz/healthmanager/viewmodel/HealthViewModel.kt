package com.pbz.healthmanager.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pbz.healthmanager.analysis.MedicineOcrAnalyzer
import com.pbz.healthmanager.data.local.entity.DietRecord
import com.pbz.healthmanager.data.local.entity.HealthIndex
import com.pbz.healthmanager.data.local.entity.Medication
import com.pbz.healthmanager.data.local.entity.RecognitionHistory
import com.pbz.healthmanager.data.local.model.MedicationAlarmWithMedication
import com.pbz.healthmanager.data.local.model.MedicationLogWithInfo
import com.pbz.healthmanager.data.remote.pb.ElderHealthSnapshot
import com.pbz.healthmanager.data.remote.pb.GuardianBoundElder
import com.pbz.healthmanager.data.remote.pb.LoginTarget
import com.pbz.healthmanager.data.remote.pb.PbBindRequest
import com.pbz.healthmanager.data.remote.pb.PbCloudService
import com.pbz.healthmanager.data.remote.pb.PbLoginUser
import com.pbz.healthmanager.data.remote.pb.PbSession
import com.pbz.healthmanager.data.remote.service.BaiduFoodService
import com.pbz.healthmanager.data.remote.service.QwenSummaryService
import io.ktor.client.plugins.ResponseException
import com.pbz.healthmanager.data.repository.HealthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import java.util.Calendar

data class LoginState(
    val loading: Boolean = false,
    val error: String = ""
)

data class GuardianMonitorSummary(
    val elderPhone: String,
    val elderName: String,
    val latestBloodPressure: String,
    val medicineProgress: String,
    val hasData: Boolean,
    val bloodPressureDanger: Boolean
)

class HealthViewModel(
    private val repository: HealthRepository,
    context: Context
) : ViewModel() {

    private val appContext = context.applicationContext
    private val baiduFoodService = BaiduFoodService(appContext)
    private val qwenSummaryService = QwenSummaryService()
    private val pbCloudService = PbCloudService()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _captureSuccess = MutableStateFlow(false)
    val captureSuccess = _captureSuccess.asStateFlow()

    private val _scanDebugStatus = MutableStateFlow("等待识别")
    val scanDebugStatus: StateFlow<String> = _scanDebugStatus.asStateFlow()
    private val _scanDebugData = MutableStateFlow("OCR: -- | 本地:-- | 云端:-- | 回填:--")
    val scanDebugData: StateFlow<String> = _scanDebugData.asStateFlow()

    private val _medicineResult = MutableStateFlow<Medication?>(null)
    val medicineResult = _medicineResult.asStateFlow()

    private val _dietResult = MutableStateFlow<DietRecord?>(null)
    val dietResult = _dietResult.asStateFlow()

    private val _capturedDietBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedDietBitmap = _capturedDietBitmap.asStateFlow()

    private val _capturedMedicineBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedMedicineBitmap = _capturedMedicineBitmap.asStateFlow()

    val medications: StateFlow<List<Medication>> = repository.allMedications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dietRecords: StateFlow<List<DietRecord>> = repository.allDietRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val healthIndices: StateFlow<List<HealthIndex>> = repository.allHealthIndices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recognitionHistory: StateFlow<List<RecognitionHistory>> = repository.recentRecognitionHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAlarmMedicationBindings: StateFlow<List<MedicationAlarmWithMedication>> =
        repository.activeMedicationAlarmBindings
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayMedicationLogs: StateFlow<List<MedicationLogWithInfo>> =
        repository.getMedicationLogsByDate(System.currentTimeMillis())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentMedicationLogs: StateFlow<List<MedicationLogWithInfo>> =
        repository.getMedicationLogsRecentDays(7)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allMedicationLogs: StateFlow<List<MedicationLogWithInfo>> =
        repository.getAllMedicationLogs()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiHealthSummaryDraft: StateFlow<String> =
        combine(recentMedicationLogs, healthIndices, activeAlarmMedicationBindings) { logs, indices, bindings ->
            buildAiHealthSummary(logs, indices, bindings)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            "正在整理近期健康数据，请稍候。"
        )

    private val _aiHealthSummaryText = MutableStateFlow("正在整理近期健康数据，请稍候。")
    val aiHealthSummaryText: StateFlow<String> = _aiHealthSummaryText.asStateFlow()

    private val _aiSummaryFromQwen = MutableStateFlow(false)
    val aiSummaryFromQwen: StateFlow<Boolean> = _aiSummaryFromQwen.asStateFlow()
    private val _aiSummaryStatus = MutableStateFlow("正在生成总结")
    val aiSummaryStatus: StateFlow<String> = _aiSummaryStatus.asStateFlow()
    private val _aiSummaryErrorDetail = MutableStateFlow("")
    val aiSummaryErrorDetail: StateFlow<String> = _aiSummaryErrorDetail.asStateFlow()

    private var lastSummaryInput: String = ""

    private val _loginState = MutableStateFlow(LoginState())
    val loginState: StateFlow<LoginState> = _loginState.asStateFlow()
    private val _boundElderAccount = MutableStateFlow("")
    val boundElderAccount: StateFlow<String> = _boundElderAccount.asStateFlow()
    private val _guardianBoundElders = MutableStateFlow<List<GuardianBoundElder>>(emptyList())
    val guardianBoundElders: StateFlow<List<GuardianBoundElder>> = _guardianBoundElders.asStateFlow()
    private val _currentGuardianPhone = MutableStateFlow("")
    val currentGuardianPhone: StateFlow<String> = _currentGuardianPhone.asStateFlow()
    private val _currentLoginDisplayName = MutableStateFlow("")
    val currentLoginDisplayName: StateFlow<String> = _currentLoginDisplayName.asStateFlow()

    private var currentElderPhone: String = ""
    private var currentGuardianPhoneInternal: String = ""
    private var currentLoginTarget: LoginTarget? = null
    private var guardianPullJob: Job? = null
    private var elderPushJob: Job? = null
    private var elderBindRequestPollJob: Job? = null
    private val loginIdentityPrefs by lazy { appContext.getSharedPreferences("login_identity_prefs", Context.MODE_PRIVATE) }
    private val _currentElderDeviceCode = MutableStateFlow("")
    val currentElderDeviceCode: StateFlow<String> = _currentElderDeviceCode.asStateFlow()
    private val _pendingBindRequests = MutableStateFlow<List<PbBindRequest>>(emptyList())
    val pendingBindRequests: StateFlow<List<PbBindRequest>> = _pendingBindRequests.asStateFlow()
    private val _bindActionMessage = MutableStateFlow("")
    val bindActionMessage: StateFlow<String> = _bindActionMessage.asStateFlow()
    private val _guardianMonitorSummaries = MutableStateFlow<List<GuardianMonitorSummary>>(emptyList())
    val guardianMonitorSummaries: StateFlow<List<GuardianMonitorSummary>> = _guardianMonitorSummaries.asStateFlow()

    init {
        viewModelScope.launch {
            aiHealthSummaryDraft.collect { draft ->
                _aiHealthSummaryText.value = draft
                refreshAiSummaryWithQwen(draft)
            }
        }
    }

    fun clearLoginError() {
        guardianPullJob?.cancel()
        guardianPullJob = null
        elderPushJob?.cancel()
        elderPushJob = null
        elderBindRequestPollJob?.cancel()
        elderBindRequestPollJob = null
        _loginState.value = _loginState.value.copy(error = "")
        _boundElderAccount.value = ""
        _guardianBoundElders.value = emptyList()
        _currentGuardianPhone.value = ""
        _currentLoginDisplayName.value = ""
        _currentElderDeviceCode.value = ""
        _pendingBindRequests.value = emptyList()
        _bindActionMessage.value = ""
        _guardianMonitorSummaries.value = emptyList()
        currentElderPhone = ""
        currentGuardianPhoneInternal = ""
        PbSession.clear()
        currentLoginTarget = null
    }

    fun logout() {
        clearLoginError()
    }

    fun refreshGuardianData() {
        if (currentLoginTarget != LoginTarget.GUARDIAN) return
        viewModelScope.launch(Dispatchers.IO) {
            pullBoundElderCoreDataToLocal()
        }
    }

    fun loginByCloud(account: String, password: String, target: LoginTarget, onSuccess: () -> Unit) {
        if (account.trim().isEmpty() || password.isEmpty()) {
            _loginState.value = LoginState(error = "请输入账号和密码")
            return
        }
        if (!pbCloudService.isConfigured()) {
            _loginState.value = LoginState(error = "PocketBase 配置缺失")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _loginState.value = LoginState(loading = true)
                val result = pbCloudService.login(account, password, target)
                if (result.isFailure) {
                    _loginState.value = LoginState(error = result.exceptionOrNull()?.message ?: "登录失败")
                    return@runCatching
                }
                val loginUser = result.getOrThrow()
                applyLoginSession(loginUser, target)
                _loginState.value = LoginState()
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { e ->
                Log.e("PbLogin", "loginByCloud crashed", e)
                _loginState.value = LoginState(error = e.message ?: "登录失败，请稍后重试")
            }
        }
    }

    fun registerByCloud(account: String, password: String, target: LoginTarget, onSuccess: () -> Unit) {
        val phone = account.trim()
        if (phone.length != 11) {
            _loginState.value = LoginState(error = "请输入11位手机号")
            return
        }
        if (password.length < 6) {
            _loginState.value = LoginState(error = "密码至少6位")
            return
        }
        if (!pbCloudService.isConfigured()) {
            _loginState.value = LoginState(error = "PocketBase 配置缺失")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                _loginState.value = LoginState(loading = true)
                val defaultName = "用户${phone.takeLast(4)}"
                val registerResult = pbCloudService.register(phone, password, defaultName, target)
                if (registerResult.isFailure) {
                    _loginState.value = LoginState(error = registerResult.exceptionOrNull()?.message ?: "注册失败")
                    return@runCatching
                }
                _loginState.value = LoginState()
                withContext(Dispatchers.Main) { onSuccess() }
            }.onFailure { e ->
                Log.e("PbLogin", "registerByCloud crashed", e)
                _loginState.value = LoginState(error = e.message ?: "注册失败，请稍后重试")
            }
        }
    }

    private suspend fun applyLoginSession(loginUser: PbLoginUser, target: LoginTarget) {
                if (shouldResetLocalDataForIdentity(target, loginUser.account)) {
                    repository.clearLocalCoreDataForAccountSwitch()
                }
                _boundElderAccount.value = loginUser.boundElderAccount
                _currentLoginDisplayName.value = loginUser.displayName
                currentLoginTarget = target
                if (target == LoginTarget.ELDER) {
                    guardianPullJob?.cancel()
                    guardianPullJob = null
                    elderPushJob?.cancel()
                    elderPushJob = null
                    elderBindRequestPollJob?.cancel()
                    elderBindRequestPollJob = null
                    currentElderPhone = loginUser.account
                    currentGuardianPhoneInternal = ""
                    _currentGuardianPhone.value = ""
                    PbSession.currentElderPhone = currentElderPhone
                    PbSession.currentGuardianPhone = ""
                    PbSession.boundElderPhone = ""
                    syncElderDataLoopOnLogin()
                    startElderAutoPush()
                    startElderBindRequestPolling()
                } else {
                    elderPushJob?.cancel()
                    elderPushJob = null
                    elderBindRequestPollJob?.cancel()
                    elderBindRequestPollJob = null
                    currentGuardianPhoneInternal = loginUser.account
                    _currentGuardianPhone.value = loginUser.account
                    currentElderPhone = ""
                    PbSession.currentGuardianPhone = currentGuardianPhoneInternal
                    PbSession.currentElderPhone = ""
                    PbSession.boundElderPhone = loginUser.boundElderAccount
                    pullBoundElderCoreDataToLocal()
                    startGuardianAutoPull()
                }
                _currentElderDeviceCode.value = loginUser.deviceCode
                persistLastLoginIdentity(target, loginUser.account)
    }

    private fun shouldResetLocalDataForIdentity(target: LoginTarget, account: String): Boolean {
        val lastRole = loginIdentityPrefs.getString("last_role", null)
        val lastAccount = loginIdentityPrefs.getString("last_account", null)
        val nextRole = target.name
        return lastRole != nextRole || lastAccount != account
    }

    private fun persistLastLoginIdentity(target: LoginTarget, account: String) {
        loginIdentityPrefs.edit()
            .putString("last_role", target.name)
            .putString("last_account", account)
            .apply()
    }

    fun refreshAiSummaryNow() {
        refreshAiSummaryWithQwen(aiHealthSummaryDraft.value, true)
    }

    private fun refreshAiSummaryWithQwen(draft: String, force: Boolean = false) {
        if (!force && draft == lastSummaryInput) return
        lastSummaryInput = draft
        if (!qwenSummaryService.isConfigured()) {
            _aiSummaryFromQwen.value = false
            _aiSummaryStatus.value = "未配置Qwen，使用本地总结"
            _aiSummaryErrorDetail.value = "Qwen API Key为空"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val qwenText = runCatching { qwenSummaryService.generateSeniorHealthSummary(draft) }.getOrNull()
            withContext(Dispatchers.Main) {
                if (!qwenText.isNullOrBlank()) {
                    _aiHealthSummaryText.value = qwenText
                    _aiSummaryFromQwen.value = true
                    _aiSummaryStatus.value = "Qwen总结已更新"
                    _aiSummaryErrorDetail.value = ""
                } else {
                    _aiSummaryFromQwen.value = false
                    _aiSummaryStatus.value = "Qwen调用失败，使用本地总结"
                    _aiSummaryErrorDetail.value = qwenSummaryService.lastError
                }
            }
        }
    }

    fun processDietImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _isProcessing.value = true
            _capturedDietBitmap.value = bitmap
            _dietResult.value = null
            runCatching {
                val response = withContext(Dispatchers.IO) { baiduFoodService.detectDish(bitmap) }
                val dish = response.result.firstOrNull() ?: return@runCatching null
                val calories = dish.calorie?.toDoubleOrNull() ?: 0.0
                withContext(Dispatchers.IO) {
                    repository.insertDietFromRecognition(bitmap, dish.name, calories)
                }
            }.onSuccess { record ->
                if (record != null) {
                    _dietResult.value = record
                    _captureSuccess.value = true
                    syncCoreIfElderLoggedIn()
                }
            }.onFailure { e ->
                Log.e("HealthViewModel", "processDietImage failed", e)
            }
            _isProcessing.value = false
        }
    }

    fun processMedicineOcrResult(result: MedicineOcrAnalyzer.OcrResult, bitmap: Bitmap? = null) {
        if (_isProcessing.value) return
        
        viewModelScope.launch {
            _isProcessing.value = true
            _medicineResult.value = null
            _scanDebugStatus.value = "识别中"
            if (bitmap != null) _capturedMedicineBitmap.value = bitmap

            runCatching {
                withContext(Dispatchers.IO) {
                    val normalizedApproval = normalizeApprovalNumber(result.approvalNumber)
                    val fromTextApproval = normalizeApprovalNumber(result.fullText)
                    val effectiveApproval = normalizedApproval ?: fromTextApproval

                    var localStatus = "未命中"
                    var cloudStatus = "未查询"
                    var cacheStatus = "未回填"
                    _scanDebugData.value = "OCR:${effectiveApproval ?: "未提取"} | 本地:$localStatus | 云端:$cloudStatus | 回填:$cacheStatus"

                    val byApproval = effectiveApproval?.let { repository.findMedicationByApprovalNumber(it) }
                    if (byApproval != null) {
                        localStatus = "命中"
                        _scanDebugData.value = "OCR:$effectiveApproval | 本地:$localStatus | 云端:$cloudStatus | 回填:$cacheStatus"
                        return@withContext byApproval
                    }

                    localStatus = "未命中"
                    if (!effectiveApproval.isNullOrBlank() && isNetworkAvailable()) {
                        cloudStatus = "查询中"
                        _scanDebugData.value = "OCR:$effectiveApproval | 本地:$localStatus | 云端:$cloudStatus | 回填:$cacheStatus"
                        val byCloud = withTimeoutOrNull(2500L) {
                            pbCloudService.findMedicationFromCloudByApproval(effectiveApproval).getOrNull()
                        }
                        if (byCloud != null) {
                            cloudStatus = "命中"
                            repository.insertMedication(byCloud)
                            cacheStatus = "成功"
                            _scanDebugData.value = "OCR:$effectiveApproval | 本地:$localStatus | 云端:$cloudStatus | 回填:$cacheStatus"
                            return@withContext byCloud
                        }
                        cloudStatus = "未命中/超时"
                    } else {
                        cloudStatus = if (effectiveApproval.isNullOrBlank()) "跳过(无主键)" else "跳过(无网络)"
                    }
                    _scanDebugData.value = "OCR:${effectiveApproval ?: "未提取"} | 本地:$localStatus | 云端:$cloudStatus | 回填:$cacheStatus"
                    null
                }
            }.onSuccess { medicine ->
                _medicineResult.value = medicine
                if (medicine != null) {
                    withContext(Dispatchers.IO) {
                        repository.insertRecognitionHistory(name = medicine.name, source = "scan")
                    }
                }
                _captureSuccess.value = medicine != null
                _scanDebugStatus.value = if (medicine != null) "识别成功：${medicine.name}" else "识别失败：未在本地和云端命中"
                if (medicine != null) syncCoreIfElderLoggedIn()
            }.onFailure { e ->
                Log.e("HealthViewModel", "processMedicineOcrResult failed", e)
                _captureSuccess.value = false
                _scanDebugStatus.value = "识别异常：${e.message.orEmpty()}"
            }

            _isProcessing.value = false
        }
    }

    fun resetScannerState() {
        _captureSuccess.value = false
    }

    fun resetCaptureStatus() {
        _captureSuccess.value = false
        _scanDebugStatus.value = "等待识别"
        _scanDebugData.value = "OCR: -- | 本地:-- | 云端:-- | 回填:--"
    }

    fun deleteMedication(medication: Medication) {
        viewModelScope.launch {
            repository.deleteMedication(medication)
            syncCoreIfElderLoggedIn()
        }
    }

    fun deleteDiet(dietRecord: DietRecord) {
        viewModelScope.launch {
            repository.deleteDietRecord(dietRecord)
            syncCoreIfElderLoggedIn()
        }
    }

    fun deleteHealthIndex(healthIndex: HealthIndex) {
        viewModelScope.launch {
            repository.deleteHealthIndex(healthIndex)
            syncCoreIfElderLoggedIn()
        }
    }

    fun addManualSearchHistory(keyword: String) {
        if (keyword.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertRecognitionHistory(name = keyword.trim(), source = "manual")
            syncCoreIfElderLoggedIn()
        }
    }

    fun deleteRecognitionHistoryById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteRecognitionHistoryById(id)
            syncCoreIfElderLoggedIn()
        }
    }

    fun clearRecognitionHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearRecognitionHistory()
            syncCoreIfElderLoggedIn()
        }
    }

    fun ensureSevenDayBloodPressureSeedData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureRecentSevenDaysBloodPressureSeedData()
        }
    }

    fun forceResetSevenDayBloodPressureSeedData() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.forceResetRecentSevenDaysBloodPressureSeedData()
            syncCoreIfElderLoggedIn()
        }
    }

    fun addBloodPressureRecord(systolic: Int, diastolic: Int, measureTime: Long = System.currentTimeMillis()) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addBloodPressureRecord(systolic, diastolic, measureTime)
            syncCoreIfElderLoggedIn()
        }
    }

    private suspend fun syncElderCoreDataToCloud() {
        val elderPhone = currentElderPhone.trim()
        if (elderPhone.isBlank() || !pbCloudService.isConfigured()) return
        runCatching {
            val snapshot = repository.exportCoreSnapshot()
            val uploadResult = pbCloudService.uploadElderSnapshot(elderPhone, snapshot)
            if (uploadResult.isFailure) {
                val code = extractHttpCode(uploadResult.exceptionOrNull())
                val msg = uploadResult.exceptionOrNull()?.message.orEmpty().take(80)
                Log.e("PbSync", "upload elder snapshot failed code=$code msg=$msg", uploadResult.exceptionOrNull())
                showSyncToast("老人端上传失败 code=$code $msg")
            } else {
                Log.d("PbSync", "upload elder snapshot success")
            }
        }.onFailure {
            Log.e("PbSync", "syncElderCoreDataToCloud crashed", it)
            showSyncToast("老人端同步异常 code=${extractHttpCode(it)}")
        }
    }

    private suspend fun syncElderDataLoopOnLogin() {
        val elderPhone = currentElderPhone.trim()
        if (elderPhone.isBlank() || !pbCloudService.isConfigured()) return
        runCatching {
            val cloudSnapshot = pbCloudService.fetchElderSnapshot(elderPhone).getOrNull()
            if (cloudSnapshot != null && snapshotHasData(cloudSnapshot)) {
                repository.importCoreSnapshot(cloudSnapshot)
                Log.d("PbSync", "syncElderDataLoopOnLogin import success")
                showSyncToast("登录同步完成")
                return@runCatching
            }
            val localSnapshot = repository.exportCoreSnapshot()
            if (snapshotHasData(localSnapshot)) {
                val uploadResult = pbCloudService.uploadElderSnapshot(elderPhone, localSnapshot)
                if (uploadResult.isFailure) {
                    val code = extractHttpCode(uploadResult.exceptionOrNull())
                    val msg = uploadResult.exceptionOrNull()?.message.orEmpty().take(80)
                    Log.e("PbSync", "syncElderDataLoopOnLogin upload failed code=$code msg=$msg", uploadResult.exceptionOrNull())
                    showSyncToast("登录同步上传失败 code=$code $msg")
                    return@runCatching
                }
                Log.d("PbSync", "syncElderDataLoopOnLogin upload success")
            }
        }.onFailure {
            Log.e("PbSync", "syncElderDataLoopOnLogin crashed", it)
            showSyncToast("登录同步异常 code=${extractHttpCode(it)}")
        }
    }

    private suspend fun pullBoundElderCoreDataToLocal() {
        if (!pbCloudService.isConfigured()) return
        val guardianPhone = currentGuardianPhoneInternal.trim()
        if (guardianPhone.isBlank()) return
        runCatching {
            val boundElders = pbCloudService.resolveBoundElders(guardianPhone).getOrNull().orEmpty()
            _guardianBoundElders.value = boundElders
            refreshGuardianMonitorSummaries(boundElders)
            val elderByRelation = boundElders.firstOrNull()?.phone.orEmpty()
            val elderPhone = elderByRelation.ifBlank { _boundElderAccount.value.trim() }
            if (elderPhone.isBlank()) {
                _boundElderAccount.value = ""
                PbSession.boundElderPhone = ""
                repository.clearLocalCoreDataForAccountSwitch()
                return
            }
            _boundElderAccount.value = elderPhone
            PbSession.boundElderPhone = elderPhone
            val snapshot = pbCloudService.fetchElderSnapshot(elderPhone).getOrNull()
            if (snapshot == null) {
                repository.clearLocalCoreDataForAccountSwitch()
                return
            }
            repository.importCoreSnapshot(snapshot)
        }.onFailure {
            Log.e("PbSync", "pullBoundElderCoreDataToLocal crashed", it)
            showSyncToast("子女端拉取失败 code=${extractHttpCode(it)}")
        }
    }

    private fun startGuardianAutoPull() {
        guardianPullJob?.cancel()
        guardianPullJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && currentLoginTarget == LoginTarget.GUARDIAN) {
                pullBoundElderCoreDataToLocal()
                delay(12_000L)
            }
        }
    }

    private fun startElderAutoPush() {
        elderPushJob?.cancel()
        elderPushJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && currentLoginTarget == LoginTarget.ELDER) {
                syncElderCoreDataToCloud()
                delay(20_000L)
            }
        }
    }

    private fun startElderBindRequestPolling() {
        elderBindRequestPollJob?.cancel()
        elderBindRequestPollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive && currentLoginTarget == LoginTarget.ELDER) {
                refreshPendingBindRequestsForElderInternal()
                delay(10_000L)
            }
        }
    }

    fun submitBindRequestByDeviceCode(deviceCode: String) {
        if (currentLoginTarget != LoginTarget.GUARDIAN) {
            _bindActionMessage.value = "当前不是子女账号"
            return
        }
        val guardianPhone = currentGuardianPhoneInternal.trim()
        if (guardianPhone.length != 11) {
            _bindActionMessage.value = "子女手机号异常"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val code = deviceCode.trim()
            val result = pbCloudService.createBindRequestByDeviceCode(guardianPhone, code)
            _bindActionMessage.value = if (result.isSuccess) {
                "绑定请求已发送，请老人端确认"
            } else {
                result.exceptionOrNull()?.message ?: "绑定失败"
            }
            if (result.isSuccess) {
                pullBoundElderCoreDataToLocal()
            }
        }
    }

    fun clearBindActionMessage() {
        _bindActionMessage.value = ""
    }

    fun refreshPendingBindRequestsForElder() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshPendingBindRequestsForElderInternal()
        }
    }

    private suspend fun refreshPendingBindRequestsForElderInternal() {
        val elderPhone = currentElderPhone.trim()
        if (elderPhone.length != 11 || currentLoginTarget != LoginTarget.ELDER) return
        val requests = pbCloudService.fetchAllBindRequestsForElder(elderPhone).getOrNull().orEmpty()
        _pendingBindRequests.value = requests
        if (_currentElderDeviceCode.value.isBlank()) {
            _currentElderDeviceCode.value = pbCloudService.ensureElderDeviceCode(elderPhone).getOrNull().orEmpty()
        }
    }

    fun approveBindRequest(guardianPhone: String) {
        val elderPhone = currentElderPhone.trim()
        if (elderPhone.length != 11 || guardianPhone.length != 11) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = pbCloudService.approveBindRequest(elderPhone, guardianPhone)
            _bindActionMessage.value = if (result.isSuccess) {
                "已确认绑定 $guardianPhone"
            } else {
                result.exceptionOrNull()?.message ?: "确认绑定失败"
            }
            refreshPendingBindRequestsForElder()
            syncCoreIfElderLoggedIn()
        }
    }

    fun cancelBindRequestFromElder(guardianPhone: String) {
        val elderPhone = currentElderPhone.trim()
        if (elderPhone.length != 11 || guardianPhone.length != 11) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = pbCloudService.cancelBindRelation(elderPhone, guardianPhone)
            _bindActionMessage.value = if (result.isSuccess) {
                "已取消绑定 $guardianPhone"
            } else {
                result.exceptionOrNull()?.message ?: "取消绑定失败"
            }
            refreshPendingBindRequestsForElderInternal()
        }
    }

    fun cancelBindRequestFromGuardian(elderPhone: String) {
        val guardianPhone = currentGuardianPhoneInternal.trim()
        if (guardianPhone.length != 11 || elderPhone.length != 11) return
        viewModelScope.launch(Dispatchers.IO) {
            val result = pbCloudService.cancelBindRelation(elderPhone, guardianPhone)
            _bindActionMessage.value = if (result.isSuccess) {
                "已取消与 $elderPhone 的绑定"
            } else {
                result.exceptionOrNull()?.message ?: "取消绑定失败"
            }
            pullBoundElderCoreDataToLocal()
        }
    }

    fun selectGuardianElderForDetails(elderPhone: String, onDone: () -> Unit) {
        if (elderPhone.length != 11) return
        viewModelScope.launch(Dispatchers.IO) {
            _boundElderAccount.value = elderPhone
            PbSession.boundElderPhone = elderPhone
            val snapshot = pbCloudService.fetchElderSnapshot(elderPhone).getOrNull()
            if (snapshot != null) {
                repository.importCoreSnapshot(snapshot)
            } else {
                repository.clearLocalCoreDataForAccountSwitch()
            }
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private suspend fun refreshGuardianMonitorSummaries(boundElders: List<GuardianBoundElder>) {
        if (boundElders.isEmpty()) {
            _guardianMonitorSummaries.value = emptyList()
            return
        }
        val summaries = boundElders.map { elder ->
            val snapshot = pbCloudService.fetchElderSnapshot(elder.phone).getOrNull()
            val health = snapshot?.healthIndices.orEmpty()
            val logs = snapshot?.medicationLogs.orEmpty()
            val latestBp = health.asSequence()
                .filter { it.type == "血压" }
                .maxByOrNull { it.measureTime }
                ?.remark
                ?.substringAfter("DEMO_BP|")
                ?.takeIf { it.contains("/") }
                ?: "--/--"
            val taken = logs.count { it.status.contains("已服") }
            val total = logs.size
            val medicineProgress = "$taken/$total"
            val bpDanger = runCatching {
                val parts = latestBp.split("/")
                val s = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val d = parts.getOrNull(1)?.toIntOrNull() ?: 0
                s >= 140 || d >= 90
            }.getOrDefault(false)
            GuardianMonitorSummary(
                elderPhone = elder.phone,
                elderName = elder.name,
                latestBloodPressure = latestBp,
                medicineProgress = medicineProgress,
                hasData = snapshot != null,
                bloodPressureDanger = bpDanger
            )
        }
        _guardianMonitorSummaries.value = summaries
    }

    private fun syncCoreIfElderLoggedIn() {
        if (currentLoginTarget != LoginTarget.ELDER) return
        viewModelScope.launch(Dispatchers.IO) {
            syncElderCoreDataToCloud()
        }
    }

    private fun snapshotHasData(snapshot: ElderHealthSnapshot): Boolean {
        val validCloudMeds = snapshot.medications.any { isValidApprovalNumber(it.approvalNumber) }
        return validCloudMeds ||
            snapshot.medicationAlarmBindings.isNotEmpty() ||
            snapshot.medicationLogs.isNotEmpty() ||
            snapshot.dietRecords.isNotEmpty() ||
            snapshot.recognitionHistories.isNotEmpty() ||
            snapshot.healthIndices.isNotEmpty()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun addTemporaryBloodPressureRecordForDebug() {
        val now = Calendar.getInstance()
        val isMorning = now.get(Calendar.HOUR_OF_DAY) < 12
        val systolic = if (isMorning) 138 else 131
        val diastolic = if (isMorning) 88 else 82
        addBloodPressureRecord(systolic = systolic, diastolic = diastolic, measureTime = now.timeInMillis)
    }

    private fun normalizeApprovalNumber(raw: String?): String? {
        val source = raw?.trim() ?: return null
        if (source.isEmpty()) return null
        val strict = Regex("国药准字\\s*([A-Z])(\\d{8})", RegexOption.IGNORE_CASE).find(source)
        if (strict != null) {
            return "国药准字${strict.groupValues[1].uppercase()}${strict.groupValues[2]}"
        }
        val cleaned = source.replace("\\s".toRegex(), "")
        val match = Regex("国[药藥]准[字宇]?([A-Za-z])([0-9OoIl|SsBb]{8})", RegexOption.IGNORE_CASE)
            .find(cleaned)
            ?: Regex("([A-Za-z])([0-9OoIl|SsBb]{8})", RegexOption.IGNORE_CASE).find(cleaned)
            ?: return null
        val letter = match.groupValues[1].uppercase()
        val digits = match.groupValues[2]
            .uppercase()
            .map {
                when (it) {
                    'O' -> '0'
                    'I', 'L', '|' -> '1'
                    'S' -> '5'
                    'B' -> '8'
                    else -> it
                }
            }
            .joinToString("")
        if (!digits.all { it.isDigit() }) return null
        return "国药准字$letter$digits"
    }

    private fun isValidApprovalNumber(raw: String?): Boolean {
        val normalized = normalizeApprovalNumber(raw) ?: return false
        return Regex("国药准字[A-Z]\\d{8}").matches(normalized)
    }

    private fun extractHttpCode(error: Throwable?): String {
        var cursor = error
        while (cursor != null) {
            if (cursor is ResponseException) {
                return cursor.response.status.value.toString()
            }
            val msg = cursor.message.orEmpty()
            val code = Regex("\\b([1-5]\\d{2})\\b").find(msg)?.groupValues?.getOrNull(1)
            if (!code.isNullOrBlank()) return code
            cursor = cursor.cause
        }
        return "unknown"
    }

    private fun showSyncToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildAiHealthSummary(
        logs: List<MedicationLogWithInfo>,
        indices: List<HealthIndex>,
        bindings: List<MedicationAlarmWithMedication>
    ): String {
        val totalExpected = bindings.size * 7
        val doneCount = logs.count { it.log.status == "已服用" }
        val skipCount = logs.count { it.log.status == "本次不吃" || it.log.status == "已跳过" }
        val adherence = if (totalExpected > 0) (doneCount * 100 / totalExpected) else 0

        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val bpPairs = indices
            .filter { it.measureTime >= sevenDaysAgo && it.type.contains("血压") }
            .mapNotNull { parseBpPair(it) }
        val avgSys = if (bpPairs.isNotEmpty()) bpPairs.map { it.first }.average().toInt() else 0
        val avgDia = if (bpPairs.isNotEmpty()) bpPairs.map { it.second }.average().toInt() else 0
        val bpHighCount = bpPairs.count { it.first >= 135 || it.second >= 85 }

        val medPart = when {
            totalExpected == 0 -> "近期还没有设置用药闹钟，请先设定固定时段，方便持续管理。"
            else -> "近7天预计服药 $totalExpected 次，已服药 $doneCount 次，未服/跳过 $skipCount 次，服药完成率约 $adherence%。"
        }

        val bpPart = when {
            bpPairs.isEmpty() -> "近7天暂无有效血压记录，建议每天早晚各测一次。"
            else -> "近7天平均血压约 ${avgSys}/${avgDia}mmHg，超出家庭自测参考线（135/85）的次数为 $bpHighCount 次。"
        }

        val advice = when {
            bpPairs.isEmpty() -> "建议先建立“早晚测压+按时服药”习惯，饮食以清淡少盐为主。"
            bpHighCount >= 3 -> "建议近期严格低盐清淡饮食，避免熬夜，保持规律服药，并连续观察晨间血压。"
            adherence in 1..79 -> "建议把闹钟集中在固定时段，家属协助提醒，减少漏服。"
            else -> "整体状态较稳定，请继续保持清淡饮食、规律作息和按时复测。"
        }
        return "健康管家总结：$medPart $bpPart 建议：$advice"
    }

    private fun parseBpPair(index: HealthIndex): Pair<Int, Int>? {
        val remark = index.remark.orEmpty()
        val match = Regex("(\\d{2,3})\\D+(\\d{2,3})").find(remark)
        if (match != null) {
            val sys = match.groupValues[1].toIntOrNull()
            val dia = match.groupValues[2].toIntOrNull()
            if (sys != null && dia != null) return sys to dia
        }
        val sys = index.value.toInt()
        val dia = Regex(".*?(\\d{2,3})").find(remark.substringAfter('/'))?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 80
        return sys to dia
    }
}
