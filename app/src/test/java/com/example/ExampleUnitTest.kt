package com.example

import com.example.data.FuelNetworkClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testOpetApi() = runBlocking {
    val client = okhttp3.OkHttpClient.Builder()
      .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
      .build()

    val urls = listOf(
      "https://api.opet.com.tr/api/fuelprices/prices?provinceCode=34&districtCode=",
      "https://api.opet.com.tr/api/fuelprices/prices?provinceCode=34",
      "https://www.opet.com.tr/api/fuelprice?slug=istanbul",
      "https://www.opet.com.tr/api/fuelprices/prices?provinceCode=34&districtCode=",
      "https://www.opet.com.tr/api/fuelprices/prices?provinceCode=34"
    )

    for (url in urls) {
      println("TESTING: $url")
      try {
        val request = okhttp3.Request.Builder()
          .url(url)
          .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
          .header("Channel", "Web")
          .header("Accept", "application/json, text/plain, */*")
          .build()

        client.newCall(request).execute().use { response ->
          println("URL: $url -> STATUS: ${response.code}")
          val body = response.body?.string()
          if (body != null) {
            println("BODY LENGTH: ${body.length}")
            println("BODY (first 800 chars):")
            println(body.take(800))
          }
        }
      } catch (e: Exception) {
        println("URL: $url -> ERROR: ${e.message}")
      }
      println("----------------------------------------")
    }
  }
}
