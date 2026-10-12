package com.example.service.sources

import android.content.Context
import android.graphics.SurfaceTexture
import android.net.Uri
import android.view.Surface
import com.pedro.encoder.input.decoder.DecoderInterface
import com.pedro.encoder.input.decoder.VideoDecoder
import com.pedro.encoder.input.decoder.VideoDecoderInterface
import com.pedro.library.util.sources.video.VideoSource

/**
 * LoopingVideoSource decodes video files and renders them via OpenGL SurfaceTexture.
 * It provides infinite seamless looping without crashing across Android 9 (API 28) and newer.
 */
class LoopingVideoSource(
    private val context: Context,
    private val uri: Uri,
    private val onLoopCallback: () -> Unit
) : VideoSource() {

    private var running = false

    val videoDecoder = VideoDecoder(
        object : VideoDecoderInterface {
            override fun onVideoDecoderFinished() {
                // Pedro decoder handles loop when loopMode is true
            }
        },
        object : DecoderInterface {
            override fun onLoop() {
                onLoopCallback()
            }
        }
    ).apply {
        setLoopMode(true)
    }

    override fun create(width: Int, height: Int, fps: Int, rotation: Int): Boolean {
        val res = videoDecoder.initExtractor(context, uri, null)
        if (!res) {
            throw IllegalArgumentException("Could not initialize video extractor for URI: $uri")
        }
        return true
    }

    override fun start(surfaceTexture: SurfaceTexture) {
        val surface = Surface(surfaceTexture)
        videoDecoder.prepareVideo(surface)
        videoDecoder.start()
        running = true
    }

    override fun stop() {
        running = false
        try {
            videoDecoder.stop()
        } catch (ignored: Exception) {}
    }

    override fun release() {
        stop()
    }

    override fun isRunning(): Boolean = running

    fun moveTo(timeSeconds: Double) {
        videoDecoder.moveTo(timeSeconds)
    }
}
