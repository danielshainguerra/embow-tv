package com.embow.tv.data.repository

import com.embow.tv.data.models.HeartbeatResponse
import com.embow.tv.data.models.PlaylistResponse
import com.embow.tv.data.network.ApiClient
import com.embow.tv.data.storage.SecureStorage

class EmbowRepository(
    private val apiClient: ApiClient,
    private val secureStorage: SecureStorage
) {

    fun getDeviceId(): String = secureStorage.getDeviceId()

    fun isDeviceActivated(): Boolean = secureStorage.isDeviceActivated()

    fun getDeviceToken(): String? = secureStorage.getDeviceToken()

    suspend fun claimDeviceToken(pairingCode: String): Result<String> {
        val deviceId = getDeviceId()
        return try {
            val result = apiClient.claimDeviceToken(deviceId, pairingCode)
            result.fold(
                onSuccess = { response ->
                    secureStorage.setDeviceToken(response.device_token)
                    Result.success(response.device_token)
                },
                onFailure = { exception ->
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun validateDeviceToken(): Result<Boolean> {
        val token = getDeviceToken() ?: return Result.failure(Exception("No device token"))
        return try {
            val result = apiClient.deviceAuth(token)
            result.fold(
                onSuccess = { response ->
                    if (response.authenticated) {
                        Result.success(true)
                    } else {
                        clearActivation()
                        Result.success(false)
                    }
                },
                onFailure = { exception ->
                    clearActivation()
                    Result.failure(exception)
                }
            )
        } catch (e: Exception) {
            clearActivation()
            Result.failure(e)
        }
    }

    suspend fun getPlaylist(): Result<PlaylistResponse> {
        val token = getDeviceToken() ?: return Result.failure(Exception("No device token"))
        return try {
            apiClient.getDevicePlaylist(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendHeartbeat(
        status: String,
        nowPlaying: String?,
        positionMs: Long?
    ): Result<HeartbeatResponse> {
        val token = getDeviceToken() ?: return Result.failure(Exception("No device token"))
        val deviceId = getDeviceId()
        return try {
            apiClient.sendHeartbeat(token, deviceId, status, nowPlaying, positionMs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun clearActivation() {
        secureStorage.clearDeviceToken()
    }
}
