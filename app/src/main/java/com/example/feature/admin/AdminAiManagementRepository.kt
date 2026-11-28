package com.example.feature.admin

import com.example.core.network.NetworkClient
import com.example.core.network.sanitizeForBackendFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object AdminAiManagementRepository {

    suspend fun fetchOverview(windowDays: Int = 7): Result<AdminAiManagementOverview> =
        withContext(Dispatchers.IO) {
            try {
                val data = NetworkClient.executeAuthorizedJson(
                    method = "GET",
                    path = "/api/admin/ai?window=$windowDays",
                    client = NetworkClient.admin,
                    notFoundHint = "Admin AI Management API not found on server (404)."
                )
                Result.success(AdminAiManagementParser.parseOverview(data))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e.sanitizeForBackendFailure())
            }
        }

    suspend fun updateConfig(
        payload: JSONObject,
        windowDays: Int = 7
    ): Result<AdminAiManagementOverview> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject(payload.toString()).apply {
                put("windowDays", windowDays)
            }
            val data = NetworkClient.executeAuthorizedJson(
                method = "PATCH",
                path = "/api/admin/ai",
                body = body,
                client = NetworkClient.admin
            )
            Result.success(AdminAiManagementParser.parseOverview(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }

    suspend fun resetSection(
        section: String = "all",
        windowDays: Int = 7
    ): Result<AdminAiManagementOverview> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("action", "reset")
                put("section", section)
                put("windowDays", windowDays)
            }
            val data = NetworkClient.executeAuthorizedJson(
                method = "POST",
                path = "/api/admin/ai",
                body = body,
                client = NetworkClient.admin
            )
            Result.success(AdminAiManagementParser.parseOverview(data))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e.sanitizeForBackendFailure())
        }
    }
}
