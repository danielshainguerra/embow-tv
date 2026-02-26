package com.embow.tv.data.models

data class ClaimTokenRequest(
    val device_id: String,
    val pairing_code: String
)

data class ClaimTokenResponse(
    val device_token: String
)

data class DeviceAuthRequest(
    val device_token: String
)

data class DeviceAuthResponse(
    val authenticated: Boolean,
    val device_id: String?
)

data class PlaylistResponse(
    val videos: List<Video>
)

data class Video(
    val id: String,
    val title: String,
    val url: String,
    val thumbnail: String?,
    val duration: Int?
)

data class HeartbeatRequest(
    val device_id: String,
    val status: String,
    val now_playing: String?,
    val position_ms: Long?
)

data class HeartbeatResponse(
    val success: Boolean,
    val commands: List<Command>
)

data class Command(
    val type: String,
    val payload: Map<String, Any>?
)
