package com.pbz.healthmanager.data.remote.model

import com.google.gson.annotations.SerializedName

/**
 * 百度菜品识别响应模型
 */
data class BaiduFoodResponse(
    @SerializedName("log_id") val logId: Long,
    @SerializedName("result_num") val resultNum: Int,
    val result: List<CalorieResult>
)

/**
 * 菜品识别结果项
 */
data class CalorieResult(
    val name: String,
    val calorie: String?, // 百度返回的是字符串格式的热量
    val probability: String, // 概率也可能是字符串
    @SerializedName("has_calorie") val hasCalorie: Boolean
)

/**
 * 百度 AccessToken 响应模型
 */
data class BaiduTokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("expires_in") val expiresIn: Long,
    @SerializedName("refresh_token") val refreshToken: String?,
    @SerializedName("scope") val scope: String?,
    @SerializedName("session_key") val sessionKey: String?,
    @SerializedName("session_secret") val sessionSecret: String?
)
