package com.pbz.healthmanager.data.remote.pb

import com.google.gson.Gson
import com.pbz.healthmanager.data.local.entity.Medication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.random.Random

class PbCloudService {
    private val gson = Gson()

    fun isConfigured(): Boolean = PbConfig.isConfigured()

    suspend fun login(account: String, password: String, target: LoginTarget): Result<PbLoginUser> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        val accountText = account.trim()
        if (accountText.isEmpty() || password.isEmpty()) {
            return Result.failure(IllegalArgumentException("请输入账号和密码"))
        }
        val collection = when (target) {
            LoginTarget.ELDER -> PbConfig.COLLECTION_ELDER_USERS
            LoginTarget.GUARDIAN -> PbConfig.COLLECTION_GUARDIAN_USERS
        }
        val candidates = resolveIdentityCandidatesForLogin(collection, accountText)
        var response: JsonObject? = null
        var matchedIdentity: String = accountText
        var authError: Throwable? = null
        for (identity in candidates) {
            val authResult = PbClient.post(
                "/api/collections/$collection/auth-with-password",
                mapOf("identity" to identity, "password" to password),
                useAdminToken = false
            )
            if (authResult.isSuccess) {
                response = authResult.getOrNull()
                matchedIdentity = identity
                break
            } else {
                authError = authResult.exceptionOrNull()
            }
        }
        val finalResponse = response ?: return Result.failure(
            IllegalArgumentException(authError?.message ?: "账号或密码错误")
        )
        return runCatching {
            val record = finalResponse["record"]?.jsonObject ?: findUserRecordByIdentity(collection, matchedIdentity)
            if (record == null) {
                throw IllegalStateException("账号或密码错误")
            }
            val id = record?.get("id")?.jsonPrimitive?.content.orEmpty()
            val phone = record?.get("phone")?.jsonPrimitive?.content
                ?: record?.get("username")?.jsonPrimitive?.content
                ?: accountText
            val boundElder = if (target == LoginTarget.GUARDIAN) {
                resolveBoundElderPhone(phone).getOrElse { "" }
            } else {
                ""
            }
            val deviceCode = if (target == LoginTarget.ELDER) {
                ensureElderDeviceCode(phone).getOrElse { "" }
            } else {
                ""
            }
            PbLoginUser(
                id = id,
                account = phone,
                displayName = record["name"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { phone },
                deviceCode = deviceCode,
                target = target,
                boundElderAccount = boundElder
            )
        }
    }

