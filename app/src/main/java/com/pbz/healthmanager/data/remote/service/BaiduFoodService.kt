package com.pbz.healthmanager.data.remote.service

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.pbz.healthmanager.data.remote.model.BaiduFoodResponse
import com.pbz.healthmanager.data.remote.model.BaiduTokenResponse
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.io.ByteArrayOutputStream
import java.io.IOException

import android.content.Context
import android.content.SharedPreferences
import com.pbz.healthmanager.data.remote.BaiduConfig

/**
 * 百度菜品识别服务类
 */
class BaiduFoodService(private val context: Context) {
    private val client = OkHttpClient()
    private val gson = Gson()
    
    private val prefs: SharedPreferences = context.getSharedPreferences("baidu_api_prefs", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean {
        return BaiduConfig.API_KEY.isNotBlank() &&
            BaiduConfig.SECRET_KEY.isNotBlank() &&
            BaiduConfig.TOKEN_URL.isNotBlank() &&
            BaiduConfig.FOOD_IDENTIFY_URL.isNotBlank()
    }

    /**
     * 获取百度 AccessToken（带 SharedPreferences 本地缓存）
     */
    @Throws(IOException::class)
    suspend fun getAccessToken(): String {
        if (!isConfigured()) throw IOException("百度配置缺失")
        val cachedToken = prefs.getString("access_token", null)
        val expiryTime = prefs.getLong("expiry_time", 0)

        // 如果缓存 Token 未过期（提前 1 小时失效以保证稳定），直接返回
        if (cachedToken != null && System.currentTimeMillis() < (expiryTime - 3600 * 1000)) {
            return cachedToken
        }

        val url = "${BaiduConfig.TOKEN_URL}?grant_type=client_credentials&client_id=${BaiduConfig.API_KEY}&client_secret=${BaiduConfig.SECRET_KEY}"
        val request = Request.Builder().url(url).build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Token Request Failed: $response"
                    Log.e("BAIDU_ERROR", errorMsg)
                    throw IOException(errorMsg)
                }
                val body = response.body?.string() ?: throw IOException("Empty body")
                Log.d("BAIDU_JSON", "Token Raw JSON: $body")
                
                val tokenRes = gson.fromJson(body, BaiduTokenResponse::class.java)
                val token = tokenRes.accessToken
                val newExpiryTime = System.currentTimeMillis() + (tokenRes.expiresIn * 1000)
                
                // 存入 SharedPreferences
                prefs.edit()
                    .putString("access_token", token)
                    .putLong("expiry_time", newExpiryTime)
                    .apply()
                    
                return token
            }
        } catch (e: Exception) {
            Log.e("BAIDU_FAILURE", "Token request failed: ${e.message}", e)
            throw e
        }
    }

    /**
     * 识别菜品（identifyFood 别名方法，保持逻辑一致）
     */
    suspend fun identifyFood(bitmap: Bitmap): BaiduFoodResponse {
        return detectDish(bitmap)
    }

    /**
     * 调用 dish_detect 接口识别菜品
     */
    @Throws(IOException::class)
    suspend fun detectDish(bitmap: Bitmap): BaiduFoodResponse {
        val token = getAccessToken()
        val base64Image = bitmapToBase64(bitmap)
        
        val url = "${BaiduConfig.FOOD_IDENTIFY_URL}?access_token=$token"
        val formBody = FormBody.Builder()
            .add("image", base64Image)
            .add("top_num", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorMsg = "Dish Detect Request Failed: $response"
                    Log.e("BAIDU_ERROR", errorMsg)
                    throw IOException(errorMsg)
                }
                
                val body = response.body?.string() ?: throw IOException("Empty body")
                // 打印原始 JSON 供调试
                Log.d("BAIDU_JSON", "Raw JSON: $body")
                
                return gson.fromJson(body, BaiduFoodResponse::class.java)
            }
        } catch (e: Exception) {
            Log.e("BAIDU_FAILURE", "Dish detection network failure: ${e.message}", e)
            throw e
        }
    }

    /**
     * 将图片压缩并转为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        var width = bitmap.width
        var height = bitmap.height
        val maxDimension = 1024
        
        // 压缩图片尺寸
        if (width > maxDimension || height > maxDimension) {
            val ratio = width.toFloat() / height.toFloat()
            if (width > height) {
                width = maxDimension
                height = (maxDimension / ratio).toInt()
            } else {
                height = maxDimension
                width = (maxDimension * ratio).toInt()
            }
        }
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
