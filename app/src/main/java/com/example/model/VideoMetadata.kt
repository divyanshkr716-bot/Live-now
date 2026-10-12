package com.example.model

import android.net.Uri

data class VideoMetadata(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val mimeType: String? = null
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSeconds = durationMs / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedResolution: String
        get() = if (width > 0 && height > 0) {
            val label = when {
                height >= 1080 || width >= 1080 -> "1080p"
                height >= 720 || width >= 720 -> "720p"
                height >= 480 || width >= 480 -> "480p"
                else -> "${minOf(width, height)}p"
            }
            "${width}x${height} ($label)"
        } else {
            "Unknown"
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "Unknown"
            val mb = sizeBytes / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format("%.2f GB", mb / 1024.0)
            } else {
                String.format("%.1f MB", mb)
            }
        }
}
