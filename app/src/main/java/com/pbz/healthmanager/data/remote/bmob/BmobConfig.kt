package com.pbz.healthmanager.data.remote.bmob

import com.pbz.healthmanager.BuildConfig

object BmobConfig {
    val appId: String = BuildConfig.BMOB_APP_ID
    val restKey: String = BuildConfig.BMOB_REST_KEY
    val masterKey: String = BuildConfig.BMOB_MASTER_KEY
    val baseUrl: String = BuildConfig.BMOB_BASE_URL.trim().removeSuffix("/")
    fun isConfigured(): Boolean = appId.isNotBlank() && restKey.isNotBlank() && baseUrl.isNotBlank()
}

object BmobSession {
    var currentGuardianPhone: String = ""
    var currentElderPhone: String = ""
    var boundElderPhone: String = ""

    fun clear() {
        currentGuardianPhone = ""
        currentElderPhone = ""
        boundElderPhone = ""
    }
}
