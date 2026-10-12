package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ChannelStreamStatus
import com.example.model.MultiStreamStatus
import com.example.model.StreamChannel
import com.example.model.StreamConfig
import com.example.model.StreamStateEnum
import com.example.model.StreamStatus
import com.example.service.sources.LoopingAudioSource
import com.example.service.sources.LoopingVideoSource
import com.example.util.VideoUtils
import com.pedro.common.ConnectChecker
import com.pedro.encoder.utils.CodecUtil
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.sources.audio.NoAudioSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class LiveStreamService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var durationTimerJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // Concurrent map of all active streaming sessions
    private val activeSessions = ConcurrentHashMap<String, ActiveStreamSession>()

    private class ActiveStreamSession(
        val channelId: String,
        val channelName: String,
        val config: StreamChannel,
        val videoUri: Uri,
        val videoName: String,
        var rtmpStream: RtmpStream? = null,
        var startTimeMs: Long = 0L,
        var consecutiveFailures: Int = 0,
        var reconnectJob: Job? = null,
        var isUserInitiatedStop: Boolean = false,
        var loopCount: Int = 0,
        var currentBitrateKbps: Long = 0
    )

    companion object {
        private const val TAG = "LiveStreamService"
        const val CHANNEL_ID = "youtube_loop_live_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.action.START_STREAM"
        const val ACTION_STOP = "com.example.action.STOP_STREAM"
        const val ACTION_START_CHANNEL = "com.example.action.START_CHANNEL"
        const val ACTION_STOP_CHANNEL = "com.example.action.STOP_CHANNEL"
        const val ACTION_START_ALL = "com.example.action.START_ALL"
        const val ACTION_STOP_ALL = "com.example.action.STOP_ALL"

        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_CHANNELS_JSON = "extra_channels_json"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_NAME = "extra_video_name"
        const val EXTRA_SERVER_URL = "extra_server_url"
        const val EXTRA_STREAM_KEY = "extra_stream_key"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_AUDIO_BITRATE = "extra_audio_bitrate"
        const val EXTRA_AUTO_RECONNECT = "extra_auto_reconnect"
        const val EXTRA_ECO_BATTERY = "extra_eco_battery"

        private const val MAX_RECONNECT_ATTEMPTS = 5

        private val _multiStreamStatus = MutableStateFlow(MultiStreamStatus())
        val multiStreamStatus: StateFlow<MultiStreamStatus> = _multiStreamStatus.asStateFlow()

        // Backward compatibility single stream status flow
        private val _streamStatus = MutableStateFlow(StreamStatus())
        val streamStatus: StateFlow<StreamStatus> = _streamStatus.asStateFlow()

        fun log(msg: String) {
            val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val entry = "[$timestamp] $msg"
            Log.d(TAG, entry)
            _multiStreamStatus.update { current ->
                val updatedLogs = (listOf(entry) + current.logs).take(60)
                current.copy(logs = updatedLogs)
            }
            _streamStatus.update { current ->
                val updatedLogs = (listOf(entry) + current.logs).take(60)
                current.copy(logs = updatedLogs)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START_CHANNEL, ACTION_START -> {
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: "default_channel"
                val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Channel 1"
                val videoUriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
                val videoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "Selected Video"
                val serverUrl = intent.getStringExtra(EXTRA_SERVER_URL) ?: StreamConfig.DEFAULT_SERVER_URL
                val streamKey = intent.getStringExtra(EXTRA_STREAM_KEY) ?: ""
                val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
                val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
                val fps = intent.getIntExtra(EXTRA_FPS, 30)
                val videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 2000)
                val audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BITRATE, 128)
                val autoReconnect = intent.getBooleanExtra(EXTRA_AUTO_RECONNECT, true)
                val ecoBattery = intent.getBooleanExtra(EXTRA_ECO_BATTERY, true)

                if (videoUriStr.isNullOrEmpty() || streamKey.trim().isEmpty()) {
                    log("[$channelName] Cannot start: Missing video URI or stream key")
                    updateChannelStatus(channelId, channelName) {
                        it.copy(state = StreamStateEnum.ERROR, errorMessage = "Missing video or stream key")
                    }
                    if (activeSessions.isEmpty()) stopSelf()
                    return START_NOT_STICKY
                }

                val channel = StreamChannel(
                    id = channelId,
                    name = channelName,
                    serverUrl = serverUrl,
                    streamKey = streamKey,
                    resolutionWidth = width,
                    resolutionHeight = height,
                    fps = fps,
                    videoBitrateKbps = videoBitrate,
                    audioBitrateKbps = audioBitrate,
                    autoReconnect = autoReconnect,
                    ecoBatterySaver = ecoBattery,
                    isEnabled = true
                )

                startChannelSession(channel, Uri.parse(videoUriStr), videoName)
            }

            ACTION_STOP_CHANNEL -> {
                val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
                if (channelId != null) {
                    stopChannelSession(channelId, StreamStateEnum.STOPPED)
                }
            }

            ACTION_START_ALL -> {
                val channelsJson = intent.getStringExtra(EXTRA_CHANNELS_JSON)
                val defaultVideoUriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
                val defaultVideoName = intent.getStringExtra(EXTRA_VIDEO_NAME) ?: "Default Video"

                if (!channelsJson.isNullOrEmpty()) {
                    startAllChannelsFromJson(channelsJson, defaultVideoUriStr, defaultVideoName)
                }
            }

            ACTION_STOP_ALL, ACTION_STOP -> {
                log("User requested to stop all live streams")
                stopAllSessions()
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun startAllChannelsFromJson(
        channelsJson: String,
        defaultVideoUriStr: String?,
        defaultVideoName: String
    ) {
        try {
            val jsonArray = JSONArray(channelsJson)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val isEnabled = obj.optBoolean("isEnabled", true)
                val streamKey = obj.optString("streamKey", "")

                if (!isEnabled || streamKey.trim().isEmpty()) continue

                val id = obj.optString("id")
                val name = obj.optString("name", "Channel ${i + 1}")
                val customVideoUri = obj.optString("customVideoUri").takeIf { it.isNotEmpty() }
                val customVideoName = obj.optString("customVideoName").takeIf { it.isNotEmpty() }

                val targetVideoUriStr = customVideoUri ?: defaultVideoUriStr
                val targetVideoName = customVideoName ?: defaultVideoName

                if (targetVideoUriStr.isNullOrEmpty()) {
                    log("[$name] Skipped: No video selected")
                    continue
                }

                val channel = StreamChannel(
                    id = id,
                    name = name,
                    serverUrl = obj.optString("serverUrl", StreamConfig.DEFAULT_SERVER_URL),
                    streamKey = streamKey,
                    customVideoUri = customVideoUri,
                    customVideoName = customVideoName,
                    resolutionWidth = obj.optInt("resolutionWidth", 1280),
                    resolutionHeight = obj.optInt("resolutionHeight", 720),
                    fps = obj.optInt("fps", 30),
                    videoBitrateKbps = obj.optInt("videoBitrateKbps", 2000),
                    audioBitrateKbps = obj.optInt("audioBitrateKbps", 128),
                    autoReconnect = obj.optBoolean("autoReconnect", true),
                    ecoBatterySaver = obj.optBoolean("ecoBatterySaver", true),
                    isEnabled = true
                )

                startChannelSession(channel, Uri.parse(targetVideoUriStr), targetVideoName)
            }
        } catch (e: Exception) {
            log("Error starting all channels: ${e.message}")
        }
    }

    private fun startChannelSession(channel: StreamChannel, videoUri: Uri, videoName: String) {
        // Stop existing session for this channel if already active
        activeSessions[channel.id]?.let { existing ->
            try {
                existing.reconnectJob?.cancel()
                existing.rtmpStream?.stopStream()
            } catch (ignored: Exception) {}
        }

        val session = ActiveStreamSession(
            channelId = channel.id,
            channelName = channel.name,
            config = channel,
            videoUri = videoUri,
            videoName = videoName,
            isUserInitiatedStop = false
        )
        activeSessions[channel.id] = session

        acquireLocks(session.config.ecoBatterySaver)
        startForegroundNotification()

        val ecoBadge = if (channel.ecoBatterySaver) " [Eco 🔋]" else ""
        log("[${channel.name}] Initializing stream: ${channel.resolutionWidth}x${channel.resolutionHeight} @ ${channel.fps}fps, ${channel.videoBitrateKbps}kbps$ecoBadge")

        updateChannelStatus(channel.id, channel.name) {
            it.copy(
                state = StreamStateEnum.STARTING,
                activeVideoName = videoName,
                activeSettingsSummary = "${channel.resolutionLabel} • ${channel.fps}FPS • ${channel.videoBitrateKbps}kbps",
                errorMessage = null,
                durationSeconds = 0,
                loopCount = 0,
                reconnectCount = 0
            )
        }

        startSessionPipeline(session)
        ensureDurationTimerRunning()
    }

    private fun startSessionPipeline(session: ActiveStreamSession) {
        val channel = session.config
        val uri = session.videoUri
        val endpoint = channel.fullRtmpEndpoint

        try {
            val connectChecker = object : ConnectChecker {
                override fun onConnectionStarted(url: String) {
                    log("[${session.channelName}] Connecting RTMP: ${channel.serverUrl}")
                    updateChannelStatus(session.channelId, session.channelName) {
                        it.copy(state = StreamStateEnum.STARTING)
                    }
                    updateNotification()
                }

                override fun onConnectionSuccess() {
                    log("[${session.channelName}] 🔴 LIVE on YouTube!")
                    session.consecutiveFailures = 0
                    if (session.startTimeMs == 0L) {
                        session.startTimeMs = System.currentTimeMillis()
                    }
                    updateChannelStatus(session.channelId, session.channelName) {
                        it.copy(
                            state = StreamStateEnum.LIVE,
                            errorMessage = null
                        )
                    }
                    updateNotification()
                }

                override fun onConnectionFailed(reason: String) {
                    log("[${session.channelName}] Connection failed: $reason")
                    handleSessionFailure(session, "Connection failed: $reason")
                }

                override fun onDisconnect() {
                    log("[${session.channelName}] Disconnected from server")
                    if (!session.isUserInitiatedStop) {
                        handleSessionFailure(session, "Disconnected from YouTube Live")
                    }
                }

                override fun onAuthError() {
                    log("[${session.channelName}] ❌ Auth error: Please verify your YouTube Stream Key")
                    updateChannelStatus(session.channelId, session.channelName) {
                        it.copy(
                            state = StreamStateEnum.ERROR,
                            errorMessage = "Auth error: Invalid Stream Key"
                        )
                    }
                    stopChannelSession(session.channelId, StreamStateEnum.ERROR)
                }

                override fun onAuthSuccess() {
                    log("[${session.channelName}] Authentication verified")
                }

                override fun onNewBitrate(bitrate: Long) {
                    val kbps = bitrate / 1000
                    session.currentBitrateKbps = kbps
                    updateChannelStatus(session.channelId, session.channelName) {
                        it.copy(currentBitrateKbps = kbps)
                    }
                }
            }

            val videoSource = LoopingVideoSource(this, uri) {
                session.loopCount++
                log("[${session.channelName}] Loop completed. Continuous iteration #${session.loopCount}")
                updateChannelStatus(session.channelId, session.channelName) {
                    it.copy(loopCount = session.loopCount)
                }
                updateNotification()
            }

            // Inspect audio track safely
            var hasAudioTrack = false
            var fileSampleRate = 44100
            var fileIsStereo = true
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(this, uri, null)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        hasAudioTrack = true
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            fileSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            fileIsStereo = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) >= 2
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                log("[${session.channelName}] Audio detection notice: ${e.message}")
            } finally {
                try {
                    extractor.release()
                } catch (ignored: Exception) {}
            }

            val audioSource = if (hasAudioTrack) {
                LoopingAudioSource(this, uri)
            } else {
                NoAudioSource()
            }

            val rtmp = RtmpStream(this, connectChecker, videoSource, audioSource)

            // Force hardware codec first for minimal battery consumption
            rtmp.forceCodecType(CodecUtil.CodecType.HARDWARE, CodecUtil.CodecType.HARDWARE)
            rtmp.forceFpsLimit(true)

            val alignedWidth = VideoUtils.alignDimension(channel.resolutionWidth, 16)
            val alignedHeight = VideoUtils.alignDimension(channel.resolutionHeight, 16)
            val videoBitrateBits = channel.videoBitrateKbps * 1024
            val audioBitrateBits = channel.audioBitrateKbps * 1024

            var videoPrepared = rtmp.prepareVideo(
                alignedWidth,
                alignedHeight,
                channel.fps,
                videoBitrateBits,
                2,
                0
            )

            // If hardware fails, fallback to 720p or software H.264
            if (!videoPrepared && (alignedWidth != 1280 || alignedHeight != 720)) {
                log("[${session.channelName}] Retrying with standard 1280x720 hardware profile...")
                videoPrepared = rtmp.prepareVideo(1280, 720, channel.fps, videoBitrateBits, 2, 0)
            }

            if (!videoPrepared) {
                log("[${session.channelName}] Hardware encoder limit reached. Falling back to Software H.264 codec...")
                rtmp.forceCodecType(CodecUtil.CodecType.SOFTWARE, CodecUtil.CodecType.HARDWARE)
                videoPrepared = rtmp.prepareVideo(
                    alignedWidth,
                    alignedHeight,
                    channel.fps,
                    videoBitrateBits,
                    2,
                    0
                )
            }

            if (!videoPrepared) {
                log("[${session.channelName}] Failed to prepare video encoder")
                updateChannelStatus(session.channelId, session.channelName) {
                    it.copy(
                        state = StreamStateEnum.ERROR,
                        errorMessage = "Video encoder incompatible"
                    )
                }
                stopChannelSession(session.channelId, StreamStateEnum.ERROR)
                return
            }

            if (hasAudioTrack) {
                try {
                    rtmp.prepareAudio(fileSampleRate, fileIsStereo, audioBitrateBits)
                } catch (e: Exception) {
                    log("[${session.channelName}] Audio prep notice: ${e.message}")
                }
            }

            session.rtmpStream = rtmp
            try {
                rtmp.startStream(endpoint)
                log("[${session.channelName}] Pipeline connected (${alignedWidth}x${alignedHeight} @ ${channel.fps}fps)")
            } catch (codecEx: Exception) {
                log("[${session.channelName}] Hardware startStream notice: ${codecEx.message}. Retrying with software codec...")
                try {
                    rtmp.stopStream()
                } catch (ignored: Exception) {}

                val fallbackVideoSource = LoopingVideoSource(this, uri) {
                    session.loopCount++
                    updateChannelStatus(session.channelId, session.channelName) {
                        it.copy(loopCount = session.loopCount)
                    }
                }
                val fallbackAudioSource = if (hasAudioTrack) LoopingAudioSource(this, uri) else NoAudioSource()
                val fallbackRtmp = RtmpStream(this, connectChecker, fallbackVideoSource, fallbackAudioSource)
                fallbackRtmp.forceCodecType(CodecUtil.CodecType.SOFTWARE, CodecUtil.CodecType.HARDWARE)
                fallbackRtmp.forceFpsLimit(true)
                fallbackRtmp.prepareVideo(alignedWidth, alignedHeight, channel.fps, videoBitrateBits, 2, 0)
                if (hasAudioTrack) {
                    try {
                        fallbackRtmp.prepareAudio(fileSampleRate, fileIsStereo, audioBitrateBits)
                    } catch (ignored: Exception) {}
                }
                session.rtmpStream = fallbackRtmp
                fallbackRtmp.startStream(endpoint)
                log("[${session.channelName}] Pipeline running with software H.264 compatibility codec")
            }

        } catch (e: Exception) {
            log("[${session.channelName}] Error starting pipeline: ${e.message}")
            updateChannelStatus(session.channelId, session.channelName) {
                it.copy(
                    state = StreamStateEnum.ERROR,
                    errorMessage = e.message ?: "Failed to start stream"
                )
            }
            stopChannelSession(session.channelId, StreamStateEnum.ERROR)
        }
    }

    private fun handleSessionFailure(session: ActiveStreamSession, reason: String) {
        if (session.isUserInitiatedStop) return

        session.consecutiveFailures++
        updateChannelStatus(session.channelId, session.channelName) {
            it.copy(
                state = StreamStateEnum.RECONNECTING,
                reconnectCount = session.consecutiveFailures,
                errorMessage = reason
            )
        }
        updateNotification()

        if (!session.config.autoReconnect) {
            log("[${session.channelName}] Auto-reconnect disabled. Stopping stream.")
            stopChannelSession(session.channelId, StreamStateEnum.ERROR)
            return
        }

        if (session.consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
            log("[${session.channelName}] Max reconnect attempts ($MAX_RECONNECT_ATTEMPTS) reached.")
            updateChannelStatus(session.channelId, session.channelName) {
                it.copy(
                    state = StreamStateEnum.ERROR,
                    errorMessage = "Failed to reconnect after $MAX_RECONNECT_ATTEMPTS attempts."
                )
            }
            stopChannelSession(session.channelId, StreamStateEnum.ERROR)
            return
        }

        val delayMs = when (session.consecutiveFailures) {
            1 -> 3000L
            2 -> 5000L
            3 -> 8000L
            4 -> 12000L
            else -> 15000L
        }

        log("[${session.channelName}] Reconnecting in ${delayMs / 1000}s (Attempt ${session.consecutiveFailures}/$MAX_RECONNECT_ATTEMPTS)...")
        session.reconnectJob?.cancel()
        session.reconnectJob = serviceScope.launch {
            delay(delayMs)
            if (isActive && !session.isUserInitiatedStop && activeSessions.containsKey(session.channelId)) {
                log("[${session.channelName}] Executing reconnect attempt ${session.consecutiveFailures}...")
                startSessionPipeline(session)
            }
        }
    }

    private fun stopChannelSession(channelId: String, finalState: StreamStateEnum) {
        val session = activeSessions.remove(channelId) ?: return
        session.isUserInitiatedStop = true
        session.reconnectJob?.cancel()

        try {
            session.rtmpStream?.let {
                if (it.isStreaming) it.stopStream()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping rtmp session ${session.channelName}", e)
        }
        session.rtmpStream = null

        updateChannelStatus(channelId, session.channelName) {
            it.copy(
                state = finalState,
                currentBitrateKbps = 0
            )
        }

        log("[${session.channelName}] Stream stopped")
        updateNotification()

        if (activeSessions.isEmpty()) {
            durationTimerJob?.cancel()
            durationTimerJob = null
            releaseLocks()
            stopSelf()
        }
    }

    private fun stopAllSessions() {
        val keys = activeSessions.keys.toList()
        for (k in keys) {
            stopChannelSession(k, StreamStateEnum.STOPPED)
        }
    }

    private fun ensureDurationTimerRunning() {
        if (durationTimerJob?.isActive == true) return

        durationTimerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                if (activeSessions.isEmpty()) break

                _multiStreamStatus.update { current ->
                    val updatedMap = current.channelStatuses.toMutableMap()
                    for ((id, session) in activeSessions) {
                        val chStatus = updatedMap[id]
                        if (chStatus != null && chStatus.state == StreamStateEnum.LIVE) {
                            updatedMap[id] = chStatus.copy(durationSeconds = chStatus.durationSeconds + 1)
                        }
                    }
                    current.copy(channelStatuses = updatedMap)
                }

                // Sync legacy single stream status with the primary or first active session
                syncLegacyStreamStatus()
            }
        }
    }

    private fun syncLegacyStreamStatus() {
        val multi = _multiStreamStatus.value
        val liveSession = multi.channelStatuses.values.firstOrNull { it.state == StreamStateEnum.LIVE }
            ?: multi.channelStatuses.values.firstOrNull { it.state == StreamStateEnum.STARTING || it.state == StreamStateEnum.RECONNECTING }
            ?: multi.channelStatuses.values.firstOrNull()

        if (liveSession != null) {
            _streamStatus.update {
                it.copy(
                    state = liveSession.state,
                    durationSeconds = liveSession.durationSeconds,
                    reconnectCount = liveSession.reconnectCount,
                    currentBitrateKbps = multi.totalBitrateKbps,
                    loopCount = liveSession.loopCount,
                    errorMessage = liveSession.errorMessage,
                    activeVideoName = liveSession.activeVideoName,
                    activeSettingsSummary = liveSession.activeSettingsSummary
                )
            }
        } else {
            _streamStatus.update {
                it.copy(
                    state = StreamStateEnum.OFFLINE,
                    currentBitrateKbps = 0
                )
            }
        }
    }

    private fun updateChannelStatus(
        channelId: String,
        channelName: String,
        transform: (ChannelStreamStatus) -> ChannelStreamStatus
    ) {
        _multiStreamStatus.update { current ->
            val existing = current.channelStatuses[channelId] ?: ChannelStreamStatus(
                channelId = channelId,
                channelName = channelName,
                state = StreamStateEnum.OFFLINE
            )
            val updated = transform(existing)
            val newMap = current.channelStatuses.toMutableMap()
            newMap[channelId] = updated
            current.copy(channelStatuses = newMap)
        }
        syncLegacyStreamStatus()
    }

    private fun acquireLocks(ecoWifi: Boolean) {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "YouTubeLoopLive:MultiStreamWakeLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L)
                }
            }

            if (wifiLock == null) {
                val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val wifiMode = if (ecoWifi) {
                    WifiManager.WIFI_MODE_FULL
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                    } else {
                        WifiManager.WIFI_MODE_FULL
                    }
                }
                wifiLock = wifiManager.createWifiLock(
                    wifiMode,
                    "YouTubeLoopLive:MultiStreamWifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire locks", e)
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null

            wifiLock?.let { if (it.isHeld) it.release() }
            wifiLock = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release locks", e)
        }
    }

    private fun registerNetworkCallback() {
        try {
            connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    log("Network connection lost!")
                    for ((_, session) in activeSessions) {
                        serviceScope.launch {
                            handleSessionFailure(session, "Network connection lost")
                        }
                    }
                }

                override fun onAvailable(network: Network) {
                    log("Network restored")
                    for ((_, session) in activeSessions) {
                        val status = _multiStreamStatus.value.channelStatuses[session.channelId]
                        if (status?.state == StreamStateEnum.RECONNECTING && !session.isUserInitiatedStop) {
                            serviceScope.launch {
                                delay(1000)
                                log("[${session.channelName}] Network back online. Reconnecting...")
                                startSessionPipeline(session)
                            }
                        }
                    }
                }
            }
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback", e)
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unregister network callback", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "YouTube Loop Live Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for YouTube Live streaming service"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val multi = _multiStreamStatus.value
        val liveCount = multi.activeLiveCount
        val totalActive = activeSessions.size
        val totalBitrate = multi.totalBitrateKbps

        val title = when {
            liveCount > 1 -> "🔴 $liveCount STREAMS LIVE ($totalBitrate kbps)"
            liveCount == 1 -> {
                val liveSession = multi.channelStatuses.values.firstOrNull { it.state == StreamStateEnum.LIVE }
                "🔴 LIVE: ${liveSession?.channelName ?: "YouTube Live"}"
            }
            totalActive > 0 -> "⏳ Connecting $totalActive Stream(s)..."
            else -> "YouTube Loop Live"
        }

        val content = when {
            liveCount > 1 -> {
                val names = multi.channelStatuses.values
                    .filter { it.state == StreamStateEnum.LIVE }
                    .joinToString(" • ") { "${it.channelName} (${it.formattedDuration})" }
                names
            }
            liveCount == 1 -> {
                val liveSession = multi.channelStatuses.values.firstOrNull { it.state == StreamStateEnum.LIVE }
                "${liveSession?.formattedDuration} • ${liveSession?.currentBitrateKbps} kbps • Loops: #${liveSession?.loopCount}"
            }
            else -> "Multi-stream service running in background"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LiveStreamService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val earliestLiveStartTime = activeSessions.values
            .filter { it.startTimeMs > 0 }
            .minOfOrNull { it.startTimeMs } ?: 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(earliestLiveStartTime > 0)
            .setUsesChronometer(earliestLiveStartTime > 0)
            .apply {
                if (earliestLiveStartTime > 0) {
                    setWhen(earliestLiveStartTime)
                }
            }
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "STOP ALL",
                stopPendingIntent
            )

        return builder.build()
    }

    private fun startForegroundNotification() {
        val notification = buildNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            serviceType
        )
    }

    private fun updateNotification() {
        try {
            val notification = buildNotification()
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update notification", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAllSessions()
        durationTimerJob?.cancel()
        unregisterNetworkCallback()
        releaseLocks()
        log("LiveStreamService terminated")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
