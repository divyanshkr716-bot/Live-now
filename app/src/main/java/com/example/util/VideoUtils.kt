package com.example.util

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.model.VideoMetadata

object VideoUtils {
    private const val TAG = "VideoUtils"

    fun extractMetadata(context: Context, uri: Uri): VideoMetadata? {
        var displayName = "Selected Video"
        var sizeBytes = 0L

        // Query ContentResolver for name and size
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex) ?: displayName
                    }
                    if (sizeIndex != -1) {
                        sizeBytes = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read content resolver metadata for uri: $uri", e)
        }

        // Use MediaMetadataRetriever for duration and resolution
        var durationMs = 0L
        var width = 0
        var height = 0
        var mimeType: String? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durationStr?.toLongOrNull() ?: 0L

            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)

            var parsedWidth = widthStr?.toIntOrNull() ?: 0
            var parsedHeight = heightStr?.toIntOrNull() ?: 0
            val rotation = rotationStr?.toIntOrNull() ?: 0

            // If video is rotated 90 or 270 degrees, swap width and height for display
            if (rotation == 90 || rotation == 270) {
                val temp = parsedWidth
                parsedWidth = parsedHeight
                parsedHeight = temp
            }

            width = parsedWidth
            height = parsedHeight
            mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve media metadata: ${e.message}", e)
        } finally {
            try {
                retriever.release()
            } catch (ignored: Exception) {}
        }

        return VideoMetadata(
            uri = uri,
            name = displayName,
            durationMs = durationMs,
            width = width,
            height = height,
            sizeBytes = sizeBytes,
            mimeType = mimeType
        )
    }

    /**
     * Ensures resolution dimensions are multiples of 16 (macroblock alignment)
     * required by H.264 MediaCodec encoders on Android 9 and older chipsets.
     */
    fun alignDimension(dim: Int, alignment: Int = 16): Int {
        if (dim <= 0) return 720
        val rem = dim % alignment
        return if (rem == 0) dim else (dim / alignment) * alignment
    }
}
