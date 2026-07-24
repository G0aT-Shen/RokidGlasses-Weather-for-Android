package com.example.myapplication.weather

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlinx.coroutines.runBlocking

class WeatherRepositoryTest {
    private val repository = WeatherRepository()

    @Test
    fun mapsCityWeatherAndWindValues() {
        assertEquals("Hangzhou", repository.cityToQuery("杭州"))
        assertEquals("晴", repository.weatherDescriptionToChinese("Sunny"))
        assertEquals("小雨", repository.weatherDescriptionToChinese("Light rain shower"))
        assertEquals("东南风", repository.windDirectionToChinese("SE"))
    }

    @Test
    fun preservesUnknownValues() {
        assertEquals("Tokyo", repository.cityToQuery("Tokyo"))
        assertEquals("Dust", repository.weatherDescriptionToChinese("Dust"))
        assertEquals("VARIABLE", repository.windDirectionToChinese("VARIABLE"))
    }

    @Test
    fun formatsCoordinatesIndependentlyOfSystemLocale() {
        assertEquals("30.27410,120.15510", repository.coordinatesToQuery(30.2741, 120.1551))
    }

    @Test
    fun formatsThreeDayForecastLabels() {
        assertEquals("今天", repository.forecastDayLabel(0))
        assertEquals("明天", repository.forecastDayLabel(1))
        assertEquals("后天", repository.forecastDayLabel(2))
        assertEquals("明天  多云  25~34°C", DailyForecast("明天", "多云", 25, 34).displayText())
    }

    @Test
    fun mapsNearestAreaNameForDisplay() {
        assertEquals("杭州", repository.displayAreaName("Hangzhou"))
        assertEquals("Tokyo", repository.displayAreaName("Tokyo"))
        assertEquals("当前位置", repository.displayAreaName(""))
        assertEquals("上海", repository.displayLocationName("Nanbeicun", "Shanghai"))
        assertEquals("上海", repository.displayLocationName("Nanbeicun", "Shanghai Municipality"))
        assertEquals("杭州", repository.displayLocationName("Hangzhou", "Zhejiang"))
        assertEquals("Unknown village", repository.displayLocationName("Unknown village", "Unknown region"))
    }

    @Test
    fun retriesIoFailureOnlyOnce() = runBlocking {
        var attempts = 0
        val failingService = object : WeatherService() {
            override suspend fun fetchWeatherJson(cityQuery: String): String {
                attempts += 1
                throw IOException("offline")
            }
        }
        val retryingRepository = WeatherRepository(failingService, retryDelayMs = 0)

        try {
            retryingRepository.getWeather("杭州")
            fail("Expected IOException")
        } catch (_: IOException) {
            assertEquals(2, attempts)
        }
    }

}