    suspend fun register(phone: String, password: String, name: String, target: LoginTarget): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (phone.length != 11) return Result.failure(IllegalArgumentException("手机号格式错误"))
        if (password.length < 6) return Result.failure(IllegalArgumentException("密码至少6位"))
        val collection = when (target) {
            LoginTarget.ELDER -> PbConfig.COLLECTION_ELDER_USERS
            LoginTarget.GUARDIAN -> PbConfig.COLLECTION_GUARDIAN_USERS
        }
        return PbClient.post(
            path = "/api/collections/$collection/records",
            body = mapOf(
                "username" to phone,
                "phone" to phone,
                "name" to name,
                "deviceCode" to if (target == LoginTarget.ELDER) generateUniqueDeviceCode() else "",
                "password" to password,
                "passwordConfirm" to password
            )
        ).map { }
    }

    suspend fun ensureElderDeviceCode(elderPhone: String): Result<String> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        val elder = listRecords(
            collection = PbConfig.COLLECTION_ELDER_USERS,
            filter = "phone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
            ?: return Result.failure(IllegalArgumentException("未找到老人账号"))
        val existing = elder["deviceCode"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (existing.matches(Regex("\\d{6}"))) return Result.success(existing)
        val code = generateUniqueDeviceCode()
        val id = elder["id"]?.jsonPrimitive?.content.orEmpty()
        if (id.isBlank()) return Result.failure(IllegalStateException("账号记录异常"))
        PbClient.put(
            "/api/collections/${PbConfig.COLLECTION_ELDER_USERS}/records/$id",
            mapOf("deviceCode" to code)
        ).getOrElse { return Result.failure(it) }
        return Result.success(code)
    }

    suspend fun createBindRequestByDeviceCode(guardianPhone: String, elderDeviceCode: String): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (guardianPhone.length != 11) return Result.failure(IllegalArgumentException("子女手机号格式错误"))
        if (!elderDeviceCode.matches(Regex("\\d{6}"))) return Result.failure(IllegalArgumentException("设备码格式错误"))
        val elder = listRecords(
            collection = PbConfig.COLLECTION_ELDER_USERS,
            filter = "deviceCode='${elderDeviceCode}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
            ?: return Result.failure(IllegalArgumentException("设备码不存在"))
        val elderPhone = elder["phone"]?.jsonPrimitive?.content.orEmpty()
        if (elderPhone.isBlank()) return Result.failure(IllegalStateException("老人账号异常"))
        val relation = listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "guardianPhone='${guardianPhone}' && elderPhone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val payload = mapOf(
            "guardianPhone" to guardianPhone,
            "elderPhone" to elderPhone,
            "status" to "INACTIVE"
        )
        if (relation != null) {
            val rid = relation["id"]?.jsonPrimitive?.content.orEmpty()
            if (rid.isNotBlank()) {
                PbClient.put("/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records/$rid", payload).getOrElse { throw it }
            }
        } else {
            PbClient.post("/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records", payload).getOrElse { throw it }
        }
        return Result.success(Unit)
    }

    suspend fun fetchPendingBindRequestsForElder(elderPhone: String): Result<List<PbBindRequest>> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (elderPhone.length != 11) return Result.failure(IllegalArgumentException("老人手机号格式错误"))
        return listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "elderPhone='${elderPhone}' && status='INACTIVE'",
            limit = 20
        ).map { relations ->
            relations.mapNotNull { relation ->
                val guardianPhone = relation["guardianPhone"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val status = relation["status"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "INACTIVE" }
                val createdAt = relation["created"]?.jsonPrimitive?.content.orEmpty()
                val updatedAt = relation["updated"]?.jsonPrimitive?.content.orEmpty()
                val guardian = listRecords(
                    collection = PbConfig.COLLECTION_GUARDIAN_USERS,
                    filter = "phone='${guardianPhone}'",
                    limit = 1
                ).getOrNull().orEmpty().firstOrNull()
                val guardianName = guardian?.get("name")?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { guardianPhone }
                PbBindRequest(guardianPhone, guardianName, status, createdAt, updatedAt)
            }
        }
    }

    suspend fun approveBindRequest(elderPhone: String, guardianPhone: String): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        val relation = listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "guardianPhone='${guardianPhone}' && elderPhone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
            ?: return Result.failure(IllegalArgumentException("绑定请求不存在"))
        val id = relation["id"]?.jsonPrimitive?.content.orEmpty()
        if (id.isBlank()) return Result.failure(IllegalStateException("绑定请求数据异常"))
        PbClient.put(
            "/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records/$id",
            mapOf("status" to "ACTIVE")
        ).getOrElse { return Result.failure(it) }
        return Result.success(Unit)
    }

    suspend fun fetchAllBindRequestsForElder(elderPhone: String): Result<List<PbBindRequest>> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (elderPhone.length != 11) return Result.failure(IllegalArgumentException("老人手机号格式错误"))
        return listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "elderPhone='${elderPhone}'",
            limit = 50
        ).map { relations ->
            relations.mapNotNull { relation ->
                val guardianPhone = relation["guardianPhone"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val status = relation["status"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "INACTIVE" }
                val createdAt = relation["created"]?.jsonPrimitive?.content.orEmpty()
                val updatedAt = relation["updated"]?.jsonPrimitive?.content.orEmpty()
                val guardian = listRecords(
                    collection = PbConfig.COLLECTION_GUARDIAN_USERS,
                    filter = "phone='${guardianPhone}'",
                    limit = 1
                ).getOrNull().orEmpty().firstOrNull()
                val guardianName = guardian?.get("name")?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { guardianPhone }
                PbBindRequest(guardianPhone, guardianName, status, createdAt, updatedAt)
            }
        }
    }

    suspend fun cancelBindRelation(elderPhone: String, guardianPhone: String): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        val relation = listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "guardianPhone='${guardianPhone}' && elderPhone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
            ?: return Result.failure(IllegalArgumentException("绑定关系不存在"))
        val id = relation["id"]?.jsonPrimitive?.content.orEmpty()
        if (id.isBlank()) return Result.failure(IllegalStateException("绑定关系异常"))
        PbClient.delete(
            "/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records/$id"
        ).getOrElse { return Result.failure(it) }
        return Result.success(Unit)
    }

    suspend fun issueBindCode(elderPhone: String): Result<String> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (elderPhone.length != 11) return Result.failure(IllegalArgumentException("老人手机号格式错误"))
        val code = (100000 + Random.nextInt(900000)).toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + 5 * 60 * 1000
        val existing = listRecords(
            collection = PbConfig.COLLECTION_BIND_VERIFY_CODES,
            filter = "elderPhone='${elderPhone}' && used=false",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val payload = buildJsonObject {
            put("elderPhone", elderPhone)
            put("code", code)
            put("expiresAt", expiresAt)
            put("used", false)
        }
        if (existing != null) {
            val id = existing["id"]?.jsonPrimitive?.content.orEmpty()
            if (id.isNotBlank()) {
                PbClient.put("/api/collections/${PbConfig.COLLECTION_BIND_VERIFY_CODES}/records/$id", payload).getOrElse { throw it }
            }
        } else {
            PbClient.post("/api/collections/${PbConfig.COLLECTION_BIND_VERIFY_CODES}/records", payload).getOrElse { throw it }
        }
        return Result.success(code)
    }

    suspend fun bindGuardianWithCode(guardianPhone: String, elderPhone: String, code: String): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (guardianPhone.length != 11 || elderPhone.length != 11) {
            return Result.failure(IllegalArgumentException("手机号格式错误"))
        }
        val now = System.currentTimeMillis()
        val codeRecord = listRecords(
            collection = PbConfig.COLLECTION_BIND_VERIFY_CODES,
            filter = "elderPhone='${elderPhone}' && code='${code}' && used=false",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
            ?: return Result.failure(IllegalArgumentException("验证码错误"))
        val expiresAt = codeRecord["expiresAt"]?.jsonPrimitive?.longOrNull ?: 0L
        if (expiresAt < now) return Result.failure(IllegalArgumentException("验证码已过期"))
        val relation = listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "guardianPhone='${guardianPhone}' && elderPhone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val relationPayload = mapOf(
            "guardianPhone" to guardianPhone,
            "elderPhone" to elderPhone,
            "status" to "ACTIVE"
        )
        if (relation != null) {
            val rid = relation["id"]?.jsonPrimitive?.content.orEmpty()
            if (rid.isNotBlank()) {
                PbClient.put("/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records/$rid", relationPayload).getOrElse { throw it }
            }
        } else {
            PbClient.post("/api/collections/${PbConfig.COLLECTION_GUARDIAN_ELDER}/records", relationPayload).getOrElse { throw it }
        }
        val codeId = codeRecord["id"]?.jsonPrimitive?.content.orEmpty()
        if (codeId.isNotBlank()) {
            PbClient.put(
                "/api/collections/${PbConfig.COLLECTION_BIND_VERIFY_CODES}/records/$codeId",
                mapOf("used" to true)
            ).getOrElse { throw it }
        }
        return Result.success(Unit)
    }

    suspend fun uploadElderSnapshot(elderPhone: String, snapshot: ElderHealthSnapshot): Result<Unit> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (elderPhone.isBlank()) return Result.failure(IllegalArgumentException("老人手机号为空"))
        val existing = listRecords(
            collection = PbConfig.COLLECTION_ELDER_HEALTH_SYNC,
            filter = "elderPhone='${elderPhone}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val payload = buildJsonObject {
            put("elderPhone", elderPhone)
            put("payload", gson.toJson(snapshot))
            put("updatedAtClient", System.currentTimeMillis())
        }
        return if (existing != null) {
            val id = existing["id"]?.jsonPrimitive?.content.orEmpty()
            if (id.isBlank()) return Result.failure(IllegalStateException("同步记录ID无效"))
            PbClient.put("/api/collections/${PbConfig.COLLECTION_ELDER_HEALTH_SYNC}/records/$id", payload).map { }
        } else {
            PbClient.post("/api/collections/${PbConfig.COLLECTION_ELDER_HEALTH_SYNC}/records", payload).map { }
        }
    }

    suspend fun fetchElderSnapshot(elderPhone: String): Result<ElderHealthSnapshot?> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (elderPhone.isBlank()) return Result.failure(IllegalArgumentException("老人手机号为空"))
        return listRecords(
            collection = PbConfig.COLLECTION_ELDER_HEALTH_SYNC,
            filter = "elderPhone='${elderPhone}'",
            limit = 1
        ).map { items ->
            val item = items.firstOrNull() ?: return@map null
            val payload = item["payload"]?.jsonPrimitive?.content.orEmpty()
            if (payload.isBlank()) return@map null
            gson.fromJson(payload, ElderHealthSnapshot::class.java)
        }
    }

    suspend fun resolveBoundElders(guardianPhone: String): Result<List<GuardianBoundElder>> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        if (guardianPhone.isBlank()) return Result.failure(IllegalArgumentException("子女手机号为空"))
        return listRecords(
            collection = PbConfig.COLLECTION_GUARDIAN_ELDER,
            filter = "guardianPhone='${guardianPhone}' && status='ACTIVE'",
            limit = 50
        ).map { relations ->
            relations.mapNotNull { relation ->
                val elderPhone = relation["elderPhone"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val elder = listRecords(
                    collection = PbConfig.COLLECTION_ELDER_USERS,
                    filter = "phone='${elderPhone}'",
                    limit = 1
                ).getOrNull()?.firstOrNull()
                if (elder == null) {
                    GuardianBoundElder(phone = elderPhone, name = elderPhone, status = "ACTIVE")
                } else {
                    val elderName = elder["name"]?.jsonPrimitive?.content ?: elderPhone
                    GuardianBoundElder(phone = elderPhone, name = elderName, status = "ACTIVE")
                }
            }
        }
    }

    suspend fun resolveBoundElderPhone(guardianPhone: String): Result<String> {
        return resolveBoundElders(guardianPhone).map { it.firstOrNull()?.phone.orEmpty() }
    }

    suspend fun findMedicationFromCloudByApproval(approvalNumber: String): Result<Medication?> {
        if (!isConfigured()) return Result.failure(IllegalStateException("PocketBase 未配置"))
        val normalized = approvalNumber.trim()
        if (normalized.isBlank()) return Result.success(null)
        val core = Regex("([A-Za-z]\\d{8})").find(normalized)?.groupValues?.getOrNull(1)?.uppercase().orEmpty()
        val candidates = buildList {
            add(normalized)
            if (core.isNotBlank()) {
                add("国药准字$core")
                add(core)
            }
        }.distinct()

        for (candidate in candidates) {
            val exact = listRecords(
                collection = PbConfig.COLLECTION_MEDICATION_CATALOG,
                filter = "approvalNumber='${candidate}'",
                limit = 1
            ).getOrNull().orEmpty().firstOrNull()
            if (exact != null) return Result.success(toMedication(exact, candidate))
        }

        if (core.isNotBlank()) {
            val fuzzy = listRecords(
                collection = PbConfig.COLLECTION_MEDICATION_CATALOG,
                filter = "approvalNumber~'${core}'",
                limit = 1
            ).getOrNull().orEmpty().firstOrNull()
            if (fuzzy != null) return Result.success(toMedication(fuzzy, normalized))
        }

        return Result.success(null)
    }

    private fun toMedication(record: JsonObject, fallbackApproval: String): Medication {
        return Medication(
            approvalNumber = record["approvalNumber"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { fallbackApproval },
            name = record["name"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "未知药品" },
            manufacturer = record["manufacturer"]?.jsonPrimitive?.content?.trim().orEmpty(),
            timesPerDay = record["timesPerDay"]?.jsonPrimitive?.intOrNull ?: 3,
            dosePerTime = record["dosePerTime"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "1片" },
            mealRelation = record["mealRelation"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "饭后" },
            reminderTimesJson = record["reminderTimesJson"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank { "[]" },
            contraindications = record["contraindications"]?.jsonPrimitive?.contentOrNull?.trim(),
            expiryDate = record["expiryDate"]?.jsonPrimitive?.contentOrNull?.trim()
        )
    }

    private suspend fun listRecords(
        collection: String,
        filter: String,
        limit: Int
    ): Result<List<JsonObject>> {
        return PbClient.get(
            path = "/api/collections/$collection/records",
            params = mapOf("filter" to filter, "limit" to limit)
        ).map { response ->
            val items = response["items"]?.jsonArray ?: JsonArray(emptyList())
            items.map { it.jsonObject }
        }
    }

    private suspend fun resolveIdentityCandidatesForLogin(collection: String, accountText: String): List<String> {
        val candidates = linkedSetOf<String>()
        candidates.add(accountText)
        val byPhone = listRecords(
            collection = collection,
            filter = "phone='${accountText}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val username = byPhone?.get("username")?.jsonPrimitive?.content.orEmpty().trim()
        if (username.isNotBlank()) candidates.add(username)
        val byUsername = listRecords(
            collection = collection,
            filter = "username='${accountText}'",
            limit = 1
        ).getOrNull().orEmpty().firstOrNull()
        val phone = byUsername?.get("phone")?.jsonPrimitive?.content.orEmpty().trim()
        if (phone.isNotBlank()) candidates.add(phone)
        return candidates.toList()
    }

    private suspend fun findUserRecordByIdentity(
        collection: String,
        accountText: String
    ): JsonObject? {
        val byPhone = listRecords(collection, "phone='${accountText}'", 1).getOrNull().orEmpty().firstOrNull()
        if (byPhone != null) return byPhone
        return listRecords(collection, "username='${accountText}'", 1).getOrNull().orEmpty().firstOrNull()
    }

    private suspend fun generateUniqueDeviceCode(): String {
        repeat(20) {
            val code = (100000 + Random.nextInt(900000)).toString()
            val existing = listRecords(
                collection = PbConfig.COLLECTION_ELDER_USERS,
                filter = "deviceCode='${code}'",
                limit = 1
            ).getOrNull().orEmpty()
            if (existing.isEmpty()) return code
        }
        return (100000 + Random.nextInt(900000)).toString()
    }
}
