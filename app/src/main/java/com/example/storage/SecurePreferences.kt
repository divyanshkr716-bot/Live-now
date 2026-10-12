package com.example.storage

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.example.model.StreamChannel
import com.example.model.StreamConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class SecurePreferences(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "youtube_loop_live_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to standard prefs", e)
        context.getSharedPreferences("youtube_loop_live_prefs", Context.MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "SecurePreferences"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_STREAM_KEY = "stream_key"
        private const val KEY_WIDTH = "resolution_width"
        private const val KEY_HEIGHT = "resolution_height"
        private const val KEY_FPS = "fps"
        private const val KEY_VIDEO_BITRATE = "video_bitrate"
        private const val KEY_AUDIO_BITRATE = "audio_bitrate"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_ECO_BATTERY = "eco_battery_saver"
        private const val KEY_SELECTED_VIDEO_URI = "selected_video_uri"
        private const val KEY_SAVED_CHANNELS_JSON = "saved_channels_json"
    }

    fun saveConfig(config: StreamConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_STREAM_KEY, config.streamKey)
            .putInt(KEY_WIDTH, config.resolutionWidth)
            .putInt(KEY_HEIGHT, config.resolutionHeight)
            .putInt(KEY_FPS, config.fps)
            .putInt(KEY_VIDEO_BITRATE, config.videoBitrateKbps)
            .putInt(KEY_AUDIO_BITRATE, config.audioBitrateKbps)
            .putBoolean(KEY_AUTO_RECONNECT, config.autoReconnect)
            .putBoolean(KEY_ECO_BATTERY, config.ecoBatterySaver)
            .apply()
    }

    fun loadConfig(): StreamConfig {
        return StreamConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, StreamConfig.DEFAULT_SERVER_URL)
                ?: StreamConfig.DEFAULT_SERVER_URL,
            streamKey = prefs.getString(KEY_STREAM_KEY, "") ?: "",
            resolutionWidth = prefs.getInt(KEY_WIDTH, 1280),
            resolutionHeight = prefs.getInt(KEY_HEIGHT, 720),
            fps = prefs.getInt(KEY_FPS, 30),
            videoBitrateKbps = prefs.getInt(KEY_VIDEO_BITRATE, 2000),
            audioBitrateKbps = prefs.getInt(KEY_AUDIO_BITRATE, 128),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            ecoBatterySaver = prefs.getBoolean(KEY_ECO_BATTERY, true)
        )
    }

    fun saveChannels(channels: List<StreamChannel>) {
        try {
            val jsonArray = JSONArray()
            for (ch in channels) {
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
            prefs.edit().putString(KEY_SAVED_CHANNELS_JSON, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving channels to JSON", e)
        }
    }

    fun loadChannels(): List<StreamChannel> {
        val rawJson = prefs.getString(KEY_SAVED_CHANNELS_JSON, null)
        if (rawJson.isNullOrEmpty()) {
            // Migrate single config or create initial default channel
            val legacyConfig = loadConfig()
            val initialChannel = StreamChannel(
                id = UUID.randomUUID().toString(),
                name = "Channel #1 (Primary)",
                serverUrl = legacyConfig.serverUrl,
                streamKey = legacyConfig.streamKey,
                resolutionWidth = legacyConfig.resolutionWidth,
                resolutionHeight = legacyConfig.resolutionHeight,
                fps = legacyConfig.fps,
                videoBitrateKbps = legacyConfig.videoBitrateKbps,
                audioBitrateKbps = legacyConfig.audioBitrateKbps,
                autoReconnect = legacyConfig.autoReconnect,
                ecoBatterySaver = legacyConfig.ecoBatterySaver,
                isEnabled = true
            )
            val initialList = listOf(initialChannel)
            saveChannels(initialList)
            return initialList
        }

        return try {
            val jsonArray = JSONArray(rawJson)
            val list = mutableListOf<StreamChannel>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val ch = StreamChannel(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", "Channel ${i + 1}"),
                    serverUrl = obj.optString("serverUrl", StreamConfig.DEFAULT_SERVER_URL),
                    streamKey = obj.optString("streamKey", ""),
                    customVideoUri = obj.optString("customVideoUri").takeIf { it.isNotEmpty() },
                    customVideoName = obj.optString("customVideoName").takeIf { it.isNotEmpty() },
                    resolutionWidth = obj.optInt("resolutionWidth", 1280),
                    resolutionHeight = obj.optInt("resolutionHeight", 720),
                    fps = obj.optInt("fps", 30),
                    videoBitrateKbps = obj.optInt("videoBitrateKbps", 2000),
                    audioBitrateKbps = obj.optInt("audioBitrateKbps", 128),
                    autoReconnect = obj.optBoolean("autoReconnect", true),
                    ecoBatterySaver = obj.optBoolean("ecoBatterySaver", true),
                    isEnabled = obj.optBoolean("isEnabled", true)
                )
                list.add(ch)
            }
            if (list.isEmpty()) {
                listOf(StreamChannel())
            } else {
                list
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing channels JSON", e)
            listOf(StreamChannel())
        }
    }

    fun saveSelectedVideoUri(uri: Uri?) {
        prefs.edit().putString(KEY_SELECTED_VIDEO_URI, uri?.toString()).apply()
    }

    fun getSelectedVideoUri(): Uri? {
        val uriStr = prefs.getString(KEY_SELECTED_VIDEO_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }
}
