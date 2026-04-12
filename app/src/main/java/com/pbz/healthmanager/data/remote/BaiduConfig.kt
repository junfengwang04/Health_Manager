package com.pbz.healthmanager.data.remote

import com.pbz.healthmanager.BuildConfig

/**
 * 百度智能云配置类
 */
object BaiduConfig {
    val API_KEY: String = BuildConfig.BAIDU_API_KEY.trim()
    val SECRET_KEY: String = BuildConfig.BAIDU_SECRET_KEY.trim()
    
    // 百度 Token 获取地址
    val TOKEN_URL: String = BuildConfig.BAIDU_TOKEN_URL.trim()
    // 菜品识别接口地址
    val FOOD_IDENTIFY_URL: String = BuildConfig.BAIDU_FOOD_IDENTIFY_URL.trim()
}
