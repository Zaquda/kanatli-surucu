package com.example.banner

import android.content.Context
import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BannerManager {
    // You will update this URL with your own JSON URL (e.g. from GitHub Gist)
    // The JSON must match the BannerConfig structure.
    var CONFIG_URL = "https://www.erdenekin.com/_functions/kampanya"

    private val _activeBanner = MutableStateFlow<BannerConfig?>(null)
    val activeBanner: StateFlow<BannerConfig?> = _activeBanner.asStateFlow()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    // Using a dummy base URL since we use @Url in the API method for the full URL
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.erdenekin.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(BannerApi::class.java)

    suspend fun checkAndFetchBanner(context: Context) {
        try {
            val config = api.getBannerConfig(CONFIG_URL)
            if (config.isActive && isTimeValid(config.startDateTime, config.endDateTime)) {
                _activeBanner.value = config
            } else {
                _activeBanner.value = null
            }
        } catch (e: Exception) {
            Log.e("BannerManager", "Error fetching banner config: \${e.message}")
            _activeBanner.value = null
        }
    }

    private fun isTimeValid(startStr: String, endStr: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val startDate = format.parse(startStr)
            val endDate = format.parse(endStr)
            val now = Date()
            
            if (startDate != null && endDate != null) {
                now.after(startDate) && now.before(endDate)
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("BannerManager", "Time parsing error: \${e.message}")
            false
        }
    }

    fun dismissBanner() {
        _activeBanner.value = null
    }
}
