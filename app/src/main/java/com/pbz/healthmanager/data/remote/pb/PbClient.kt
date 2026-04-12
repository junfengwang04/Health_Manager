package com.pbz.healthmanager.data.remote.pb

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

object PbClient {
    private var adminToken: String? = null
    private val gson = Gson()

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
        }
        defaultRequest {
            url(PbConfig.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    suspend fun adminAuth(): Result<String> = runCatching {
        val resp = client.post("/api/admins/auth-with-password") {
            setBody(mapOf(
                "identity" to PbConfig.adminEmail,
                "password" to PbConfig.adminPassword
            ))
        }.body<JsonObject>()
        val token = resp["token"]?.jsonPrimitive?.content.orEmpty()
        require(token.isNotBlank()) { "PocketBase 管理员认证失败" }
        adminToken = token
        token
    }

    private suspend fun ensureAdminToken(): String {
        val token = adminToken
        if (!token.isNullOrBlank()) return token
        return adminAuth().getOrElse { throw it }
    }

    suspend fun get(path: String, params: Map<String, Any?> = emptyMap()): Result<JsonObject> = runCatching {
        val token = ensureAdminToken()
        client.get(path) {
            bearerAuth(token)
            url {
                params.forEach { (k, v) ->
                    v?.let { parameters.append(k, it.toString()) }
                }
            }
        }.body<JsonObject>()
    }

    suspend fun post(path: String, body: Any, useAdminToken: Boolean = true): Result<JsonObject> = runCatching {
        client.post(path) {
            if (useAdminToken) {
                bearerAuth(ensureAdminToken())
            }
            setBody(encodeBody(body))
        }.body<JsonObject>()
    }

    suspend fun put(path: String, body: Any): Result<JsonObject> = runCatching {
        val token = ensureAdminToken()
        client.patch(path) {
            bearerAuth(token)
            setBody(encodeBody(body))
        }.body<JsonObject>()
    }

    suspend fun delete(path: String): Result<Unit> = runCatching {
        val token = ensureAdminToken()
        client.delete(path) {
            bearerAuth(token)
        }
        Unit
    }

    private fun encodeBody(body: Any): String {
        return if (body is JsonObject) body.toString() else gson.toJson(body)
    }
}
