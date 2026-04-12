package com.pbz.healthmanager.data.remote

import com.pbz.healthmanager.BuildConfig

object QwenConfig {
    val API_KEY: String = BuildConfig.QWEN_API_KEY.trim()
    val CHAT_URL: String = BuildConfig.QWEN_CHAT_URL.trim()
    val MODEL: String = BuildConfig.QWEN_MODEL.trim().ifBlank { "qwen3.5-flash" }
}
