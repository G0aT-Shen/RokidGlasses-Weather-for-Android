package com.example.myapplication.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

open class WeatherService {
    open suspend fun fetchWeatherJson(cityQuery: String): String = withContext(Dispatchers.IO) {
        val query = URLEncoder.encode(cityQuery, StandardCharsets.UTF_8.toString())
        val connection = URL("https://wttr.in/$query?format=j1").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = REQUEST_TIMEOUT_MS
        connection.readTimeout = REQUEST_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "RokidWeather/1.0")

        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("天气服务返回 HTTP $responseCode")
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 10_000
    }
}
