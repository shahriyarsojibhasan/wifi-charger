package com.shahriyar.smartwificharger.data.api

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class OpenMeteoResponse(
    @SerializedName("current") val current: CurrentWeatherDto?
)

data class CurrentWeatherDto(
    @SerializedName("cloud_cover") val cloudCover: Int,
    @SerializedName("is_day") val isDay: Int,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("temperature_2m") val temperature: Float? = null
) {
    fun getWeatherDescription(isBn: Boolean = false): String {
        return when (weatherCode) {
            0 -> if (isBn) "পরিষ্কার আকাশ (রৌদ্রোজ্জ্বল)" else "Clear Sky (Sunny)"
            1, 2 -> if (isBn) "আংশিক মেঘলা" else "Partly Cloudy"
            3 -> if (isBn) "ঘন মেঘলা" else "Overcast Clouds"
            45, 48 -> if (isBn) "কুয়াশাচ্ছন্ন (শীতকালীন কুয়াশা)" else "Foggy / Winter Haze"
            51, 53, 55 -> if (isBn) "হালকা গুঁড়ি গুঁড়ি বৃষ্টি" else "Light Drizzle"
            61, 63, 65 -> if (isBn) "বৃষ্টিপাত" else "Rain"
            80, 81, 82 -> if (isBn) "ভারী বৃষ্টি" else "Heavy Rain Showers"
            95, 96, 99 -> if (isBn) "বজ্রবৃষ্টি / ঝড়" else "Thunderstorm"
            else -> if (cloudCover > 60) (if (isBn) "মেঘলা" else "Cloudy") else (if (isBn) "পরিষ্কার" else "Clear")
        }
    }

    fun isCloudyOrRainy(): Boolean = cloudCover > 60 || weatherCode >= 51 || weatherCode in 45..48
}

interface WeatherApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double = 24.8465, // Default: Bogura, Bangladesh
        @Query("longitude") longitude: Double = 89.3777,
        @Query("current") current: String = "cloud_cover,is_day,weather_code"
    ): Response<OpenMeteoResponse>
}

object WeatherApiClient {
    val instance: WeatherApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(3000, TimeUnit.MILLISECONDS)
            .readTimeout(3000, TimeUnit.MILLISECONDS)
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}
