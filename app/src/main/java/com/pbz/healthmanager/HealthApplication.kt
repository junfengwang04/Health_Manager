package com.pbz.healthmanager

import android.app.Application
import com.pbz.healthmanager.data.local.AppDatabase
import com.pbz.healthmanager.data.repository.HealthRepository

/**
 * 全局 Application 类，用于初始化数据库和 Repository
 */
class HealthApplication : Application() {

    // 初始化数据库单例
    val database by lazy { AppDatabase.Companion.getDatabase(this) }

    // 初始化业务仓库
    val repository by lazy { HealthRepository(applicationContext, database.healthDao()) }

    override fun onCreate() {
        super.onCreate()
    }
}
