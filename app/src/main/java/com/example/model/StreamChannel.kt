package com.example.model

import java.util.UUID

data class StreamChannel(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Channel 1",
    val serverUrl: String = StreamConfig.DEFAULT_SERVER_URL,
    val streamKey: String = "",
    val customVideoUri: String? = null,
    val customVideoName: String? = null,
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2000,
    val audioBitrateKbps: Int = 128,
    val autoReconnect: Boolean = true,
    val ecoBatterySaver: Boolean = true,
    val isEnabled: Boolean = true
) {
    val fullRtmpEndpoint: String
        get() {
            val trimmedUrl = serverUrl.trim()
            val trimmedKey = streamKey.trim()
            return if (trimmedUrl.endsWith("/")) {
                "$trimmedUrl$trimmedKey"
            } else {
                "$trimmedUrl/$trimmedKey"
            }
        }

    val maskedStreamKey: String
        get() = if (streamKey.length > 8) {
            "${streamKey.take(4)}••••••••${streamKey.takeLast(4)}"
        } else if (streamKey.isNotEmpty()) {
            "••••••••"
        } else {
            ""
        }

    val resolutionLabel: String
        get() = when {
            resolutionHeight == 1080 -> "1080p (FHD)"
            resolutionHeight == 720 -> "720p (HD)"
            resolutionHeight == 480 -> "480p (SD)"
            resolutionHeight == 360 -> "360p"
            else -> "${resolutionWidth}x${resolutionHeight}"
        }

    val activePreset: StreamPreset?
        get() = when {
            resolutionHeight == 480 && fps <= 25 && videoBitrateKbps <= 1400 -> StreamPreset.ULTRA_BATTERY
            resolutionHeight == 720 && videoBitrateKbps <= 2200 -> StreamPreset.BALANCED_HD
            resolutionHeight == 1080 -> StreamPreset.FULL_HD
            else -> null
        }
}
