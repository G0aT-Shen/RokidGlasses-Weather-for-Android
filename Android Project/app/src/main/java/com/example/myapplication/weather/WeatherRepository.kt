package com.example.myapplication.weather

import java.io.IOException
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

class WeatherRepository(
    private val service: WeatherService = WeatherService(),
    private val retryDelayMs: Long = RETRY_DELAY_MS,
) {
    suspend fun getWeather(city: String): WeatherInfo {
        val response = fetchWithRetry(cityToQuery(city))
        return parseWeather(city, response, LocalTime.now().format(TIME_FORMATTER))
    }

    suspend fun getWeather(
        latitude: Double,
        longitude: Double,
        locationName: String? = null,
    ): WeatherInfo {
        val response = fetchWithRetry(coordinatesToQuery(latitude, longitude))
        val city = locationName?.takeIf(String::isNotBlank) ?: nearestAreaName(response)
        return parseWeather(city, response, LocalTime.now().format(TIME_FORMATTER))
    }

    private suspend fun fetchWithRetry(cityQuery: String): String {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return service.fetchWeatherJson(cityQuery)
            } catch (error: CancellationException) {
                throw error
            } catch (error: IOException) {
                lastError = error
                if (attempt < MAX_ATTEMPTS - 1) delay(retryDelayMs)
            }
        }
        throw lastError ?: IOException("天气服务请求失败")
    }

    internal fun parseWeather(city: String, response: String, updateTime: String): WeatherInfo {
        val root = JSONObject(response)
        val current = root.getJSONArray("current_condition").getJSONObject(0)
        val description = current
            .getJSONArray("weatherDesc")
            .getJSONObject(0)
            .getString("value")

        return WeatherInfo(
            city = city,
            condition = weatherDescriptionToChinese(description),
            temperature = current.getString("temp_C").toInt(),
            feelsLike = current.getString("FeelsLikeC").toInt(),
            humidity = current.getString("humidity").toInt(),
            wind = "${windDirectionToChinese(current.getString("winddir16Point"))} " +
                "${current.getString("windspeedKmph")}km/h",
            updateTime = updateTime,
            forecast = parseForecast(root),
        )
    }

    private fun parseForecast(root: JSONObject): List<DailyForecast> {
        val weatherDays = root.optJSONArray("weather") ?: return emptyList()
        return buildList {
            repeat(minOf(FORECAST_DAYS, weatherDays.length())) { index ->
                val day = weatherDays.optJSONObject(index) ?: return@repeat
                val minimum = day.optString("mintempC").toIntOrNull() ?: return@repeat
                val maximum = day.optString("maxtempC").toIntOrNull() ?: return@repeat
                val hourly = day.optJSONArray("hourly")
                val midday = hourly?.optJSONObject(minOf(MIDDAY_HOURLY_INDEX, hourly.length() - 1))
                val description = midday
                    ?.optJSONArray("weatherDesc")
                    ?.optJSONObject(0)
                    ?.optString("value")
                    .orEmpty()
                add(
                    DailyForecast(
                        day = forecastDayLabel(index),
                        condition = weatherDescriptionToChinese(description).ifBlank { "--" },
                        minTemperature = minimum,
                        maxTemperature = maximum,
                    )
                )
            }
        }
    }

    internal fun forecastDayLabel(index: Int): String = when (index) {
        0 -> "今天"
        1 -> "明天"
        2 -> "后天"
        else -> "第${index + 1}天"
    }

    internal fun cityToQuery(city: String): String = CITY_QUERIES[city] ?: city

    internal fun coordinatesToQuery(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.5f,%.5f", latitude, longitude)

    internal fun nearestAreaName(response: String): String {
        val nearestArea = JSONObject(response)
            .optJSONArray("nearest_area")
            ?.optJSONObject(0)
        val areaName = nearestArea
            ?.optJSONArray("areaName")
            ?.optJSONObject(0)
            ?.optString("value")
            .orEmpty()
        val regionName = nearestArea
            ?.optJSONArray("region")
            ?.optJSONObject(0)
            ?.optString("value")
            .orEmpty()
        return displayLocationName(areaName, regionName)
    }

    internal fun displayAreaName(areaName: String): String {
        if (areaName.isBlank()) return "当前位置"
        return translatedCityName(areaName) ?: areaName
    }

    internal fun displayLocationName(areaName: String, regionName: String): String {
        return translatedCityName(areaName)
            ?: translatedCityName(regionName)
            ?: areaName.ifBlank { regionName.ifBlank { "当前位置" } }
    }

    private fun translatedCityName(name: String): String? = CITY_QUERIES.entries
        .firstOrNull { (_, query) ->
            name.equals(query, ignoreCase = true) ||
                name.startsWith("$query ", ignoreCase = true)
        }
        ?.key

    internal fun weatherDescriptionToChinese(description: String): String {
        val value = description.lowercase()
        return when {
            "thunder" in value -> "雷阵雨"
            "heavy rain" in value || "torrential" in value -> "大雨"
            "moderate rain" in value -> "中雨"
            "light rain" in value || "patchy rain" in value || "drizzle" in value -> "小雨"
            "rain" in value -> "雨"
            "snow" in value || "sleet" in value || "ice" in value -> "雪"
            "fog" in value || "mist" in value -> "雾"
            "overcast" in value -> "阴"
            "cloudy" in value -> "多云"
            "sunny" in value || "clear" in value -> "晴"
            else -> description
        }
    }

    internal fun windDirectionToChinese(direction: String): String = WIND_DIRECTIONS[direction] ?: direction

    private companion object {
        const val MAX_ATTEMPTS = 2
        const val FORECAST_DAYS = 3
        const val MIDDAY_HOURLY_INDEX = 4
        const val RETRY_DELAY_MS = 1_000L
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CITY_QUERIES = mapOf(
            "北京" to "Beijing", "上海" to "Shanghai", "广州" to "Guangzhou", "深圳" to "Shenzhen",
            "杭州" to "Hangzhou", "成都" to "Chengdu", "武汉" to "Wuhan", "南京" to "Nanjing",
            "重庆" to "Chongqing", "天津" to "Tianjin", "苏州" to "Suzhou", "西安" to "Xi'an",
            "长沙" to "Changsha", "郑州" to "Zhengzhou", "青岛" to "Qingdao", "大连" to "Dalian",
            "厦门" to "Xiamen", "福州" to "Fuzhou", "合肥" to "Hefei", "济南" to "Jinan",
            "哈尔滨" to "Harbin", "昆明" to "Kunming", "贵阳" to "Guiyang", "南宁" to "Nanning",
            "沈阳" to "Shenyang", "长春" to "Changchun", "太原" to "Taiyuan", "石家庄" to "Shijiazhuang",
        )
        val WIND_DIRECTIONS = mapOf(
            "N" to "北风", "NNE" to "东北偏北风", "NE" to "东北风", "ENE" to "东北偏东风",
            "E" to "东风", "ESE" to "东南偏东风", "SE" to "东南风", "SSE" to "东南偏南风",
            "S" to "南风", "SSW" to "西南偏南风", "SW" to "西南风", "WSW" to "西南偏西风",
            "W" to "西风", "WNW" to "西北偏西风", "NW" to "西北风", "NNW" to "西北偏北风",
        )
    }
}
