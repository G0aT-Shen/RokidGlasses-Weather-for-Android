package com.example.myapplication.weather

data class WeatherInfo(
    val city: String,
    val condition: String,
    val temperature: Int,
    val feelsLike: Int,
    val humidity: Int,
    val wind: String,
    val updateTime: String,
    val forecast: List<DailyForecast> = emptyList(),
)

data class DailyForecast(
    val day: String,
    val condition: String,
    val minTemperature: Int,
    val maxTemperature: Int,
) {
    fun displayText(): String = "$day  $condition  $minTemperature~$maxTemperature°C"
}
