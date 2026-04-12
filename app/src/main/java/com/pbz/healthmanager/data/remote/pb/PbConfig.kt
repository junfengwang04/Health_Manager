package com.pbz.healthmanager.data.remote.pb

import com.pbz.healthmanager.BuildConfig

object PbConfig {
    val baseUrl: String = BuildConfig.PB_BASE_URL.trim().removeSuffix("/")
    val adminEmail: String = BuildConfig.PB_ADMIN_EMAIL.trim()
    val adminPassword: String = BuildConfig.PB_ADMIN_PASSWORD.trim()
    const val COLLECTION_ELDER_USERS = "elder_users"
    const val COLLECTION_GUARDIAN_USERS = "guardian_users"
    const val COLLECTION_GUARDIAN_ELDER = "guardian_elder"
    const val COLLECTION_ELDER_HEALTH_SYNC = "elder_health_sync"
    const val COLLECTION_BIND_VERIFY_CODES = "bind_verify_codes"
    const val COLLECTION_MEDICATION_CATALOG = "medication_catalog"

    fun isConfigured(): Boolean {
        return baseUrl.isNotBlank() && adminEmail.isNotBlank() && adminPassword.isNotBlank()
    }
}
