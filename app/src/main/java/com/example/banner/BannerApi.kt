package com.example.banner

import retrofit2.http.GET
import retrofit2.http.Url

interface BannerApi {
    @GET
    suspend fun getBannerConfig(@Url url: String): BannerConfig
}
