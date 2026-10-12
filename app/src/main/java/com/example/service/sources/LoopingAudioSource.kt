package com.example.service.sources

import android.content.Context
import android.net.Uri
import com.pedro.encoder.Frame
import com.pedro.encoder.input.audio.GetMicrophoneData
import com.pedro.encoder.input.decoder.AudioDecoder
import com.pedro.encoder.input.decoder.AudioDecoderInterface
import com.pedro.encoder.input.decoder.DecoderInterface
import com.pedro.library.util.sources.audio.AudioSource

/**
 * LoopingAudioSource decodes audio tracks from local video files.
 * Unlike the default Pedro AudioFileSource, this class does NOT throw if sample rate
 * differs from requested values, and dynamically feeds PCM audio to Pedro AAC encoder.
 */
class LoopingAudioSource(
    private val context: Context,
    private val uri: Uri
) : AudioSource() {

    private var running = false

    val audioDecoder = AudioDecoder(
        null,
        object : AudioDecoderInterface {
            override fun onAudioDecoderFinished() {
                // Pedro decoder handles loop when loopMode is true
            }
        },
        object : DecoderInterface {
            override fun onLoop() {}
        }
    ).apply {
        setLoopMode(true)
    }

    override fun create(sampleRate: Int, isStereo: Boolean, echoCanceler: Boolean, noiseSuppressor: Boolean): Boolean {
        val res = audioDecoder.initExtractor(context, uri, null)
        if (!res) return false
        return true
    }

    override fun start(getMicrophoneData: GetMicrophoneData) {
        this.getMicrophoneData = getMicrophoneData
        audioDecoder.setGetMicrophoneData(getMicrophoneData)
        audioDecoder.prepareAudio()
        audioDecoder.start()
        running = true
    }

    override fun stop() {
        running = false
        try {
            audioDecoder.stop()
        } catch (ignored: Exception) {}
    }

    override fun release() {
        stop()
    }

    override fun isRunning(): Boolean = running

    override fun getMaxInputSize(): Int = audioDecoder.size

    override fun setMaxInputSize(size: Int) {}

    fun inputPCMData(frame: Frame) {
        this.getMicrophoneData?.inputPCMData(frame)
    }

    fun mute() {
        audioDecoder.mute()
    }

    fun unMute() {
        audioDecoder.unMute()
    }

    fun isMuted(): Boolean = audioDecoder.isMuted

    fun moveTo(timeSeconds: Double) {
        audioDecoder.moveTo(timeSeconds)
    }
}
