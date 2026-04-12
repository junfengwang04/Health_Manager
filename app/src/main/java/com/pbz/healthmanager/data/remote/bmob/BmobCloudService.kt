package com.pbz.healthmanager.data.remote.bmob

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetAddress
import java.net.UnknownHostException

enum class LoginTarget {
    ELDER,
    GUARDIAN
}

data class BmobLoginUser(
    val objectId: String,
    val account: String,
    val target: LoginTarget,
    val boundElderAccount: String = ""
)

data class ElderHealthSnapshot(
    val medications: List<com.pbz.healthmanager.data.local.entity.Medication> = emptyList(),
    val medicationLogs: List<com.pbz.healthmanager.data.local.entity.MedicationLog> = emptyList(),
    val healthIndices: List<com.pbz.healthmanager.data.local.entity.HealthIndex> = emptyList()
)

data class GuardianBoundElder(
    val phone: String,
    val name: String
)

class BmobCloudService(
    private val client: OkHttpClient = OkHttpClient.Builder().dns(BmobFallbackDns()).build(),
    private val gson: Gson = Gson()
) {
    private val baseUrlCandidates: List<String> = listOf(
        BmobConfig.baseUrl
    ).map { it.trim().removeSuffix("/") }.filter { it.isNotBlank() }.distinct()

    fun isConfigured(): Boolean = BmobConfig.isConfigured()

    fun uploadElderSnapshot(elderPhone: String, snapshot: ElderHealthSnapshot): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("Bmob未配置"))
        if (elderPhone.isBlank()) return Result.failure(IllegalArgumentException("老人手机号为空"))
        return runCatching {
            val where = JsonObject().apply { addProperty("elderPhone", elderPhone) }
            val query = get("/1/classes/ElderHealthSync", mapOf("where" to gson.toJson(where), "limit" to "1"))
            val payload = JsonObject().apply {
                addProperty("elderPhone", elderPhone)
                addProperty("payload", gson.toJson(snapshot))
                addProperty("updatedAtClient", System.currentTimeMillis())
            }
            val existing = query?.getAsJsonArray("results")?.firstOrNull()?.asJsonObject
            val objectId = existing?.get("objectId")?.asString
            if (objectId.isNullOrBlank()) {
                post("/1/classes/ElderHealthSync", payload)
            } else {
                put("/1/classes/ElderHealthSync/$objectId", payload)
            }
        }
    }

    fun fetchElderSnapshot(elderPhone: String): Result<ElderHealthSnapshot?> {
        if (!isConfigured()) return Result.failure(IllegalStateException("Bmob未配置"))
        if (elderPhone.isBlank()) return Result.failure(IllegalArgumentException("老人手机号为空"))
        return runCatching {
            val where = JsonObject().apply { addProperty("elderPhone", elderPhone) }
            val response = get("/1/classes/ElderHealthSync", mapOf("where" to gson.toJson(where), "limit" to "1"))
                ?: return@runCatching null
            val first = response.getAsJsonArray("results")?.firstOrNull()?.asJsonObject ?: return@runCatching null
            val payload = first.get("payload")?.asString ?: return@runCatching null
            gson.fromJson(payload, ElderHealthSnapshot::class.java)
        }
    }

    fun resolveBoundElderPhone(guardianPhone: String): Result<String> {
        return runCatching {
            resolveBoundElders(guardianPhone).getOrElse { emptyList() }.firstOrNull()?.phone.orEmpty()
        }
    }

    fun resolveBoundElders(guardianPhone: String): Result<List<GuardianBoundElder>> {
        if (!isConfigured()) return Result.failure(IllegalStateException("Bmob未配置"))
        if (guardianPhone.isBlank()) return Result.failure(IllegalArgumentException("子女手机号为空"))
        return runCatching {
            val relationFields = listOf("guardianPhone", "guardianAccount", "guardian", "phone")
            val relationTables = listOf("GuardianElder", "GuardianElderRelation")
            val elderPhones = linkedSetOf<String>()
            for (table in relationTables) {
                for (field in relationFields) {
                    val where = JsonObject().apply { addProperty(field, guardianPhone) }
                    val response = get(
                        "/1/classes/$table",
                        mapOf("where" to gson.toJson(where), "limit" to "50")
                    ) ?: continue
                    val results = response.getAsJsonArray("results") ?: continue
                    results.forEach { row ->
                        val elder = extractBoundElderAccount(row.asJsonObject)
                        if (elder.isNotBlank()) {
                            elderPhones.add(elder)
                        }
                    }
                }
            }
            elderPhones.map { phone ->
                val elderJson = fetchElderUserByPhone(phone)
                val name = elderJson?.let { extractElderName(it) }.orEmpty().ifBlank { maskPhone(phone) }
                GuardianBoundElder(phone = phone, name = name)
            }
        }
    }

    fun login(account: String, password: String, target: LoginTarget): Result<BmobLoginUser> {
        if (!isConfigured()) return Result.failure(IllegalStateException("Bmob未配置"))
        val accountText = account.trim()
        if (accountText.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("请输入账号和密码"))
        }
        return runCatching {
            val user = when (target) {
                LoginTarget.ELDER -> loginFromTable("ElderUser", accountText, password, LoginTarget.ELDER)
                LoginTarget.GUARDIAN -> loginFromTable("GuardianUser", accountText, password, LoginTarget.GUARDIAN)
            }
            if (user != null) return@runCatching enforceGuardianBinding(user)
            throw IllegalArgumentException("账号或密码错误")
        }
    }

    private fun loginFromTable(
        tableName: String,
        account: String,
        password: String,
        target: LoginTarget
    ): BmobLoginUser? {
        val where = JsonObject().apply {
            add(
                "\$and",
                JsonArray().apply {
                    add(
                        JsonObject().apply {
                            add(
                                "\$or",
                                JsonArray().apply {
                                    add(JsonObject().apply { addProperty("phone", account) })
                                    add(JsonObject().apply { addProperty("account", account) })
                                }
                            )
                        }
                    )
                    add(
                        JsonObject().apply {
                            add(
                                "\$or",
                                JsonArray().apply {
                                    add(JsonObject().apply { addProperty("password", password) })
                                }
                            )
                        }
                    )
                }
            )
        }
        val response = get(
            "/1/classes/$tableName",
            mapOf("where" to gson.toJson(where), "limit" to "1")
        ) ?: return null
        val first = response.getAsJsonArray("results")?.firstOrNull()?.asJsonObject ?: return null
        val objectId = first.get("objectId")?.asString?.takeIf { it.isNotBlank() } ?: return null
        val accountValue = listOf("phone", "account")
            .firstNotNullOfOrNull { key -> first.get(key)?.asString?.takeIf { it.isNotBlank() } }
            ?: account
        val elderAccount = if (target == LoginTarget.GUARDIAN) {
            resolveGuardianBoundElder(accountValue, first)
        } else {
            ""
        }
        return BmobLoginUser(
            objectId = objectId,
            account = accountValue,
            target = target,
            boundElderAccount = elderAccount
        )
    }

    private fun get(path: String, query: Map<String, String>): JsonObject? {
        return executeWithFallback(path) { base ->
            val baseUrl = "$base$path".toHttpUrlOrNull() ?: return@executeWithFallback null
            val urlBuilder = baseUrl.newBuilder()
            query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .get()
                .header("X-Bmob-Application-Id", BmobConfig.appId)
                .header("X-Bmob-REST-API-Key", BmobConfig.restKey)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                val json = runCatching { gson.fromJson(body, JsonObject::class.java) }.getOrNull()
                if (!response.isSuccessful || json == null) return@executeWithFallback null
                json
            }
        }
    }

    private fun post(path: String, body: JsonObject): JsonObject? {
        val requestBody = gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType())
        return executeWithFallback(path) { base ->
            val request = Request.Builder()
                .url("$base$path")
                .post(requestBody)
                .header("X-Bmob-Application-Id", BmobConfig.appId)
                .header("X-Bmob-REST-API-Key", BmobConfig.restKey)
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val json = runCatching { gson.fromJson(bodyText, JsonObject::class.java) }.getOrNull()
                if (!response.isSuccessful) return@executeWithFallback null
                json
            }
        }
    }

    private fun put(path: String, body: JsonObject): JsonObject? {
        val requestBody = gson.toJson(body).toRequestBody("application/json; charset=utf-8".toMediaType())
        return executeWithFallback(path) { base ->
            val request = Request.Builder()
                .url("$base$path")
                .put(requestBody)
                .header("X-Bmob-Application-Id", BmobConfig.appId)
                .header("X-Bmob-REST-API-Key", BmobConfig.restKey)
                .header("Content-Type", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                val json = runCatching { gson.fromJson(bodyText, JsonObject::class.java) }.getOrNull()
                if (!response.isSuccessful) return@executeWithFallback null
                json
            }
        }
    }

    private fun executeWithFallback(
        path: String,
        action: (baseUrl: String) -> JsonObject?
    ): JsonObject? {
        var unknownHostError: UnknownHostException? = null
        baseUrlCandidates.forEach { base ->
            try {
                val result = action(base)
                if (result != null) return result
            } catch (e: UnknownHostException) {
                unknownHostError = e
            }
        }
        if (unknownHostError != null) {
            throw IllegalStateException(
                "无法解析Bmob域名，已尝试: ${baseUrlCandidates.joinToString()}",
                unknownHostError
            )
        }
        return null
    }

    private fun JsonArray.firstOrNull() = if (size() > 0) get(0) else null

    private fun extractBoundElderAccount(json: JsonObject): String {
        return listOf("elderPhone", "ElderAccount", "elderAccount", "boundElderAccount", "elder_id", "elderId")
            .firstNotNullOfOrNull { key ->
                json.get(key)?.asString?.trim()?.takeIf { it.isNotBlank() && it != "\"\"" }
            }.orEmpty()
    }

    private fun resolveGuardianBoundElder(guardianAccount: String, userJson: JsonObject): String {
        val fromUser = extractBoundElderAccount(userJson)
        if (fromUser.isNotBlank()) return fromUser
        return resolveBoundElders(guardianAccount).getOrElse { emptyList() }.firstOrNull()?.phone.orEmpty()
    }

    private fun enforceGuardianBinding(user: BmobLoginUser): BmobLoginUser {
        return user
    }

    private fun fetchElderUserByPhone(elderPhone: String): JsonObject? {
        val where = JsonObject().apply {
            add(
                "\$or",
                JsonArray().apply {
                    add(JsonObject().apply { addProperty("phone", elderPhone) })
                    add(JsonObject().apply { addProperty("account", elderPhone) })
                }
            )
        }
        val response = get("/1/classes/ElderUser", mapOf("where" to gson.toJson(where), "limit" to "1")) ?: return null
        return response.getAsJsonArray("results")?.firstOrNull()?.asJsonObject
    }

    private fun extractElderName(json: JsonObject): String {
        return listOf("name", "elderName", "nickname", "nickName")
            .firstNotNullOfOrNull { key -> json.get(key)?.asString?.trim()?.takeIf { it.isNotBlank() } }
            .orEmpty()
    }

    private fun maskPhone(phone: String): String {
        if (phone.length < 7) return phone
        return phone.replaceRange(3, 7, "****")
    }
}

private class BmobFallbackDns : Dns {
    private val hostIpMap = mapOf(
        "api.bmobapp.com" to listOf("120.55.242.134"),
        "api.bmob.cn" to listOf("39.107.206.151"),
        "api2.bmob.cn" to listOf("39.107.206.151")
    )

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            Dns.SYSTEM.lookup(hostname)
        } catch (e: UnknownHostException) {
            val fallback = hostIpMap[hostname]?.mapNotNull { ip ->
                runCatching { InetAddress.getByName(ip) }.getOrNull()
            }.orEmpty()
            if (fallback.isNotEmpty()) fallback else throw e
        }
    }
}
