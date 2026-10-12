package com.example.model

enum class StreamPreset(
    val label: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateKbps: Int,
    val audioBitrateKbps: Int,
    val batteryImpact: String
) {
    ULTRA_BATTERY(
        label = "Ultra Battery 24/7",
        subtitle = "848x480 SD • 24 FPS • 1200 kbps",
        width = 848,
        height = 480,
        fps = 24,
        videoBitrateKbps = 1200,
        audioBitrateKbps = 96,
        batteryImpact = "~1% / hr (5000mAh Phone)"
    ),
    BALANCED_HD(
        label = "Balanced HD 24/7",
        subtitle = "1280x720 HD • 30 FPS • 2000 kbps",
        width = 1280,
        height = 720,
        fps = 30,
        videoBitrateKbps = 2000,
        audioBitrateKbps = 128,
        batteryImpact = "~1.5% - 2% / hr (5000mAh Phone)"
    ),
    FULL_HD(
        label = "Full HD Quality",
        subtitle = "1920x1080 FHD • 30 FPS • 3500 kbps",
        width = 1920,
        height = 1080,
        fps = 30,
        videoBitrateKbps = 3500,
        audioBitrateKbps = 128,
        batteryImpact = "~3% - 4% / hr (Best when plugged in)"
    )
}

data class StreamConfig(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val streamKey: String = "",
    val resolutionWidth: Int = 1280,
    val resolutionHeight: Int = 720,
    val fps: Int = 30,
    val videoBitrateKbps: Int = 2000,
    val audioBitrateKbps: Int = 128,
    val autoReconnect: Boolean = true,
    val ecoBatterySaver: Boolean = true
) {
    companion object {
        const val DEFAULT_SERVER_URL = "rtmp://a.rtmp.youtube.com/live2"
        const val BACKUP_SERVER_URL = "rtmp://b.rtmp.youtube.com/live2?backup=1"
        const val RTMPS_SERVER_URL = "rtmps://a.rtmps.youtube.com/live2"
    }

    val activePreset: StreamPreset?
        get() = when {
            resolutionHeight == 480 && fps <= 25 && videoBitrateKbps <= 1400 -> StreamPreset.ULTRA_BATTERY
            resolutionHeight == 720 && videoBitrateKbps <= 2200 -> StreamPreset.BALANCED_HD
            resolutionHeight == 1080 -> StreamPreset.FULL_HD
            else -> null
        }

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
}
