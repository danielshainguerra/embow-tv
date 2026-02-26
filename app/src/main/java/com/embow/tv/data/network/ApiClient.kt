package com.embow.tv.data.network

import com.embow.tv.data.models.*
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ApiClient(private val supabaseUrl: String, private val supabaseAnonKey: String) {

    private val functionsBaseUrl = "$supabaseUrl/functions/v1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun buildUrl(functionName: String): String {
        return "$functionsBaseUrl/$functionName"
    }

    fun claimDeviceToken(deviceId: String, pairingCode: String): Result<ClaimTokenResponse> {
        return try {
            val requestBody = ClaimTokenRequest(deviceId, pairingCode)
            val json = gson.toJson(requestBody)

            val request = Request.Builder()
                .url(buildUrl("claim-device-token"))
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val claimResponse = gson.fromJson(responseBody, ClaimTokenResponse::class.java)
                Result.success(claimResponse)
            } else {
                Result.failure(Exception("Failed to claim token: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun deviceAuth(deviceToken: String): Result<DeviceAuthResponse> {
        return try {
            val request = Request.Builder()
                .url(buildUrl("device-auth"))
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $deviceToken")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val authResponse = gson.fromJson(responseBody, DeviceAuthResponse::class.java)
                Result.success(authResponse)
            } else {
                Result.failure(Exception("Device auth failed: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getDevicePlaylist(deviceToken: String): Result<PlaylistResponse> {
        return try {
            val request = Request.Builder()
                .url(buildUrl("get-device-playlist"))
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $deviceToken")
                .addHeader("Content-Type", "application/json")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val playlist = gson.fromJson(responseBody, PlaylistResponse::class.java)
                Result.success(playlist)
            } else {
                Result.failure(Exception("Failed to get playlist: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sendHeartbeat(
        deviceToken: String,
        deviceId: String,
        status: String,
        nowPlaying: String?,
        positionMs: Long?
    ): Result<HeartbeatResponse> {
        return try {
            val requestBody = HeartbeatRequest(deviceId, status, nowPlaying, positionMs)
            val json = gson.toJson(requestBody)

            val request = Request.Builder()
                .url(buildUrl("tv-heartbeat"))
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer $deviceToken")
                .addHeader("Content-Type", "application/json")
                .post(json.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val heartbeatResponse = gson.fromJson(responseBody, HeartbeatResponse::class.java)
                Result.success(heartbeatResponse)
            } else {
                Result.failure(Exception("Heartbeat failed: ${response.code} - $responseBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
