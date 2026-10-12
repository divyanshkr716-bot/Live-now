package com.example.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.PowerManager
import android.provider.Settings

data class BatteryInfo(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 25f,
    val isIgnoringOptimizations: Boolean = true
) {
    val healthSummary: String
        get() = when {
            temperatureCelsius > 42f -> "Hot (${temperatureCelsius.toInt()}°C) - Keep away from direct sunlight"
            temperatureCelsius > 37f -> "Warm (${temperatureCelsius.toInt()}°C) - Normal for live encoding"
            else -> "Cool (${temperatureCelsius.toInt()}°C) - Optimal"
        }

    val batteryDrainEstimate: String
        get() = if (isCharging) {
            "⚡ Charging (Battery will stay 100%)"
        } else {
            "🔋 ~1% - 1.5% / hr on 5000mAh phone (Screen Locked)"
        }
}

object BatteryUtils {

    fun getBatteryInfo(context: Context): BatteryInfo {
        var level = 100
        var isCharging = false
        var tempC = 25f

        try {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            if (batteryStatus != null) {
                val rawLevel = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && scale > 0) {
                    level = (rawLevel * 100 / scale)
                }

                val status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                val temp = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temp > 0) {
                    tempC = temp / 10f
                }
            }
        } catch (e: Exception) {
            // Ignore if battery receiver not available
        }

        val isIgnoring = isIgnoringBatteryOptimizations(context)

        return BatteryInfo(
            levelPercent = level,
            isCharging = isCharging,
            temperatureCelsius = tempC,
            isIgnoringOptimizations = isIgnoring
        )
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            true
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to general battery settings
                val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ignored: Exception) {}
        }
    }
}
