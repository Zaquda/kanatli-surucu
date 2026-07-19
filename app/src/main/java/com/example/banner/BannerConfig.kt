package com.example.banner

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BannerConfig(
    val id: String,
    val imageUrl: String,
    val targetUrl: String? = null,
    val startDateTime: String, // format: "yyyy-MM-dd HH:mm"
    val endDateTime: String,   // format: "yyyy-MM-dd HH:mm"
    val isActive: Boolean = true
)
