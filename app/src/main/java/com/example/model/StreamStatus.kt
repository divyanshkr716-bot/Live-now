package com.example.model

enum class StreamStateEnum {
    OFFLINE,
    STARTING,
    LIVE,
    RECONNECTING,
    STOPPED,
    ERROR
}

data class ChannelStreamStatus(
    val channelId: String,
    val channelName: String,
    val state: StreamStateEnum = StreamStateEnum.OFFLINE,
    val durationSeconds: Long = 0,
    val reconnectCount: Int = 0,
    val currentBitrateKbps: Long = 0,
    val loopCount: Int = 0,
    val errorMessage: String? = null,
    val activeVideoName: String? = null,
    val activeSettingsSummary: String? = null
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

    val isStreaming: Boolean
        get() = state == StreamStateEnum.LIVE || state == StreamStateEnum.RECONNECTING || state == StreamStateEnum.STARTING
}

data class MultiStreamStatus(
    val channelStatuses: Map<String, ChannelStreamStatus> = emptyMap(),
    val logs: List<String> = emptyList()
) {
    val activeLiveCount: Int
        get() = channelStatuses.values.count { it.state == StreamStateEnum.LIVE }

    val isAnyStreaming: Boolean
        get() = channelStatuses.values.any { it.isStreaming }

    val totalBitrateKbps: Long
        get() = channelStatuses.values
            .filter { it.state == StreamStateEnum.LIVE }
            .sumOf { it.currentBitrateKbps }

    fun getStatusForChannel(channelId: String, channelName: String): ChannelStreamStatus {
        return channelStatuses[channelId] ?: ChannelStreamStatus(
            channelId = channelId,
            channelName = channelName,
            state = StreamStateEnum.OFFLINE
        )
    }
}

data class StreamStatus(
    val state: StreamStateEnum = StreamStateEnum.OFFLINE,
    val durationSeconds: Long = 0,
    val reconnectCount: Int = 0,
    val currentBitrateKbps: Long = 0,
    val loopCount: Int = 0,
    val errorMessage: String? = null,
    val activeVideoName: String? = null,
    val activeSettingsSummary: String? = null,
    val logs: List<String> = emptyList()
) {
    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return String.format("%02d:%02d:%02d", hours, minutes, seconds)
        }

    val isStreaming: Boolean
        get() = state == StreamStateEnum.LIVE || state == StreamStateEnum.RECONNECTING || state == StreamStateEnum.STARTING
}
