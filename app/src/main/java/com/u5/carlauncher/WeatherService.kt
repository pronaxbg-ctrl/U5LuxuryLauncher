package com.u5.carlauncher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class LiveWeather(
    val tempC: Int? = null,
    val weatherCode: Int = 0
) {
    val icon: ImageVector
        get() = when (weatherCode) {
            0 -> Icons.Rounded.WbSunny
            1, 2, 3 -> Icons.Rounded.Cloud
            45, 48 -> Icons.Rounded.Dehaze
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> Icons.Rounded.WaterDrop
            71, 73, 75, 77, 85, 86 -> Icons.Rounded.AcUnit
            95, 96, 99 -> Icons.Rounded.Thunderstorm
            else -> Icons.Rounded.WbCloudy
        }
}

object WeatherService {
    suspend fun fetchCurrentWeather(lat: Double, lon: Double): LiveWeather? {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr =
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    instanceFollowRedirects = true
                }
                if (conn.responseCode == 200) {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    val cw = JSONObject(text).optJSONObject("current_weather")
                    if (cw != null) {
                        val temp = cw.optDouble("temperature", Double.NaN)
                        val code = cw.optInt("weathercode", 0)
                        if (!temp.isNaN()) {
                            return@withContext LiveWeather(temp.roundToInt(), code)
                        }
                    }
                }
                null
            } catch (_: Exception) {
                null
            }
        }
    }
}
