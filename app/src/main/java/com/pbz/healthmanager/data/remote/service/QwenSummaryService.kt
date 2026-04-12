package com.pbz.healthmanager.data.remote.service

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.pbz.healthmanager.data.remote.QwenConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class QwenSummaryService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    var lastError: String = ""
        private set

    fun isConfigured(): Boolean = QwenConfig.API_KEY.isNotBlank() && QwenConfig.CHAT_URL.isNotBlank()

    fun generateSeniorHealthSummary(rawDataText: String): String? {
        if (!isConfigured()) return null
        val safeInput = if (rawDataText.length > 1800) rawDataText.take(1800) else rawDataText

        val systemPrompt = "你是一名适老化健康助手。请输出简洁中文总结，包含：近期吃药情况、血压情况、生活建议。语气温和，句子短，不要使用markdown。"
        val userPrompt = "以下是老年用户近期数据：$safeInput。请生成100~160字总结。"

        val models = listOf(
            QwenConfig.MODEL,
            "qwen-plus",
            "qwen-plus-latest",
            "qwen-turbo",
            "qwen-max-latest"
        ).distinct()
        models.forEach { model ->
            val payload = JsonObject().apply {
                addProperty("model", model)
                add("messages", gson.toJsonTree(listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt)
                )))
                addProperty("temperature", 0.3)
                addProperty("max_tokens", 260)
            }

            val request = Request.Builder()
                .url(QwenConfig.CHAT_URL)
                .post(gson.toJson(payload).toRequestBody("application/json".toMediaType()))
                .addHeader("Authorization", "Bearer ${QwenConfig.API_KEY}")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code}: $body"
                    return@use
                }
                val json = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
                if (json == null) {
                    lastError = "响应解析失败: ${body.take(120)}"
                    return@use
                }
                val choices = json.getAsJsonArray("choices")
                if (choices != null && choices.size() > 0) {
                    val text = choices[0].asJsonObject
                        .getAsJsonObject("message")
                        ?.get("content")
                        ?.asString
                        ?.trim()
                    if (!text.isNullOrBlank()) {
                        lastError = ""
                        return text
                    }
                }
                lastError = "空响应: $body"
            }
        }
        return null
    }
}
