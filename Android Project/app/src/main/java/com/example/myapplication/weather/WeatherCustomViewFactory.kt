package com.example.myapplication.weather

import org.json.JSONArray
import org.json.JSONObject

object WeatherCustomViewFactory {
    fun create(weather: WeatherInfo): String {
        val children = JSONArray()
            .put(textNode("weatherCity", weather.city, "18sp", "#FFFFFFFF"))
            .put(
                textNode(
                    id = "weatherCondition",
                    text = "${weather.condition}  ${weather.temperature}°C",
                    textSize = "30sp",
                    textColor = "#FF40FF5E",
                    textStyle = "bold",
                    paddingTop = "8dp",
                )
            )
            .put(
                textNode(
                    id = "weatherMetrics",
                    text = "体感 ${weather.feelsLike}°C   湿度 ${weather.humidity}%",
                    textSize = "16sp",
                    textColor = "#FFFFFFFF",
                    paddingTop = "12dp",
                )
            )
            .put(
                textNode(
                    id = "weatherWind",
                    text = weather.wind,
                    textSize = "16sp",
                    textColor = "#FFFFFFFF",
                    paddingTop = "6dp",
                )
            )
            .put(textNode("forecastTitle", "未来三天", "14sp", "#FFAAAAAA", paddingTop = "14dp"))
            .put(textNode("forecastDay0", forecastText(weather, 0), "14sp", "#FFFFFFFF", paddingTop = "5dp"))
            .put(textNode("forecastDay1", forecastText(weather, 1), "14sp", "#FFFFFFFF", paddingTop = "5dp"))
            .put(textNode("forecastDay2", forecastText(weather, 2), "14sp", "#FFFFFFFF", paddingTop = "5dp"))
            .put(
                textNode(
                    id = "weatherUpdateTime",
                    text = "${weather.updateTime} 更新",
                    textSize = "13sp",
                    textColor = "#FFAAAAAA",
                    paddingTop = "12dp",
                )
            )

        return JSONObject()
            .put("type", "LinearLayout")
            .put(
                "props",
                JSONObject()
                    .put("id", "weatherRoot")
                    .put("layout_width", "match_parent")
                    .put("layout_height", "match_parent")
                    .put("marginTop", "120dp")
                    .put("marginBottom", "80dp")
                    .put("paddingStart", "24dp")
                    .put("paddingEnd", "24dp")
                    .put("backgroundColor", "#FF000000")
                    .put("orientation", "vertical")
                    .put("gravity", "center")
            )
            .put("children", children)
            .toString()
    }

    fun createUpdate(weather: WeatherInfo): String = JSONArray()
        .put(updateNode("weatherCity", weather.city))
        .put(updateNode("weatherCondition", "${weather.condition}  ${weather.temperature}°C"))
        .put(updateNode("weatherMetrics", "体感 ${weather.feelsLike}°C   湿度 ${weather.humidity}%"))
        .put(updateNode("weatherWind", weather.wind))
        .put(updateNode("forecastDay0", forecastText(weather, 0)))
        .put(updateNode("forecastDay1", forecastText(weather, 1)))
        .put(updateNode("forecastDay2", forecastText(weather, 2)))
        .put(updateNode("weatherUpdateTime", "${weather.updateTime} 更新"))
        .toString()

    private fun forecastText(weather: WeatherInfo, index: Int): String =
        weather.forecast.getOrNull(index)?.displayText().orEmpty()

    private fun updateNode(id: String, text: String): JSONObject = JSONObject()
        .put("action", "update")
        .put("id", id)
        .put("props", JSONObject().put("text", text))

    private fun textNode(
        id: String,
        text: String,
        textSize: String,
        textColor: String,
        textStyle: String? = null,
        paddingTop: String? = null,
    ): JSONObject {
        val props = JSONObject()
            .put("id", id)
            .put("layout_width", "match_parent")
            .put("layout_height", "wrap_content")
            .put("text", text)
            .put("textColor", textColor)
            .put("textSize", textSize)
            .put("gravity", "center")

        textStyle?.let { props.put("textStyle", it) }
        paddingTop?.let { props.put("paddingTop", it) }

        return JSONObject()
            .put("type", "TextView")
            .put("props", props)
    }
}
