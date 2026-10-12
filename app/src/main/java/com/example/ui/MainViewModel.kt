package com.example.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ChannelStreamStatus
import com.example.model.MultiStreamStatus
import com.example.model.StreamChannel
import com.example.model.StreamConfig
import com.example.model.StreamPreset
import com.example.model.StreamStateEnum
import com.example.model.StreamStatus
import com.example.model.VideoMetadata
import com.example.service.LiveStreamService
import com.example.storage.SecurePreferences
import com.example.util.BatteryInfo
import com.example.util.BatteryUtils
import com.example.util.VideoUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val securePreferences = SecurePreferences(application)

    private val _defaultVideo = MutableStateFlow<VideoMetadata?>(null)
    val defaultVideo: StateFlow<VideoMetadata?> = _defaultVideo.asStateFlow()

    // Backward compatibility for single video
    val selectedVideo: StateFlow<VideoMetadata?> = _defaultVideo

    private val _channels = MutableStateFlow<List<StreamChannel>>(emptyList())
    val channels: StateFlow<List<StreamChannel>> = _channels.asStateFlow()

    private val _channelVideos = MutableStateFlow<Map<String, VideoMetadata>>(emptyMap())
    val channelVideos: StateFlow<Map<String, VideoMetadata>> = _channelVideos.asStateFlow()

    private val _validationError = MutableStateFlow<String?>(null)
    val validationError: StateFlow<String?> = _validationError.asStateFlow()

    private val _batteryInfo = MutableStateFlow(BatteryUtils.getBatteryInfo(application))
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    // Multi-stream real-time state from Service
    val multiStreamStatus: StateFlow<MultiStreamStatus> = LiveStreamService.multiStreamStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MultiStreamStatus())

    // Backward compatibility single stream status flow
    val streamStatus: StateFlow<StreamStatus> = LiveStreamService.streamStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreamStatus())

    // Backward compatibility config flow representing primary channel
    val config: StateFlow<StreamConfig> = MutableStateFlow(StreamConfig())

    private val _isKeyVisibleMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val isKeyVisibleMap: StateFlow<Map<String, Boolean>> = _isKeyVisibleMap.asStateFlow()

    // Backward compatibility
    private val _isKeyVisible = MutableStateFlow(false)
    val isKeyVisible: StateFlow<Boolean> = _isKeyVisible.asStateFlow()

    init {
        loadSavedData()
        startBatteryMonitoring()
    }

    private fun startBatteryMonitoring() {
        viewModelScope.launch {
            while (isActive) {
                refreshBatteryInfo()
                delay(10000)
            }
        }
    }

    fun refreshBatteryInfo() {
        _batteryInfo.value = BatteryUtils.getBatteryInfo(getApplication())
    }

    fun requestDisableBatteryOptimization() {
        BatteryUtils.openBatteryOptimizationSettings(getApplication())
        refreshBatteryInfo()
    }

    private fun loadSavedData() {
        viewModelScope.launch {
            // Load saved channels
            val loadedChannels = securePreferences.loadChannels()
            _channels.value = loadedChannels

            // Load saved default video
            val savedUri = securePreferences.getSelectedVideoUri()
            if (savedUri != null) {
                try {
                    val metadata = VideoUtils.extractMetadata(getApplication(), savedUri)
                    if (metadata != null && metadata.durationMs > 0) {
                        _defaultVideo.value = metadata
                    }
                } catch (e: Exception) {
                    // Stale URI
                }
            }

            // Load custom videos for channels
            val customMap = mutableMapOf<String, VideoMetadata>()
            for (ch in loadedChannels) {
                if (!ch.customVideoUri.isNullOrEmpty()) {
                    try {
                        val meta = VideoUtils.extractMetadata(getApplication(), Uri.parse(ch.customVideoUri))
                        if (meta != null) customMap[ch.id] = meta
                    } catch (ignored: Exception) {}
                }
            }
            _channelVideos.value = customMap
        }
    }

    fun onDefaultVideoSelected(uri: Uri) {
        val context = getApplication<Application>()
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (ignored: Exception) {}

        viewModelScope.launch {
            val metadata = VideoUtils.extractMetadata(context, uri)
            _defaultVideo.value = metadata
            securePreferences.saveSelectedVideoUri(uri)
            _validationError.value = null
            LiveStreamService.log("Default stream video set: ${metadata?.name ?: "Video"} (${metadata?.formattedResolution})")
        }
    }

    // Backward compatibility
    fun onVideoSelected(uri: Uri) = onDefaultVideoSelected(uri)

    fun onChannelCustomVideoSelected(channelId: String, uri: Uri) {
        val context = getApplication<Application>()
        try {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (ignored: Exception) {}

        viewModelScope.launch {
            val metadata = VideoUtils.extractMetadata(context, uri)
            if (metadata != null) {
                _channelVideos.update { it + (channelId to metadata) }
                updateChannel(channelId) {
                    it.copy(customVideoUri = uri.toString(), customVideoName = metadata.name)
                }
                LiveStreamService.log("Custom video assigned to channel: ${metadata.name}")
            }
        }
    }

    fun clearChannelCustomVideo(channelId: String) {
        _channelVideos.update { it - channelId }
        updateChannel(channelId) {
            it.copy(customVideoUri = null, customVideoName = null)
        }
    }

    fun addChannel(
        name: String = "Channel #${_channels.value.size + 1}",
        serverUrl: String = StreamConfig.DEFAULT_SERVER_URL,
        streamKey: String = "",
        preset: StreamPreset = StreamPreset.BALANCED_HD
    ) {
        val newChannel = StreamChannel(
            id = UUID.randomUUID().toString(),
            name = name,
            serverUrl = serverUrl,
            streamKey = streamKey,
            resolutionWidth = preset.width,
            resolutionHeight = preset.height,
            fps = preset.fps,
            videoBitrateKbps = preset.videoBitrateKbps,
            audioBitrateKbps = preset.audioBitrateKbps,
            ecoBatterySaver = (preset == StreamPreset.ULTRA_BATTERY || preset == StreamPreset.BALANCED_HD),
            isEnabled = true
        )
        val updated = _channels.value + newChannel
        _channels.value = updated
        securePreferences.saveChannels(updated)
        LiveStreamService.log("Added new stream target: $name")
    }

    fun duplicateChannel(channelId: String) {
        val original = _channels.value.find { it.id == channelId } ?: return
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (Copy)"
        )
        val updated = _channels.value + copy
        _channels.value = updated
        securePreferences.saveChannels(updated)
        LiveStreamService.log("Duplicated channel: ${copy.name}")
    }

    fun removeChannel(channelId: String) {
        // First stop if active
        stopSingleChannel(channelId)
        val updated = _channels.value.filter { it.id != channelId }
        _channels.value = if (updated.isEmpty()) {
            listOf(StreamChannel(name = "Channel #1 (Primary)"))
        } else {
            updated
        }
        securePreferences.saveChannels(_channels.value)
    }

    fun updateChannel(channelId: String, transform: (StreamChannel) -> StreamChannel) {
        val updated = _channels.value.map { if (it.id == channelId) transform(it) else it }
        _channels.value = updated
        securePreferences.saveChannels(updated)
    }

    fun updateChannelName(channelId: String, newName: String) {
        updateChannel(channelId) { it.copy(name = newName) }
    }

    fun updateChannelStreamKey(channelId: String, key: String) {
        _validationError.value = null
        updateChannel(channelId) { it.copy(streamKey = key.trim()) }
    }

    fun updateChannelServerUrl(channelId: String, url: String) {
        updateChannel(channelId) { it.copy(serverUrl = url.trim()) }
    }

    fun selectPresetForChannel(channelId: String, preset: StreamPreset) {
        updateChannel(channelId) {
            it.copy(
                resolutionWidth = preset.width,
                resolutionHeight = preset.height,
                fps = preset.fps,
                videoBitrateKbps = preset.videoBitrateKbps,
                audioBitrateKbps = preset.audioBitrateKbps,
                ecoBatterySaver = (preset == StreamPreset.ULTRA_BATTERY || preset == StreamPreset.BALANCED_HD)
            )
        }
        LiveStreamService.log("Preset updated: ${preset.label} (${preset.batteryImpact})")
    }

    fun toggleChannelEnabled(channelId: String) {
        updateChannel(channelId) { it.copy(isEnabled = !it.isEnabled) }
    }

    fun toggleKeyVisibilityForChannel(channelId: String) {
        _isKeyVisibleMap.update { current ->
            val cur = current[channelId] ?: false
            current + (channelId to !cur)
        }
    }

    fun isKeyVisibleForChannel(channelId: String): Boolean {
        return _isKeyVisibleMap.value[channelId] ?: false
    }

    fun toggleKeyVisibility() {
        _isKeyVisible.update { !it }
    }

    fun clearValidationError() {
        _validationError.value = null
    }

    fun startSingleChannel(channelId: String) {
        val channel = _channels.value.find { it.id == channelId }
        if (channel == null) {
            _validationError.value = "Channel not found."
            return
        }

        if (channel.streamKey.trim().isEmpty()) {
            _validationError.value = "Please enter Stream Key for '${channel.name}'."
            return
        }

        // Determine video: custom video or default video
        val videoUriStr = channel.customVideoUri ?: _defaultVideo.value?.uri?.toString()
        val videoName = channel.customVideoName ?: _defaultVideo.value?.name ?: "Stream Video"

        if (videoUriStr.isNullOrEmpty()) {
            _validationError.value = "Please select a video from phone storage for '${channel.name}'."
            return
        }

        _validationError.value = null
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_START_CHANNEL
            putExtra(LiveStreamService.EXTRA_CHANNEL_ID, channel.id)
            putExtra(LiveStreamService.EXTRA_CHANNEL_NAME, channel.name)
            putExtra(LiveStreamService.EXTRA_VIDEO_URI, videoUriStr)
            putExtra(LiveStreamService.EXTRA_VIDEO_NAME, videoName)
            putExtra(LiveStreamService.EXTRA_SERVER_URL, channel.serverUrl)
            putExtra(LiveStreamService.EXTRA_STREAM_KEY, channel.streamKey)
            putExtra(LiveStreamService.EXTRA_WIDTH, channel.resolutionWidth)
            putExtra(LiveStreamService.EXTRA_HEIGHT, channel.resolutionHeight)
            putExtra(LiveStreamService.EXTRA_FPS, channel.fps)
            putExtra(LiveStreamService.EXTRA_VIDEO_BITRATE, channel.videoBitrateKbps)
            putExtra(LiveStreamService.EXTRA_AUDIO_BITRATE, channel.audioBitrateKbps)
            putExtra(LiveStreamService.EXTRA_AUTO_RECONNECT, channel.autoReconnect)
            putExtra(LiveStreamService.EXTRA_ECO_BATTERY, channel.ecoBatterySaver)
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopSingleChannel(channelId: String) {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_STOP_CHANNEL
            putExtra(LiveStreamService.EXTRA_CHANNEL_ID, channelId)
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun startAllChannels() {
        val enabledChannels = _channels.value.filter { it.isEnabled }
        if (enabledChannels.isEmpty()) {
            _validationError.value = "No enabled channels found."
            return
        }

        val missingKey = enabledChannels.find { it.streamKey.trim().isEmpty() }
        if (missingKey != null) {
            _validationError.value = "Missing Stream Key for '${missingKey.name}'."
            return
        }

        val hasDefaultVideo = _defaultVideo.value != null
        val missingVideo = enabledChannels.find { it.customVideoUri.isNullOrEmpty() && !hasDefaultVideo }
        if (missingVideo != null) {
            _validationError.value = "Please select a default video from phone storage first."
            return
        }

        _validationError.value = null
        val context = getApplication<Application>()

        // Construct JSON of all channels
        val jsonArray = JSONArray()
        for (ch in enabledChannels) {
            val obj = JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("serverUrl", ch.serverUrl)
                put("streamKey", ch.streamKey)
                put("customVideoUri", ch.customVideoUri ?: "")
                put("customVideoName", ch.customVideoName ?: "")
                put("resolutionWidth", ch.resolutionWidth)
                put("resolutionHeight", ch.resolutionHeight)
                put("fps", ch.fps)
                put("videoBitrateKbps", ch.videoBitrateKbps)
                put("audioBitrateKbps", ch.audioBitrateKbps)
                put("autoReconnect", ch.autoReconnect)
                put("ecoBatterySaver", ch.ecoBatterySaver)
                put("isEnabled", ch.isEnabled)
            }
            jsonArray.put(obj)
        }

        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_START_ALL
            putExtra(LiveStreamService.EXTRA_CHANNELS_JSON, jsonArray.toString())
            _defaultVideo.value?.let {
                putExtra(LiveStreamService.EXTRA_VIDEO_URI, it.uri.toString())
                putExtra(LiveStreamService.EXTRA_VIDEO_NAME, it.name)
            }
        }

        ContextCompat.startForegroundService(context, serviceIntent)
    }

    fun stopAllChannels() {
        val context = getApplication<Application>()
        val serviceIntent = Intent(context, LiveStreamService::class.java).apply {
            action = LiveStreamService.ACTION_STOP_ALL
        }
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    // Backward compatibility start / stop
    fun startLiveStream() {
        if (_channels.value.isNotEmpty()) {
            startSingleChannel(_channels.value.first().id)
        }
    }

    fun stopLiveStream() {
        stopAllChannels()
    }
}
